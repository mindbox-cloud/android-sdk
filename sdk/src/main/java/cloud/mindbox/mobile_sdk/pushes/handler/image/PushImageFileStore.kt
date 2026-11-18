package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.app.Notification
import android.content.ContentResolver
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.os.BundleCompat
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import java.io.File
import java.io.InputStream
import java.util.UUID

internal object PushImageFileStore {

    private const val DIRECTORY = "mindbox_push_images"
    private const val AUTHORITY_SUFFIX = ".mindbox.pushimages"
    private const val PROVIDER_ROOT = "push_images"
    private val staleAfter = Milliseconds(3_600_000L)

    fun save(context: Context, input: InputStream): File {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.gif")
        file.outputStream().use { output -> input.copyTo(output) }
        return file
    }

    fun uriFor(context: Context, file: File): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority(context.packageName + AUTHORITY_SUFFIX)
        .appendPath(PROVIDER_ROOT)
        .appendPath(file.name)
        .build()

    @RequiresApi(Build.VERSION_CODES.S)
    fun removeStale(context: Context, activeNotifications: List<Notification>, now: Timestamp) {
        val authority = context.packageName + AUTHORITY_SUFFIX
        val shownFileNames = activeNotifications
            .mapNotNull { BundleCompat.getParcelable(it.extras, Notification.EXTRA_PICTURE_ICON, Icon::class.java) }
            .filter { it.type == Icon.TYPE_URI && it.uri.authority == authority }
            .mapNotNull { it.uri.lastPathSegment }
            .toSet()
        File(context.cacheDir, DIRECTORY).listFiles()
            ?.filter { it.name !in shownFileNames && now.ms - it.lastModified() > staleAfter.interval }
            ?.forEach { it.delete() }
    }
}
