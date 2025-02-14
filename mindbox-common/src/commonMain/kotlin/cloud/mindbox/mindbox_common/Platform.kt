package cloud.mindbox.mindbox_common

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

expect fun platform(): String

expect fun commonVersion(): String

object MindboxCommon {
    private val client = HttpClient()

    fun commonFunction(): String {
        return commonVersion()
    }

    suspend fun getConfig(): String {
        val response = client.get("https://ktor.io/docs/")
        return response.bodyAsText()
    }
}
