package cloud.mindbox.mobile_sdk.inapp.presentation.view.cache

import android.util.Base64
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Android port of the iOS prototype `InAppWebViewAssetCache` (MindboxWebViewFacade.swift).
 *
 * Responsibilities:
 *  - Persist webview in-app HTML + referenced scripts + referenced media to `cacheDir`.
 *  - Serve an inlined HTML string at show-time so the popup can render without network.
 *  - Backfill missing scripts on repeated prefetch calls (idempotent per file).
 *
 * Out of scope for this port (skipped on purpose):
 *  - JS → native diagnostics bridge.
 *  - Hidden-WebView warmup.
 *
 * Additions over the iOS prototype:
 *  - [lookupCachedBytes] is called from the `WebViewClient.shouldInterceptRequest` hook
 *    (see [WebViewInAppViewHolder.resolveCachedResource]) to serve bytes for dynamic
 *    runtime fetches that escape the static HTML inlining — cache-buster URLs, scripts
 *    injected via DOM APIs the static regex can't rewrite, etc. iOS's prototype relies
 *    on `WKURLSchemeHandler` being gated behind `runtimeInterceptionEnabled`; on Android
 *    `shouldInterceptRequest` is cheap enough to leave on by default.
 *
 * Thread-safety: all disk IO and HTTP is dispatched to [Dispatchers.IO]. [cachedInlinedHtml]
 * is intentionally synchronous so callers can use it on the main thread right before
 * `WebViewController.loadContent(...)`.
 */
internal object InAppWebViewAssetCache {

    private const val TAG_PREFIX = "[WebViewCache]"

    /** Root of the per-content-URL cache tree inside `cacheDir`. */
    private const val ROOT_DIR_NAME = "Mindbox/InAppWebView"

    /** Sub-folder under each content-URL directory where downloaded JS and media live. */
    private const val SCRIPTS_DIR_NAME = "scripts"

    /** Filename for the cached HTML document inside each content-URL directory. */
    private const val INDEX_HTML = "index.html"

    private val ioScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    /** Lazily resolved via DI so this object can be referenced before SDK init without crashing. */
    private val rootDir: File?
        get() = runCatching {
            val base = MindboxDI.appModule.appContext.cacheDir
                ?: return@runCatching null
            File(base, ROOT_DIR_NAME).apply { mkdirs() }
        }.getOrNull()

    // region Public API

    /**
     * Returns a ready-to-load inlined HTML for [contentUrl] if the cache holds one, otherwise `null`.
     *
     * Mirrors iOS `cachedInlinedHTML(for:priorityInlineScripts:)`. Synchronous by design — callers
     * invoke this from `WebViewInappViewHolder.renderLayer` right before loading.
     *
     * [priorityInlineScripts] — extra scripts (e.g. byendpoint.js, popmechanic/quizzes JSONs for
     * the endpointId) that must be inlined before the HTML's regular script references.
     */
    fun cachedInlinedHtml(
        contentUrl: String,
        priorityInlineScripts: List<String> = emptyList()
    ): String? {
        if (!WebViewCacheConfig.enabled) {
            mindboxLogD("$TAG_PREFIX cachedInlinedHtml MISS: feature flag disabled ($contentUrl)")
            return null
        }
        val dir = directory(contentUrl)
        if (dir == null) {
            mindboxLogW("$TAG_PREFIX cachedInlinedHtml MISS: no writable cache dir for $contentUrl")
            return null
        }
        val indexFile = File(dir, INDEX_HTML)
        if (!indexFile.exists()) {
            mindboxLogD(
                "$TAG_PREFIX cachedInlinedHtml MISS: no index.html at ${indexFile.absolutePath} " +
                    "(dir-exists=${dir.exists()}, dir-children=${dir.list()?.size ?: 0})"
            )
            return null
        }
        val html = runCatching { indexFile.readText(Charsets.UTF_8) }
            .onFailure { mindboxLogW("$TAG_PREFIX cachedInlinedHtml MISS: failed to read $indexFile", it) }
            .getOrNull() ?: return null
        val scriptsDir = File(dir, SCRIPTS_DIR_NAME)
        mindboxLogD(
            "$TAG_PREFIX cachedInlinedHtml HIT: ${html.length} chars, " +
                "scripts-dir-children=${scriptsDir.list()?.size ?: 0} for $contentUrl"
        )
        return inline(
            html = html,
            scriptsDir = scriptsDir,
            priorityInlineScripts = priorityInlineScripts
        )
    }

    /**
     * Persists [html] for [contentUrl] and kicks off downloads for every `<script src>` and env-var
     * path referenced from it. Idempotent: re-running re-writes the HTML and only downloads scripts
     * that aren't already on disk.
     *
     * Mirrors iOS `store(html:for:log:logError:)`.
     */
    fun store(html: String, contentUrl: String): Job? {
        if (!WebViewCacheConfig.enabled) return null
        return ioScope.launch {
            val dir = directory(contentUrl) ?: return@launch
            val scriptsDir = File(dir, SCRIPTS_DIR_NAME).apply { mkdirs() }
            runCatching {
                File(dir, INDEX_HTML).writeText(html, Charsets.UTF_8)
            }.onFailure { e ->
                mindboxLogE("$TAG_PREFIX Failed to prepare cache dir", e)
                return@launch
            }

            val urls = extractScriptUrls(html)
            mindboxLogD("$TAG_PREFIX Persisted HTML, downloading ${urls.size} script(s) for $contentUrl")
            urls
                .map { url -> async { downloadScript(url, scriptsDir) } }
                .awaitAll()
        }
    }

    /**
     * Prefetches HTML + all scripts referenced from it for every URL in [contentUrls].
     * Idempotent per file — re-runs on cached HTML only backfill missing script files,
     * never re-download existing ones.
     *
     * [extraScripts] are additional URLs (not referenced from the HTML) that should be cached
     * alongside each in-app. Used for bootstrap scripts loaded at runtime by tracker.js — e.g.
     * `https://web-static.mindbox.ru/js/byendpoint/{endpointId}.js` which sets `window.PopMechanic`.
     *
     * Mirrors iOS `prefetch(contentUrls:extraScripts:log:logError:)`.
     */
    fun prefetch(
        contentUrls: List<String>,
        extraScripts: List<String> = emptyList()
    ) {
        if (!WebViewCacheConfig.enabled) {
            mindboxLogD("$TAG_PREFIX prefetch skipped: feature flag disabled")
            return
        }
        val unique = contentUrls.toSet()
        mindboxLogD(
            "$TAG_PREFIX prefetch kicking off: ${unique.size} HTML URL(s), " +
                "${extraScripts.size} extra script(s)"
        )
        for (urlString in unique) {
            ioScope.launch {
                if (!hasCachedHtml(urlString)) {
                    downloadAndStoreHtml(urlString)
                } else {
                    mindboxLogD("$TAG_PREFIX HTML cached, ensuring scripts are also cached: $urlString")
                    backfillScripts(urlString)
                }
                downloadExtraScripts(extraScripts, urlString)
                mindboxLogD("$TAG_PREFIX prefetch completed for $urlString")
            }
        }
    }

    /**
     * Synchronously downloads [contentUrl] + its scripts/media on the calling coroutine. Used at
     * render time on a cache MISS so we can produce an inlined HTML on the first online show too,
     * not only from the second show onward. Returns `null` if the network download fails — callers
     * should fall back to the legacy online fetch-and-inject path.
     */
    suspend fun fetchAndStoreBlocking(
        contentUrl: String,
        extraScripts: List<String> = emptyList()
    ): Boolean {
        if (!WebViewCacheConfig.enabled) return false
        val html = runCatching { httpGetString(contentUrl) }.getOrElse { e ->
            mindboxLogW("$TAG_PREFIX blocking fetch failed for $contentUrl", e)
            return false
        }
        if (html.isNullOrEmpty()) {
            mindboxLogW("$TAG_PREFIX blocking fetch empty response for $contentUrl")
            return false
        }
        val dir = directory(contentUrl) ?: return false
        val scriptsDir = File(dir, SCRIPTS_DIR_NAME).apply { mkdirs() }
        runCatching {
            File(dir, INDEX_HTML).writeText(html, Charsets.UTF_8)
        }.onFailure { e ->
            mindboxLogE("$TAG_PREFIX blocking fetch failed to write index.html for $contentUrl", e)
            return false
        }
        val urls = (extractScriptUrls(html) + extraScripts).distinct()
        mindboxLogD("$TAG_PREFIX blocking fetch downloading ${urls.size} dependency(s) for $contentUrl")
        urls
            .map { url -> ioScope.async { downloadScript(url, scriptsDir) } }
            .awaitAll()
        return true
    }

    /**
     * Looks up a cached subresource for [requestedUrl] under the cache tree of [contentUrl].
     * Used from `WebViewClient.shouldInterceptRequest` to serve byendpoint/quizzes/main JS
     * without touching the network.
     *
     * Matching strategy (in order):
     *   1. Exact URL match — fast path when the runtime URL equals the prefetched URL.
     *   2. Query-stripped match — handles cache-buster params (`?v=`, `?_=`) that differ
     *      between prefetch time and render time.
     *   3. Directory scan — decodes every cached filename and compares its path against
     *      [requestedUrl]'s path. Last-resort so we never leave known assets on the table.
     *
     * Returns `null` if the asset isn't cached. Callers should fall through to the network
     * in that case.
     */
    fun lookupCachedBytes(contentUrl: String, requestedUrl: String): CachedAsset? {
        if (!WebViewCacheConfig.enabled) return null
        if (requestedUrl.isBlank()) return null
        val dir = directory(contentUrl) ?: return null
        val scriptsDir = File(dir, SCRIPTS_DIR_NAME)
        if (!scriptsDir.exists()) return null

        // 1. Exact match on the full URL.
        val exactScript = scriptFile(requestedUrl, scriptsDir)
        if (exactScript != null && exactScript.exists()) {
            return readAsset(exactScript, requestedUrl)
        }
        val exactMedia = mediaFile(requestedUrl, scriptsDir)
        if (exactMedia != null && exactMedia.exists()) {
            return readAsset(exactMedia, requestedUrl)
        }

        // 2. Query-stripped match: caller prefetched `byendpoint.js` but runtime URL has
        // `byendpoint.js?_=123` appended.
        val pathOnly = requestedUrl.substringBefore('?')
        if (pathOnly != requestedUrl) {
            val pathScript = scriptFile(pathOnly, scriptsDir)
            if (pathScript != null && pathScript.exists()) {
                return readAsset(pathScript, pathOnly)
            }
            val pathMedia = mediaFile(pathOnly, scriptsDir)
            if (pathMedia != null && pathMedia.exists()) {
                return readAsset(pathMedia, pathOnly)
            }
        }

        // 3. Directory scan — compare by path across all cached files.
        val children = scriptsDir.listFiles() ?: return null
        for (file in children) {
            val name = file.name
            val isScript = name.endsWith(".js")
            val mediaPrefix = "media-"
            val isMedia = name.startsWith(mediaPrefix)
            val encoded = when {
                isScript -> name.removeSuffix(".js")
                isMedia -> name.removePrefix(mediaPrefix)
                else -> continue
            }
            val decoded = runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrNull()
                ?: continue
            if (decoded.substringBefore('?') == pathOnly) {
                return readAsset(file, decoded)
            }
        }

        return null
    }

    /** Return payload for [lookupCachedBytes]. */
    data class CachedAsset(
        val bytes: ByteArray,
        val mimeType: String,
        val sourceUrl: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CachedAsset) return false
            return sourceUrl == other.sourceUrl && mimeType == other.mimeType &&
                bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + sourceUrl.hashCode()
            return result
        }
    }

    private fun readAsset(file: File, sourceUrl: String): CachedAsset? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return CachedAsset(
            bytes = bytes,
            mimeType = guessMimeTypeForUrl(sourceUrl),
            sourceUrl = sourceUrl,
        )
    }

    private fun guessMimeTypeForUrl(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".js") || path.endsWith(".mjs") -> "application/javascript"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".html") || path.endsWith(".htm") -> "text/html"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".mp4") -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    // endregion

    // region Private: download / store

    private suspend fun downloadAndStoreHtml(urlString: String) {
        mindboxLogD("$TAG_PREFIX Prefetching HTML: $urlString")
        val html = runCatching { httpGetString(urlString) }.getOrElse { e ->
            mindboxLogW("$TAG_PREFIX Prefetch failed for $urlString", e)
            return
        }
        if (html == null) {
            mindboxLogW("$TAG_PREFIX Prefetch bad response for $urlString")
            return
        }
        // Reuse `store` to do HTML-write + script download in one path.
        val dir = directory(urlString) ?: return
        val scriptsDir = File(dir, SCRIPTS_DIR_NAME).apply { mkdirs() }
        runCatching {
            File(dir, INDEX_HTML).writeText(html, Charsets.UTF_8)
        }.onFailure { e ->
            mindboxLogE("$TAG_PREFIX Failed to write index.html for $urlString", e)
            return
        }
        val urls = extractScriptUrls(html)
        mindboxLogD("$TAG_PREFIX Persisted HTML, downloading ${urls.size} script(s) for $urlString")
        urls
            .map { url -> ioScope.async { downloadScript(url, scriptsDir) } }
            .awaitAll()
    }

    private suspend fun backfillScripts(contentUrl: String) {
        val dir = directory(contentUrl) ?: return
        val indexFile = File(dir, INDEX_HTML)
        val html = runCatching { indexFile.readText(Charsets.UTF_8) }.getOrNull() ?: return
        val scriptsDir = File(dir, SCRIPTS_DIR_NAME).apply { mkdirs() }
        val urls = extractScriptUrls(html)
        mindboxLogD("$TAG_PREFIX Backfilling ${urls.size} script(s) for $contentUrl")
        urls
            .map { url -> ioScope.async { downloadScript(url, scriptsDir) } }
            .awaitAll()
    }

    private suspend fun downloadExtraScripts(urls: List<String>, contentUrl: String) {
        if (urls.isEmpty()) return
        val dir = directory(contentUrl) ?: return
        val scriptsDir = File(dir, SCRIPTS_DIR_NAME).apply { mkdirs() }
        urls
            .map { url -> ioScope.async { downloadScript(url, scriptsDir) } }
            .awaitAll()
    }

    private suspend fun downloadScript(urlString: String, dir: File) {
        val fileURL = scriptFile(urlString, dir) ?: return
        if (fileURL.exists()) {
            // Re-scan in case new media URLs are referenced (though typically idempotent).
            scanCachedScriptForMedia(fileURL, dir)
            return
        }
        val bytes = runCatching { httpGetBytes(urlString) }.getOrElse { e ->
            mindboxLogW("$TAG_PREFIX Failed to download $urlString", e)
            return
        }
        if (bytes == null) {
            mindboxLogW("$TAG_PREFIX Bad HTTP response for $urlString")
            return
        }
        runCatching {
            fileURL.writeBytes(bytes)
            scanCachedScriptForMedia(fileURL, dir)
        }.onFailure { e ->
            mindboxLogE("$TAG_PREFIX Failed to write ${fileURL.name}", e)
        }
    }

    private suspend fun scanCachedScriptForMedia(fileURL: File, dir: File) {
        val content = runCatching { fileURL.readText(Charsets.UTF_8) }.getOrNull() ?: return
        val mediaUrls = extractMediaUrls(content)
        mediaUrls
            .map { mediaUrl -> ioScope.async { downloadMedia(mediaUrl, dir) } }
            .awaitAll()
    }

    private suspend fun downloadMedia(urlString: String, dir: File) {
        val fileURL = mediaFile(urlString, dir) ?: return
        if (fileURL.exists()) return
        val bytes = runCatching { httpGetBytes(urlString) }.getOrElse { e ->
            mindboxLogW("$TAG_PREFIX Failed to download media $urlString", e)
            return
        } ?: return
        runCatching { fileURL.writeBytes(bytes) }
    }

    // endregion

    // region Private: inline (cache → HTML string)

    private fun inline(
        html: String,
        scriptsDir: File,
        priorityInlineScripts: List<String>
    ): String {
        val afterScriptInline = inlineScripts(html, scriptsDir)
        return inlineBootstrapScripts(
            html = afterScriptInline,
            scriptsDir = scriptsDir,
            priorityInlineScripts = priorityInlineScripts
        )
    }

    /**
     * Replaces `<script src="https://...">` tags whose source we have cached with inline
     * `<script>…</script>` tags containing the cached file bytes. Leaves `type="module"` scripts
     * alone — inlining them strips `type="module"` which breaks import/export.
     */
    private fun inlineScripts(html: String, scriptsDir: File): String {
        val staticRegex = Regex(
            pattern = """<script\b[^>]*\bsrc\s*=\s*"(https://[^"]+)"[^>]*>\s*</script>""",
            option = RegexOption.IGNORE_CASE
        )
        val moduleRegex = Regex("""\btype\s*=\s*"module"""", RegexOption.IGNORE_CASE)

        val builder = StringBuilder(html.length)
        var cursor = 0
        for (match in staticRegex.findAll(html)) {
            builder.append(html, cursor, match.range.first)
            val tag = match.value
            val src = match.groupValues[1]
            val inlined = if (moduleRegex.containsMatchIn(tag)) {
                // Keep ES-module scripts as-is — online they load from CDN, offline they'll fail
                // until runtime interception lands.
                tag
            } else {
                val cached = readScriptIfExists(src, scriptsDir)
                if (cached != null) "<script>\n$cached\n</script>" else tag
            }
            builder.append(inlined)
            cursor = match.range.last + 1
        }
        builder.append(html, cursor, html.length)
        return builder.toString()
    }

    /**
     * Injects cached scripts into the bootstrap HTML and disables the bootstrap's dynamic
     * `document.head.appendChild(...)` calls so the same scripts don't also load from CDN.
     *
     * Injection order before `</head>`:
     *  1. Quizzes ES-module (from `QUIZZES_STABLE_SCRIPT_URL`) as `<script type="module">` —
     *     neutralizes byendpoint's later dynamic append.
     *  2. [priorityInlineScripts] — classic scripts (byendpoint.js) or JSON preloads
     *     (`__POPMECHANIC_INIT`, `__PRELOADED_QUIZZES_CONFIG`). Must run BEFORE tracker/main.
     *  3. Env-var script URLs (`MAIN_JS_PATH`, `TRACKER_PATH`) as `<script type="module">`.
     *
     * Mirrors iOS `inlineBootstrapScripts(...)`.
     */
    private fun inlineBootstrapScripts(
        html: String,
        scriptsDir: File,
        priorityInlineScripts: List<String>
    ): String {
        val injected = StringBuilder()

        // Quizzes module — inline up-front so byendpoint's later dynamic append has nothing to do.
        val quizzesFile = scriptFile(QUIZZES_STABLE_SCRIPT_URL, scriptsDir)
        if (quizzesFile != null && quizzesFile.exists()) {
            val contents = runCatching { quizzesFile.readText(Charsets.UTF_8) }.getOrNull()
            if (contents != null) {
                injected.append("\n<script type=\"module\" data-mb-cached-src=\"")
                injected.append(QUIZZES_STABLE_SCRIPT_URL)
                injected.append("\">\n")
                injected.append(contents)
                injected.append("\n</script>\n")
            }
        }

        for (urlString in priorityInlineScripts) {
            val rawContents = readScriptIfExists(urlString, scriptsDir) ?: continue

            val globalName = preloadGlobalVariableName(urlString)
            if (globalName != null) {
                // JSON config → inject as `window.NAME = {...json...};` so byendpoint uses it and
                // skips the runtime fetch. Escape stray `</` so the inlined blob can't terminate
                // the surrounding <script> tag.
                val withInlinedMedia = inlineMediaReferences(rawContents, scriptsDir)
                val safeJson = withInlinedMedia.replace("</", "<\\/")
                injected.append("\n<script data-mb-cached-preload=\"")
                injected.append(urlString)
                injected.append("\">\nwindow.")
                injected.append(globalName)
                injected.append(" = ")
                injected.append(safeJson)
                injected.append(";\n</script>\n")
                continue
            }

            val contents = inlineMediaReferences(rawContents, scriptsDir)
            injected.append("\n<script data-mb-cached-src=\"")
            injected.append(urlString)
            injected.append("\">\n")
            injected.append(contents)
            injected.append("\n</script>\n")
        }

        // Env-var script URLs from the bootstrap.
        val envRegex = Regex("""([A-Z_][A-Z0-9_]*_PATH)\s*:\s*['"](https?://[^'"]+)['"]""")
        val seen = mutableSetOf<String>()
        for (match in envRegex.findAll(html)) {
            val urlString = match.groupValues[2]
            if (!seen.add(urlString)) continue
            val contents = readScriptIfExists(urlString, scriptsDir) ?: continue
            injected.append("\n<script type=\"module\" data-mb-cached-src=\"")
            injected.append(urlString)
            injected.append("\">\n")
            injected.append(contents)
            injected.append("\n</script>\n")
        }

        if (injected.isEmpty()) return html

        // Leave the bootstrap's dynamic `document.head.appendChild(script)` calls intact —
        // the WebViewClient's `shouldInterceptRequest` hook will serve the cached bytes for
        // those requests, and the script's real `onload` will fire naturally.
        val headCloseRegex = Regex("</head>", RegexOption.IGNORE_CASE)
        val headMatch = headCloseRegex.find(html)
        return if (headMatch != null) {
            html.substring(0, headMatch.range.first) +
                injected.toString() + "</head>" +
                html.substring(headMatch.range.last + 1)
        } else {
            injected.toString() + html
        }
    }

    /**
     * Replaces every cached Mindbox media URL embedded in [content] with an inline `data:` URI so
     * the popup renders without network for images/screenshots.
     */
    private fun inlineMediaReferences(content: String, scriptsDir: File): String {
        val regex = Regex(
            pattern = """https://[A-Za-z0-9.\-]+\.mindbox\.ru/[^\s"'\\)]+?\.(?:jpg|jpeg|png|webp|gif|svg|mp4)""",
            option = RegexOption.IGNORE_CASE
        )
        return regex.replace(content) { match ->
            val urlString = match.value
            val fileURL = mediaFile(urlString, scriptsDir)
            if (fileURL == null || !fileURL.exists()) return@replace urlString
            val data = runCatching { fileURL.readBytes() }.getOrNull() ?: return@replace urlString
            val ext = urlString.substringAfterLast('.', "").lowercase()
            val mime = mimeType(ext)
            val base64 = Base64.encodeToString(data, Base64.NO_WRAP)
            // Lambda form of Regex.replace treats the return value literally — no group-reference
            // substitution — so we don't need escapeReplacement here.
            "data:$mime;base64,$base64"
        }
    }

    // endregion

    // region Private: URL / file path helpers

    private fun hasCachedHtml(contentUrl: String): Boolean {
        val dir = directory(contentUrl) ?: return false
        return File(dir, INDEX_HTML).exists()
    }

    /** Per-content-URL cache directory. Uses a sanitized filename based on percent-encoding. */
    private fun directory(contentUrl: String): File? {
        val root = rootDir ?: return null
        val name = sanitize(contentUrl) ?: return null
        return File(root, name).apply { mkdirs() }
    }

    private fun scriptFile(urlString: String, dir: File): File? {
        val name = sanitize(urlString) ?: return null
        return File(dir, "$name.js")
    }

    private fun mediaFile(urlString: String, dir: File): File? {
        val name = sanitize(urlString) ?: return null
        return File(dir, "media-$name")
    }

    /**
     * Percent-encode keeping only alphanumerics so the result is safe to use as a filename.
     * Mirrors iOS `urlString.addingPercentEncoding(withAllowedCharacters: .alphanumerics)`.
     */
    private fun sanitize(value: String): String? {
        if (value.isEmpty()) return null
        // URLEncoder.encode escapes `+` instead of space → replace back to %20 for determinism,
        // but the important thing is that the output matches 1:1 across calls for the same input.
        val raw = URLEncoder.encode(value, Charsets.UTF_8.name())
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c.isLetterOrDigit()) {
                out.append(c)
                i++
            } else if (c == '%' && i + 2 < raw.length) {
                // Already percent-encoded byte — keep as-is (uppercase hex).
                out.append('%')
                out.append(raw[i + 1].uppercaseChar())
                out.append(raw[i + 2].uppercaseChar())
                i += 3
            } else {
                // Non-alphanumeric that URLEncoder didn't escape (e.g. `*`, `-`, `.`, `_`).
                val code = c.code
                out.append('%')
                out.append(String.format("%02X", code))
                i++
            }
        }
        return out.toString()
    }

    private fun readScriptIfExists(urlString: String, scriptsDir: File): String? {
        val file = scriptFile(urlString, scriptsDir) ?: return null
        if (!file.exists()) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    private fun extractScriptUrls(html: String): List<String> {
        val seen = linkedSetOf<String>()

        // Static <script src="https://..."> tags.
        val staticRegex = Regex(
            pattern = """<script\b[^>]*\bsrc\s*=\s*"(https://[^"]+)"""",
            option = RegexOption.IGNORE_CASE
        )
        for (match in staticRegex.findAll(html)) {
            seen.add(match.groupValues[1])
        }

        // Bootstrap `window.__env_vars` paths that the inline script appends as dynamic module scripts.
        val envRegex = Regex("""([A-Z_][A-Z0-9_]*_PATH)\s*:\s*['"](https?://[^'"]+)['"]""")
        for (match in envRegex.findAll(html)) {
            seen.add(match.groupValues[2])
        }

        return seen.toList()
    }

    private fun extractMediaUrls(content: String): List<String> {
        val regex = Regex(
            pattern = """https://[A-Za-z0-9.\-]+\.mindbox\.ru/[^\s"'\\)]+?\.(?:jpg|jpeg|png|webp|gif|svg|mp4)""",
            option = RegexOption.IGNORE_CASE
        )
        val seen = linkedSetOf<String>()
        for (match in regex.findAll(content)) {
            seen.add(match.value)
        }
        return seen.toList()
    }

    private fun mimeType(ext: String): String = when (ext.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }

    // endregion

    // region Private: HTTP

    private suspend fun httpGetString(urlString: String): String? = withContext(Dispatchers.IO) {
        val bytes = httpGetBytesBlocking(urlString) ?: return@withContext null
        String(bytes, Charsets.UTF_8)
    }

    private suspend fun httpGetBytes(urlString: String): ByteArray? = withContext(Dispatchers.IO) {
        httpGetBytesBlocking(urlString)
    }

    private fun httpGetBytesBlocking(urlString: String): ByteArray? {
        val url = runCatching { URL(urlString) }.getOrNull() ?: return null
        val conn = (url.openConnection() as? HttpURLConnection) ?: return null
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.useCaches = false
            val code = conn.responseCode
            if (code !in 200..299) {
                null
            } else {
                conn.inputStream.use { it.readBytes() }
            }
        } catch (e: Exception) {
            mindboxLogW("$TAG_PREFIX HTTP GET failed for $urlString", e)
            null
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    // endregion
}
