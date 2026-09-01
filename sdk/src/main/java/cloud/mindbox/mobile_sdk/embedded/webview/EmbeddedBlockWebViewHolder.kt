package cloud.mindbox.mobile_sdk.embedded.webview

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockState
import cloud.mindbox.mobile_sdk.findActivity
import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.getOrNull
import cloud.mindbox.mobile_sdk.gatedTags
import cloud.mindbox.mobile_sdk.safeAs
import cloud.mindbox.mobile_sdk.inapp.data.managers.SEND_INAPP_TAGS_FEATURE
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.validators.BridgeMessageValidator
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.view.BridgeMessage
import cloud.mindbox.mobile_sdk.inapp.presentation.view.DataCollector
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppInsets
import cloud.mindbox.mobile_sdk.inapp.presentation.view.MindboxWebPage
import cloud.mindbox.mobile_sdk.inapp.presentation.view.MindboxWebPageRegistry
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewAction
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewActionHandlers
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewBridgeHost
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewCommonBridgeActions
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewNoCacheRetryPolicy
import cloud.mindbox.mobile_sdk.inapp.presentation.view.dispatch
import cloud.mindbox.mobile_sdk.inapp.presentation.view.fromBridgeMessage
import cloud.mindbox.mobile_sdk.inapp.presentation.view.toBridgeErrorPayload
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppWebViewCachePolicy
import cloud.mindbox.mobile_sdk.inapp.webview.*
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.getShortUserAgent
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.loggingRunCatchingSuspending
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.sendFailureWithContext
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@OptIn(InternalMindboxApi::class)
internal class EmbeddedBlockWebViewHolder(
    private val inAppId: String,
    private val placeSystemName: String,
    @Volatile private var layer: Layer.WebViewLayer,
    private val context: Context,
    @Volatile private var frequency: Frequency,
    @Volatile private var tags: Map<String, String>?,
    private val startTick: Milliseconds,
    private val ackBudget: Milliseconds = Constants.WebView.readyTimeout,
) : EmbeddedUpdatableContentProvider, MindboxWebPage {

    override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null

    override val contentView: android.view.View?
        get() = if (lastState == EmbeddedBlockState.Ready) webViewController?.view else null

    @Volatile private var webViewController: WebViewController? = null

    private val presence = MutableStateFlow(false)

    private val isActive: Boolean get() = presence.value

    @Volatile private var isReleased = false

    @Volatile private var isLoadRequested = false

    @Volatile private var lastState: EmbeddedBlockState = EmbeddedBlockState.Loading

    @Volatile private var lastLoadedContent: WebViewHtmlContent? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val pendingResponsesById: MutableMap<String, CompletableDeferred<BridgeMessage.Response>> =
        ConcurrentHashMap()

    private val gson: Gson by mindboxInject { this.gson }
    private val gatewayManager: GatewayManager by mindboxInject { gatewayManager }
    private val sessionStorageManager: SessionStorageManager by mindboxInject { sessionStorageManager }
    private val permissionManager: PermissionManager by mindboxInject { permissionManager }
    private val inAppFailureTracker: InAppFailureTracker by mindboxInject { inAppFailureTracker }
    private val inAppInteractor: InAppInteractor by mindboxInject { inAppInteractor }
    private val webViewCachePolicy: InAppWebViewCachePolicy by mindboxInject { webViewCachePolicy }
    private val appContext by mindboxInject { appContext }
    private val timeProvider by mindboxInject { timeProvider }
    private val featureToggleManager: FeatureToggleManager by mindboxInject { featureToggleManager }
    private val inAppMessageManager by mindboxInject { inAppMessageManager }
    private val webPageRegistry: MindboxWebPageRegistry by mindboxInject { webPageRegistry }
    private val messageValidator: BridgeMessageValidator by lazy { BridgeMessageValidator() }

    private val commonBridgeActionsLazy = lazy {
        WebViewCommonBridgeActions(object : WebViewBridgeHost {
            override val hostActivity: Activity? get() = webViewController?.view?.context?.findActivity()
            override val hostTags: Map<String, String>? get() = gatedTags()
            override val hostPage: MindboxWebPage get() = this@EmbeddedBlockWebViewHolder

            override val isUserPresent: Boolean get() = this@EmbeddedBlockWebViewHolder.isUserPresent

            override fun sendToPage(message: BridgeMessage.Request, onError: (String?) -> Unit) {
                val controller = webViewController ?: return
                sendActionInternal(controller, message, onError)
            }

            override val closeCapability: ((BridgeMessage.Request) -> String)? = null
            override val hideCapability: (() -> String)? = null
        })
    }
    private val commonBridgeActions: WebViewCommonBridgeActions by commonBridgeActionsLazy

    @Volatile private var isRegisteredForBroadcasts = false

    private val noCacheRetryPolicy: WebViewNoCacheRetryPolicy = WebViewNoCacheRetryPolicy {
        webViewCachePolicy.isCacheEnabled
    }

    @Volatile private var hasPageAnswered = false

    @Volatile private var didAccountForShow = false

    @Volatile private var didReportShownContent = false

    private val isUserPresent: Boolean get() = isActive && !isReleased

    private val heldFailure = AtomicReference<HeldFailure?>(null)

    private data class HeldFailure(
        val failureReason: FailureReason,
        val errorDescription: String,
        val throwable: Throwable?,
        val tags: Map<String, String>?,
    )

    @Volatile private var renderedTimeToDisplay: Milliseconds? = null

    @Volatile private var pendingAckJob: Job? = null

    override fun start() {
        if (isReleased) return
        presence.value = true
        flushHeldFailure()
        if (!isLoadRequested) {
            isLoadRequested = true
            load()
            return
        }
        report(lastState)
        if (lastState == EmbeddedBlockState.Ready) accountForShow()
    }

    override fun pause() {
        presence.value = false
    }

    override fun release() {
        presence.value = false
        isReleased = true
        pendingAckJob?.cancel()
        unregisterFromBroadcasts()
        if (commonBridgeActionsLazy.isInitialized()) {
            commonBridgeActions.tearDown()
        }
        heldFailure.set(null)
        cancelPendingResponses("Embedded block content is released")
        webViewController?.let { controller ->
            controller.setEventListener(null)
            (controller.view.parent as? ViewGroup)?.removeView(controller.view)
            controller.destroy()
        }
        webViewController = null
    }

    override fun refreshMetricsSnapshot(frequency: Frequency, tags: Map<String, String>?) {
        this.frequency = frequency
        this.tags = tags
    }

    override fun updateParams(params: Map<String, String>, onResult: (Boolean) -> Unit) {
        val controller = webViewController ?: run {
            onResult(false)
            return
        }
        layer = layer.copy(params = params)
        didReportShownContent = false
        pendingAckJob?.cancel()
        pendingAckJob = Mindbox.mindboxScope.launch {
            val configuration: Configuration = DbManager.listenConfigurations().first()
            val payload = startPayload(configuration)
            val isUpdated = runCatching {
                coroutineScope {
                    val answer = async {
                        sendActionAndAwaitResponse(
                            controller,
                            BridgeMessage.createAction(WebViewAction.INIT_DATA_UPDATED, payload)
                        )
                    }
                    val response = answer.awaitWithForegroundBudget(ackBudget)
                    if (response == null) answer.cancel()
                    response
                }
            }.getOrNull() != null
            mindboxLogI("[EmbeddedBlock] initDataUpdated for $inAppId answered success=$isUpdated")
            onResult(isUpdated)
        }
    }

    private fun load() {
        report(EmbeddedBlockState.Loading)
        val controller = runCatching { createWebViewController() }.getOrElse { error ->
            reportLoadFailure("Failed to create a WebView for the embedded block", error)
            return
        }
        webViewController = controller

        Mindbox.mindboxScope.launch {
            val configuration: Configuration = DbManager.listenConfigurations().first()
            val handlers = createActionHandlers(configuration)

            controller.setJsBridge(bridge = { json ->
                mindboxLogI("SDK <- receive message $json")
                val message = gson.fromBridgeMessage(json)
                if (!messageValidator.isValid(message)) {
                    return@setJsBridge
                }
                controller.executeOnViewThread {
                    if (isReleased) return@executeOnViewThread
                    when (message) {
                        is BridgeMessage.Request -> handleRequest(message, controller, handlers)
                        is BridgeMessage.Response -> handleResponse(message)
                        is BridgeMessage.Error -> handleError(message)
                        else -> mindboxLogW("Unknown message type: $message")
                    }
                }
            })
            controller.setUserAgentSuffix(configuration.getShortUserAgent())

            val contentUrl = layer.contentUrl ?: run {
                reportLoadFailure("WebView content URL is null for the embedded block", throwable = null)
                return@launch
            }
            runCatching {
                gatewayManager.fetchWebViewContent(contentUrl)
            }.onSuccess { response: String ->
                onContentPageLoaded(WebViewHtmlContent(baseUrl = layer.baseUrl ?: "", html = response))
            }.onFailure { error ->
                if (error is CancellationException) throw error
                reportLoadFailure("Failed to fetch HTML content for the embedded block", error)
            }
        }
    }

    private fun createWebViewController(): WebViewController {
        mindboxLogI("[EmbeddedBlock] Creating WebView for embedded in-app $inAppId")
        val controller = WebViewController.create(
            context = context,
            isDebugEnabled = false,
            isCacheEnabled = false,
            log = { message -> mindboxLogI("[EmbeddedBlock][WebView] $message") }
        )
        controller.view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        controller.setEventListener(object : WebViewEventListener {
            override fun onPageFinished(url: String?) {
                mindboxLogI("[EmbeddedBlock] onPageFinished: $url")
            }

            override fun onShouldOverrideUrlLoading(url: String?, isForMainFrame: Boolean?): Boolean {
                return isForMainFrame == true
            }

            override fun onError(error: WebViewError) {
                mindboxLogE(
                    "[EmbeddedBlock] WebView error: code=${error.code}, " +
                        "description=${error.description}, url=${error.url}"
                )
                if (error.isForMainFrame == true) {
                    sendFailure(
                        failureReason = FailureReason.WEBVIEW_LOAD_FAILED,
                        errorDescription = "Embedded block WebView error: code=${error.code}, " +
                            "description=${error.description}, url=${error.url}",
                        tags = gatedTags()
                    )
                    report(EmbeddedBlockState.Failed)
                }
            }

            override fun onHttpError(url: String?, statusCode: Int?, isForMainFrame: Boolean?) {
                mindboxLogI("[EmbeddedBlock] HTTP error $statusCode for $url (mainFrame=$isForMainFrame)")
                if (noCacheRetryPolicy.onHttpError(url, statusCode, hasPageAnswered)) {
                    retryContentPageWithoutCache()
                }
            }
        })
        return controller
    }

    private fun createActionHandlers(configuration: Configuration): WebViewActionHandlers {
        return WebViewActionHandlers().apply {
            commonBridgeActions.register(this)
            registerSuspend(WebViewAction.READY) { handleReadyAction(configuration) }
            register(WebViewAction.INIT) {
                hasPageAnswered = true
                BridgeMessage.SUCCESS_PAYLOAD
            }
            register(WebViewAction.CONTENT_RENDERED, ::handleContentRenderedAction)
            register(WebViewAction.SHOW_IN_APP, ::handleShowInAppAction)
            registerSuspend(WebViewAction.FILTER_SHOWABLE_INAPPS, ::handleFilterShowableInappsAction)
        }
    }

    private fun handleReadyAction(configuration: Configuration): String {
        hasPageAnswered = true
        registerForBroadcasts()
        return startPayload(configuration)
    }

    private fun registerForBroadcasts() {
        if (isReleased) return
        if (isRegisteredForBroadcasts) return
        isRegisteredForBroadcasts = true
        webPageRegistry.register(this)
    }

    private fun unregisterFromBroadcasts() {
        if (!isRegisteredForBroadcasts) return
        isRegisteredForBroadcasts = false
        webPageRegistry.unregister(this)
    }

    override fun push(action: WebViewAction, payload: String) {
        if (isReleased) return
        val controller = webViewController ?: return
        Mindbox.mindboxScope.launch {
            loggingRunCatchingSuspending {
                val response = withTimeoutOrNull(Constants.WebView.readyTimeout.interval) {
                    sendActionAndAwaitResponse(controller, BridgeMessage.createAction(action, payload))
                }
                mindboxLogI(
                    "[EmbeddedBlock] push '$action' " +
                        (if (response != null) "confirmed" else "was not confirmed") + " by the page"
                )
            }
        }
    }

    private fun startPayload(configuration: Configuration): String = DataCollector(
        appContext = appContext,
        sessionStorageManager = sessionStorageManager,
        permissionManager = permissionManager,
        gson = gson,
        configuration = configuration,
        params = DataCollector.mergedParams(config = layer.params),
        inAppInsets = InAppInsets(),
        inAppId = inAppId,
        operation = null,
    ).get()

    private suspend fun handleFilterShowableInappsAction(message: BridgeMessage.Request): String {
        val askedIds = runCatching {
            gson.fromJson(message.payload, JsonObject::class.java)?.get("inappIds") as? JsonArray
        }.getOrNull() ?: throw IllegalArgumentException("no 'inappIds' array in the payload")
        val requestedIds = askedIds.mapNotNull { element ->
            runCatching { element.asJsonPrimitive.takeIf { primitive -> primitive.isString }?.asString }
                .getOrNull()
        }
        if (requestedIds.size != askedIds.size()) {
            mindboxLogE(
                "[EmbeddedBlock] ${askedIds.size() - requestedIds.size} of ${askedIds.size()} " +
                    "asked ids are not strings, skipping them"
            )
        }
        val showableIds = inAppInteractor.filterShowableInAppIds(inAppId, requestedIds)
        mindboxLogI(
            "[EmbeddedBlock] filterShowableInapps: ${requestedIds.size} id(s) asked, " +
                "${showableIds.size} allowed"
        )
        return gson.toJson(InAppIdsPayload(showableIds))
    }

    private fun handleContentRenderedAction(message: BridgeMessage.Request): String {
        hasPageAnswered = true
        if (didReportShownContent) {
            mindboxLogI("[EmbeddedBlock] The page reported itself again with nothing asked of it, ignoring: the block is already shown")
            return BridgeMessage.SUCCESS_PAYLOAD
        }
        val raw: Any? = runCatching {
            JSONObject(message.payload ?: BridgeMessage.EMPTY_PAYLOAD).get("count")
        }.getOrNull()
        val number = raw as? Number
            ?: throw refusedContentReport("missing or non-numeric 'count'")
        val count = number.toWholeIntOrNull()
            ?: throw refusedContentReport("'count' must be a whole number of items, got $number")
        if (count < 0) throw refusedContentReport("'count' must not be negative, got $count")
        mindboxLogI("[EmbeddedBlock] Page rendered $count content element(s)")
        if (count == 0) {
            report(EmbeddedBlockState.Empty)
            return BridgeMessage.SUCCESS_PAYLOAD
        }
        didReportShownContent = true
        renderedTimeToDisplay = timeProvider.monotonicElapsedSince(startTick)
        report(EmbeddedBlockState.Ready)
        if (isActive) accountForShow()
        return BridgeMessage.SUCCESS_PAYLOAD
    }

    private fun Number.toWholeIntOrNull(): Int? = when (this) {
        is Int -> this
        is Double -> toInt().takeIf { whole -> whole.toDouble() == this }
        else -> null
    }

    private fun refusedContentReport(reason: String): Throwable {
        mindboxLogE("[EmbeddedBlock] contentRendered refused: $reason")
        sendFailure(
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDescription = "The embedded block page reported contentRendered with an unusable payload: $reason",
            tags = gatedTags()
        )
        report(EmbeddedBlockState.Failed)
        return IllegalArgumentException(reason)
    }

    /**
     * The block drew something the user can see, so its in-app was shown. Reported once per
     * content instance; the content-change rule lives in the interactor's place slot, where
     * the session state is. Off screen the show waits — [start] re-asks when the block returns.
     */
    private fun accountForShow() {
        if (didAccountForShow) return
        if (!isUserPresent) {
            mindboxLogI("[EmbeddedBlock] Content rendered off screen, the show waits for the block to return")
            return
        }
        didAccountForShow = true
        val timeToDisplay = renderedTimeToDisplay ?: timeProvider.monotonicElapsedSince(startTick)
        Mindbox.mindboxScope.launch {
            loggingRunCatchingSuspending {
                inAppInteractor.recordBlockShow(placeSystemName, inAppId, frequency, timeToDisplay, gatedTags())
            }
        }.invokeOnCompletion { cause ->
            if (cause is CancellationException) didAccountForShow = false
        }
    }

    private fun handleShowInAppAction(message: BridgeMessage.Request): String {
        val payload = gson.fromJson<JsonObject>(message.payload).getOrNull()
            ?: throw IllegalArgumentException(SHOW_IN_APP_INVALID_PAYLOAD)
        val requestedId = payload.getOrNull(SHOW_IN_APP_ID_FIELD)
            ?.takeIf { element -> element.isJsonPrimitive && element.asJsonPrimitive.isString }
            ?.asString
        require(!requestedId.isNullOrEmpty()) { SHOW_IN_APP_INVALID_PAYLOAD }
        val extraParams: Map<String, JsonElement> =
            payload.getOrNull(SHOW_IN_APP_PARAMS_FIELD).safeAs<JsonObject>()
                ?.entrySet()?.associate { (key, value) -> key to value }
                ?: emptyMap()
        mindboxLogI(
            "[EmbeddedBlock] showInApp: inappId=$requestedId" +
                " index=${payload.getOrNull(SHOW_IN_APP_INDEX_FIELD)}" +
                " sourceInappId=${payload.getOrNull(SHOW_IN_APP_SOURCE_FIELD)}" +
                " with ${extraParams.size} param(s)"
        )
        if (lastState != EmbeddedBlockState.Loading && lastState != EmbeddedBlockState.Ready) {
            mindboxLogI("[EmbeddedBlock] Ignored a show request from a block that is not shown")
            return BridgeMessage.SUCCESS_PAYLOAD
        }
        inAppMessageManager.showInAppById(requestedId, extraParams)
        return BridgeMessage.SUCCESS_PAYLOAD
    }

    private fun onContentPageLoaded(content: WebViewHtmlContent) {
        val controller = webViewController ?: run {
            mindboxLogW("[EmbeddedBlock] WebView controller is null when loading content, skipping")
            return
        }
        lastLoadedContent = content
        hasPageAnswered = false
        didReportShownContent = false
        controller.loadContent(content)
    }

    private fun retryContentPageWithoutCache() {
        val controller = webViewController ?: return
        val content = lastLoadedContent ?: return
        mindboxLogI(
            "[EmbeddedBlock] Retrying block content load with cache bypassed " +
                "(${noCacheRetryPolicy.lastHttpErrorDetail})"
        )
        controller.setCacheBypass(true)
        onContentPageLoaded(content)
    }

    private fun sendFailure(
        failureReason: FailureReason,
        errorDescription: String,
        throwable: Throwable? = null,
        tags: Map<String, String>? = null,
    ) {
        if (!isActive) {
            mindboxLogI(
                "[EmbeddedBlock] $failureReason for $inAppId happened off screen, holding the " +
                    "report until the block is looked at"
            )
            heldFailure.compareAndSet(null, HeldFailure(failureReason, errorDescription, throwable, tags))
            if (isActive) flushHeldFailure()
            return
        }
        inAppFailureTracker.sendFailureWithContext(
            inAppId = inAppId,
            failureReason = failureReason,
            errorDescription = errorDescription,
            throwable = throwable,
            tags = tags
        )
    }

    private fun flushHeldFailure() {
        val held = heldFailure.getAndSet(null) ?: return
        inAppFailureTracker.sendFailureWithContext(
            inAppId = inAppId,
            failureReason = held.failureReason,
            errorDescription = held.errorDescription,
            throwable = held.throwable,
            tags = held.tags
        )
    }

    private fun reportLoadFailure(description: String, throwable: Throwable?) {
        sendFailure(
            failureReason = FailureReason.WEBVIEW_LOAD_FAILED,
            errorDescription = description,
            throwable = throwable,
            tags = gatedTags()
        )
        webViewController?.executeOnViewThread { report(EmbeddedBlockState.Failed) }
            ?: mainHandler.post { if (!isReleased) report(EmbeddedBlockState.Failed) }
    }

    private fun report(state: EmbeddedBlockState) {
        lastState = state
        if (isActive) onStateChange?.invoke(state)
    }

    private suspend fun <T> Deferred<T>.awaitWithForegroundBudget(budget: Milliseconds): T? {
        var remaining = budget
        while (true) {
            if (!presence.value) {
                if (settlesBeforePresenceTurns(lookedAt = true)) return await()
                mindboxLogI(
                    "[EmbeddedBlock] Back on screen with a data push still unconfirmed — " +
                        "waiting out the remaining ${remaining.interval}ms"
                )
                continue
            }
            if (remaining.interval <= 0L) return null
            val spendStartedTick = timeProvider.monotonicMillis()
            when (withTimeoutOrNull(remaining.interval) { settlesBeforePresenceTurns(lookedAt = false) }) {
                null -> return null
                true -> return await()
                false -> remaining = Milliseconds(
                    (remaining.interval - timeProvider.monotonicElapsedSince(spendStartedTick).interval)
                        .coerceAtLeast(0L)
                )
            }
        }
    }

    private suspend fun Job.settlesBeforePresenceTurns(lookedAt: Boolean): Boolean = coroutineScope {
        val presenceTurned = async { presence.first { isLookedAt -> isLookedAt == lookedAt } }
        try {
            select {
                this@settlesBeforePresenceTurns.onJoin { true }
                presenceTurned.onJoin { false }
            }
        } finally {
            presenceTurned.cancel()
        }
    }

    private suspend fun sendActionAndAwaitResponse(
        controller: WebViewController,
        message: BridgeMessage.Request,
    ): BridgeMessage.Response {
        val responseDeferred: CompletableDeferred<BridgeMessage.Response> = CompletableDeferred()
        pendingResponsesById[message.id] = responseDeferred
        sendActionInternal(controller, message) { error ->
            if (responseDeferred.isActive) {
                responseDeferred.completeExceptionally(
                    IllegalStateException("Failed to send message ${message.action} to WebView: $error")
                )
            }
        }
        return try {
            responseDeferred.await()
        } finally {
            pendingResponsesById.remove(message.id)
        }
    }

    private fun sendActionInternal(
        controller: WebViewController,
        message: BridgeMessage,
        onError: ((String?) -> Unit)? = null,
    ) {
        mindboxLogI("SDK -> send message $message")
        val json: String = gson.toJson(message)
        val escapedJson: String = JSONObject.quote(json)
        controller.evaluateJavaScript(JS_CALL_BRIDGE.format(escapedJson)) { result ->
            if (result != JS_RETURN) {
                onError?.invoke(result)
            }
        }
    }

    private fun handleRequest(
        message: BridgeMessage.Request,
        controller: WebViewController,
        handlers: WebViewActionHandlers,
    ) {
        handlers.dispatch(
            message = message,
            isUserPresent = isUserPresent,
            isAlive = { !isReleased },
            launchSuspending = { handle -> Mindbox.mindboxScope.launch { handle() } },
            respond = { payload -> sendSuccessResponse(message, payload, controller) },
            refuse = { error ->
                mindboxLogW("[EmbeddedBlock] Page action ${message.action} was refused: ${error.message}")
                sendErrorResponse(message, error, controller)
            },
        )
    }

    private fun sendSuccessResponse(
        message: BridgeMessage.Request,
        responsePayload: String?,
        controller: WebViewController,
    ) {
        sendActionInternal(controller, BridgeMessage.createResponseAction(message, responsePayload))
    }

    private fun sendErrorResponse(
        message: BridgeMessage.Request,
        error: Throwable,
        controller: WebViewController,
    ) {
        val json: String = gson.toBridgeErrorPayload(error)
        mindboxLogW("[EmbeddedBlock] Error response for ${message.action}: $json")
        sendActionInternal(controller, BridgeMessage.createErrorAction(message, json))
    }

    private fun handleResponse(message: BridgeMessage.Response) {
        val responseDeferred = pendingResponsesById.remove(message.id)
        if (responseDeferred == null) {
            mindboxLogW("No pending response for id ${message.id}")
            return
        }
        if (!responseDeferred.isCompleted) {
            responseDeferred.complete(message)
        }
    }

    private fun handleError(message: BridgeMessage.Error) {
        mindboxLogW("[EmbeddedBlock] WebView error message: ${message.payload}")
        pendingResponsesById.remove(message.id)?.cancel("WebView error: ${message.payload}")
    }

    private fun cancelPendingResponses(reason: String) {
        val error = CancellationException(reason)
        pendingResponsesById.values.forEach { deferred ->
            if (!deferred.isCompleted) {
                deferred.cancel(error)
            }
        }
        pendingResponsesById.clear()
    }

    private fun gatedTags(): Map<String, String>? =
        tags.gatedTags(featureToggleManager.isEnabled(SEND_INAPP_TAGS_FEATURE))

    private data class InAppIdsPayload(
        @SerializedName("inappIds")
        val inappIds: List<String>?,
    )

    private companion object {
        private const val SHOW_IN_APP_ID_FIELD = "inappId"
        private const val SHOW_IN_APP_PARAMS_FIELD = "params"
        private const val SHOW_IN_APP_INDEX_FIELD = "index"
        private const val SHOW_IN_APP_SOURCE_FIELD = "sourceInappId"
        private const val SHOW_IN_APP_INVALID_PAYLOAD = "Invalid payload: missing or empty 'inappId'"
        private const val JS_RETURN = "true"
        private const val JS_BRIDGE = "window.bridgeMessagesHandlers.emit"
        private const val JS_CALL_BRIDGE = "(()=>{try{$JS_BRIDGE(%s);return!0}catch(_){return!1}})()"
    }
}
