package cloud.mindbox.mobile_sdk.network

import cloud.mindbox.mobile_sdk.models.FullInitData
import cloud.mindbox.mobile_sdk.models.PartialInitData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface RestApi {

    @POST("async")
    suspend fun firstInitSdk(
        @Query("endpointId") endpointId: String,
        @Query("operation") operation: String,
        @Query("deviceUUID") deviceId: String,
        @Body data: FullInitData
    ): Response<Unit>

    @POST("async")
    suspend fun secondInitSdk(
        @Query("endpointId") endpointId: String,
        @Query("operation") operation: String,
        @Query("deviceUUID") deviceId: String,
        @Body data: PartialInitData
    ): Response<Unit>
}