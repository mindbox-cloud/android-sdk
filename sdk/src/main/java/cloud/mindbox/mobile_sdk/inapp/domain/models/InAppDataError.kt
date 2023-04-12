package cloud.mindbox.mobile_sdk.inapp.domain.models

import com.android.volley.VolleyError

internal class CustomerSegmentationError(volleyError: VolleyError) :
    Exception(volleyError)

internal class GeoError(volleyError: VolleyError) : Exception(volleyError)

internal class ProductSegmentationError(volleyError: VolleyError) :
    Exception(volleyError)