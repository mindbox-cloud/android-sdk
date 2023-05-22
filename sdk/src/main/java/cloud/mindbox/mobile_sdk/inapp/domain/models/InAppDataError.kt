package cloud.mindbox.mobile_sdk.inapp.domain.models

import com.android.volley.VolleyError
import com.bumptech.glide.load.engine.GlideException

internal class CustomerSegmentationError(volleyError: VolleyError) :
    Exception(volleyError)

internal class GeoError(volleyError: VolleyError) : Exception(volleyError)

internal class ProductSegmentationError(volleyError: VolleyError) :
    Exception(volleyError)

internal class InAppContentFetchingError(error: GlideException?) : Exception(error)