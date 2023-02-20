package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationFetchStatus
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import kotlinx.coroutines.flow.first

internal class InAppSegmentationRepositoryImpl(
    private val context: Context,
    private val inAppMapper: InAppMapper,
    private val sessionStorageManager: SessionStorageManager,
) : InAppSegmentationRepository {

    override var unShownInApps: List<InApp> = mutableListOf()

    override suspend fun fetchSegmentations() {
        if (unShownInApps.isEmpty()) {
            MindboxLoggerImpl.d(
                this,
                "No unshown inapps. Do not request segmentations"
            )
            sessionStorageManager.segmentationFetchStatus =
                SegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
            return
        }
        MindboxLoggerImpl.d(
            this,
            "Request segmentations"
        )
        val configuration = DbManager.listenConfigurations().first()
        val response = GatewayManager.checkSegmentation(
            context = context,
            configuration = configuration,
            segmentationCheckRequest = inAppMapper.mapToSegmentationCheckRequest(unShownInApps)
        )
        sessionStorageManager.inAppSegmentations = inAppMapper.mapToSegmentationCheck(response)
        sessionStorageManager.segmentationFetchStatus =
            SegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
    }

    override fun setSegmentationStatus(status: SegmentationFetchStatus) {
        sessionStorageManager.segmentationFetchStatus = status
    }

    override fun getSegmentationFetched(): SegmentationFetchStatus {
        return LoggingExceptionHandler.runCatching(SegmentationFetchStatus.SEGMENTATION_FETCH_ERROR) {
            sessionStorageManager.segmentationFetchStatus
        }
    }

    override fun getSegmentations(): List<CustomerSegmentationInApp> {
        return LoggingExceptionHandler.runCatching(emptyList()) {
            sessionStorageManager.inAppSegmentations?.customerSegmentations ?: emptyList()
        }
    }
}