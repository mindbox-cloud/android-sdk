package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.SessionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationFetchStatus
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import kotlinx.coroutines.flow.first

internal class InAppSegmentationRepositoryImpl(
    private val context: Context,
    private val inAppMapper: InAppMessageMapper,
    private val sessionManager: SessionManager,
) : InAppSegmentationRepository {

    override var unShownInApps: List<InApp> = mutableListOf()

    override suspend fun fetchSegmentations() {
        val configuration = DbManager.listenConfigurations().first()
        val response = GatewayManager.checkSegmentation(
            context = context,
            configuration = configuration,
            segmentationCheckRequest = inAppMapper.mapToSegmentationCheckRequest(unShownInApps)
        )
        sessionManager.inAppSegmentations = inAppMapper.mapToSegmentationCheck(response)
    }

    override fun getSegmentationFetched(): SegmentationFetchStatus {
        return LoggingExceptionHandler.runCatching(SegmentationFetchStatus.SEGMENTATION_FETCH_ERROR) {
            sessionManager.segmentationFetchStatus
        }
    }

    override fun getSegmentations(): List<CustomerSegmentationInApp> {
        return LoggingExceptionHandler.runCatching(emptyList()) {
            sessionManager.inAppSegmentations?.customerSegmentations ?: emptyList()
        }
    }
}