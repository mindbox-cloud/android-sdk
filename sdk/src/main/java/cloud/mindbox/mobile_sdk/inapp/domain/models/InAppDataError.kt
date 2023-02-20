package cloud.mindbox.mobile_sdk.inapp.domain.models

import com.android.volley.VolleyError

internal class SegmentationError(val volleyError: VolleyError) : Exception(volleyError.message)

internal class GeoError(val volleyError: VolleyError) : Exception(volleyError.message)