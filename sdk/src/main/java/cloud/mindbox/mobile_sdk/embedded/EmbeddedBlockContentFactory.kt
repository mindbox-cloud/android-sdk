package cloud.mindbox.mobile_sdk.embedded

import android.content.Context
import androidx.annotation.MainThread
import cloud.mindbox.mobile_sdk.embedded.webview.TempEmbeddedBlockPageContract
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedBlockWebViewPage
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedBlockWebViewProvider
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.embedded.mock.TempMindboxStoriesFeedMock
import cloud.mindbox.mobile_sdk.embedded.mock.TempStoriesFeedMockPage

internal class EmbeddedBlockContentFactory(
    private val rawConfig: () -> String = { MindboxPreferences.inAppConfig },
    private val overrideFor: (String) -> MindboxEmbeddedBlockDebug.Content? =
        EmbeddedBlockContentOverrides::contentFor,
) {

    fun create(context: Context, placeSystemName: String): EmbeddedContentResolution {
        // The debug override outranks the config: acceptance testing switches scenarios on the fly,
        // and a place the config knows nothing about still has to be reproducible.
        overrideFor(placeSystemName)?.let { override ->
            return resolveOverride(context, placeSystemName, override)
        }
        val rawConfig = rawConfig()
        if (rawConfig.isBlank()) {
            mindboxLogI("[EmbeddedBlock] Config is not loaded yet, the block keeps waiting")
            return EmbeddedContentResolution.NotReadyYet
        }
        val config = TempEmbeddedBlocksConfig.parse(rawConfig) ?: run {
            mindboxLogI("[EmbeddedBlock] Config has no inlineBlocks section, the block stays collapsed")
            return EmbeddedContentResolution.NothingToShow
        }
        val candidates = config.placementsFor(placeSystemName)
        if (candidates.isEmpty()) {
            mindboxLogI(
                "[EmbeddedBlock] Config has no placement '$placeSystemName', the block stays collapsed",
            )
            return EmbeddedContentResolution.NothingToShow
        }
        // TODO(MOBILE-324): the backend sends every candidate for the place and the SDK picks the
        //  one whose targeting matches; with the in-app migration that check is the in-app
        //  targeting engine — until then the first candidate wins.
        return EmbeddedContentResolution.Content(createPageContent(context, candidates.first()))
    }

    /**
     * Builds the same content the config path builds, from the override instead of the config: an
     * url is loaded exactly as a configured one is, ready-made markup takes the place of a page the
     * network does not serve, and an override of [MindboxEmbeddedBlockDebug.Content.Empty] answers
     * the way a place with no placement answers.
     */
    private fun resolveOverride(
        context: Context,
        placeSystemName: String,
        override: MindboxEmbeddedBlockDebug.Content,
    ): EmbeddedContentResolution {
        mindboxLogI("[EmbeddedBlock] Place '$placeSystemName' is served by a debug override")
        return when (override) {
            is MindboxEmbeddedBlockDebug.Content.Empty -> EmbeddedContentResolution.NothingToShow
            is MindboxEmbeddedBlockDebug.Content.Url -> EmbeddedContentResolution.Content(
                webViewContent(context, source = EmbeddedBlockWebViewPage.Source.Url(override.url), hasUrl = true),
            )
            is MindboxEmbeddedBlockDebug.Content.Html -> EmbeddedContentResolution.Content(
                webViewContent(context, source = EmbeddedBlockWebViewPage.Source.Html(override.html), hasUrl = false),
            )
        }
    }

    private fun createPageContent(
        context: Context,
        placement: TempEmbeddedBlockPlacement,
    ): EmbeddedContentProvider {
        val pageUrl = placement.pageUrl
        return webViewContent(
            context = context,
            source = pageUrl
                ?.let { EmbeddedBlockWebViewPage.Source.Url(it) }
                ?: EmbeddedBlockWebViewPage.Source.Html(
                    TempStoriesFeedMockPage.html(TempMindboxStoriesFeedMock.scenario),
                ),
            hasUrl = pageUrl != null,
        )
    }

    private fun webViewContent(
        context: Context,
        source: EmbeddedBlockWebViewPage.Source,
        hasUrl: Boolean,
    ): EmbeddedContentProvider {
        val page = EmbeddedBlockWebViewPage(
            source = source,
            context = context,
            bridgeName = TempEmbeddedBlockPageContract.BRIDGE_NAME,
            // Ready-made markup reports over the bridge and never sets the DOM flag; polling it
            // would spin for as long as the block is shown.
            domReadyFlag = TempEmbeddedBlockPageContract.DOM_READY_FLAG.takeIf { hasUrl },
        )
        return EmbeddedBlockWebViewProvider(page)
    }

    internal companion object {

        @MainThread
        fun resolve(context: Context, placeSystemName: String?): EmbeddedContentResolution = when {
            placeSystemName.isNullOrBlank() -> {
                mindboxLogI("[EmbeddedBlock] No placeSystemName, nothing to resolve")
                EmbeddedContentResolution.NothingToShow
            }
            else -> EmbeddedBlockContentFactory().create(context, placeSystemName)
        }
    }
}
