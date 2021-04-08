package cloud.mindbox.mobile_sdk

import com.orhanobut.hawk.Hawk
import io.paperdb.Paper
import io.paperdb.PaperDbException


internal fun removeConfiguration() {
    try {
        val configurationBook = Paper.book("mindbox_configuration_book")
        configurationBook.destroy()
    } catch (exception: PaperDbException) {
        exception.printStackTrace()
    }
}

fun clearPreferences() {
    Hawk.deleteAll()
}