package cloud.mindbox.mobile_sdk.network

import cloud.mindbox.mobile_sdk.models.InitData
import cloud.mindbox.mobile_sdk.models.PartialInitData
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.POST

interface RestApi {

    @POST
    fun firstInitSdk(
        @Field("endpointId") endpointId: String,
        @Field("operation") operation: String,
        @Field("deviceUUID") deviceId: String,
        @Body data: InitData
    )

    @POST
    fun secondInitSdk(
        @Field("endpointId") endpointId: String,
        @Field("operation") operation: String,
        @Field("deviceUUID") deviceId: String,
        @Body data: PartialInitData
    )
}