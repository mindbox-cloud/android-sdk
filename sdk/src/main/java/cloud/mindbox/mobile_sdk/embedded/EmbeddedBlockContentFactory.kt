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
) {

    fun create(context: Context, placeSystemName: String): EmbeddedContentResolution {
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

    private fun createPageContent(
        context: Context,
        placement: TempEmbeddedBlockPlacement,
    ): EmbeddedContentProvider {
        val pageUrl = placement.pageUrl
        val page = EmbeddedBlockWebViewPage(
            source = pageUrl
                ?.let { EmbeddedBlockWebViewPage.Source.Url(it) }
                ?: EmbeddedBlockWebViewPage.Source.Html(
                    TempStoriesFeedMockPage.html(TempMindboxStoriesFeedMock.scenario),
                ),
            context = context,
            bridgeName = TempEmbeddedBlockPageContract.BRIDGE_NAME,
            // The mock reports over the bridge and never sets the DOM flag; polling it would spin
            // for as long as the block is shown.
            domReadyFlag = pageUrl?.let { TempEmbeddedBlockPageContract.DOM_READY_FLAG },
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
