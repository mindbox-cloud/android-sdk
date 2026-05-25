package cloud.mindbox.detekt

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GsonSerializedNameRuleTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `reports data class used by Gson class literal in another file`(): Unit {
        val modelFile: File = writeSourceFile(
            relativePath = "com/example/GeoTargeting.kt",
            content = """
                package com.example

                data class GeoTargeting(
                    val cityId: String,
                    val regionId: String,
                    val countryId: String,
                )
            """.trimIndent()
        )
        writeSourceFile(
            relativePath = "com/example/GeoSerializationManager.kt",
            content = """
                package com.example

                import com.google.gson.Gson

                class GeoSerializationManager(private val gson: Gson) {
                    fun deserialize(json: String): GeoTargeting {
                        return gson.fromJson(json, GeoTargeting::class.java)
                    }
                }
            """.trimIndent()
        )

        val messages: List<String> = lintFile(modelFile)
        assertEquals(
            listOf(
                "GeoTargeting.cityId must declare @SerializedName.",
                "GeoTargeting.regionId must declare @SerializedName.",
                "GeoTargeting.countryId must declare @SerializedName."
            ),
            messages
        )
    }

    @Test
    fun `reports data class used by TypeToken typealias`(): Unit {
        val sourceFile: File = writeSourceFile(
            relativePath = "com/example/PushToken.kt",
            content = """
                package com.example

                import com.google.gson.Gson
                import com.google.gson.reflect.TypeToken

                data class PrefPushToken(
                    val token: String,
                    val updateDate: Long,
                )

                typealias PushTokenMap = Map<String, String>
                typealias PrefPushTokenMap = Map<String, PrefPushToken>

                fun decode(json: String): PrefPushTokenMap {
                    val type = object : TypeToken<PrefPushTokenMap>() {}.type
                    return Gson().fromJson(json, type)
                }
            """.trimIndent()
        )

        val messages: List<String> = lintFile(sourceFile)
        assertEquals(
            listOf(
                "PrefPushToken.token must declare @SerializedName.",
                "PrefPushToken.updateDate must declare @SerializedName."
            ),
            messages
        )
    }

    @Test
    fun `reports data class used by typed toJson variable`(): Unit {
        val modelFile: File = writeSourceFile(
            relativePath = "com/example/GeoTargeting.kt",
            content = """
                package com.example

                data class GeoTargeting(
                    val cityId: String,
                )
            """.trimIndent()
        )
        writeSourceFile(
            relativePath = "com/example/GeoSerializationManager.kt",
            content = """
                package com.example

                import com.google.gson.Gson

                class GeoSerializationManager(private val gson: Gson) {
                    fun serialize(inAppGeo: GeoTargeting): String {
                        return gson.toJson(inAppGeo)
                    }
                }
            """.trimIndent()
        )

        val messages: List<String> = lintFile(modelFile)
        assertEquals(listOf("GeoTargeting.cityId must declare @SerializedName."), messages)
    }

    @Test
    fun `reports data class used by toJson constructor call`(): Unit {
        val modelFile: File = writeSourceFile(
            relativePath = "com/example/GeoTargeting.kt",
            content = """
                package com.example

                data class GeoTargeting(
                    val cityId: String,
                )
            """.trimIndent()
        )
        writeSourceFile(
            relativePath = "com/example/GeoSerializationManager.kt",
            content = """
                package com.example

                import com.google.gson.Gson

                class GeoSerializationManager(private val gson: Gson) {
                    fun serialize(): String {
                        return gson.toJson(GeoTargeting(cityId = "1"))
                    }
                }
            """.trimIndent()
        )

        val messages: List<String> = lintFile(modelFile)
        assertEquals(listOf("GeoTargeting.cityId must declare @SerializedName."), messages)
    }

    @Test
    fun `reports data class used by generic fromJson`(): Unit {
        val sourceFile: File = writeSourceFile(
            relativePath = "com/example/GeoTargeting.kt",
            content = """
                package com.example

                data class GeoTargeting(
                    val cityId: String,
                )

                fun deserialize(json: String): GeoTargeting {
                    return fromJson<GeoTargeting>(json)
                }
            """.trimIndent()
        )

        val messages: List<String> = lintFile(sourceFile)
        assertEquals(listOf("GeoTargeting.cityId must declare @SerializedName."), messages)
    }

    @Test
    fun `reports data class used by TypeToken object declaration`(): Unit {
        val sourceFile: File = writeSourceFile(
            relativePath = "com/example/GeoTargeting.kt",
            content = """
                package com.example

                import com.google.gson.reflect.TypeToken

                data class GeoTargeting(
                    val cityId: String,
                )

                val geoTargetingType = object : TypeToken<GeoTargeting>() {}.type
            """.trimIndent()
        )

        val messages: List<String> = lintFile(sourceFile)
        assertEquals(listOf("GeoTargeting.cityId must declare @SerializedName."), messages)
    }

    @Test
    fun `reports data class used by nested TypeToken generic`(): Unit {
        val sourceFile: File = writeSourceFile(
            relativePath = "com/example/GeoTargeting.kt",
            content = """
                package com.example

                import com.google.gson.reflect.TypeToken

                data class GeoTargeting(
                    val cityId: String,
                )

                val geoTargetingListType = object : TypeToken<List<GeoTargeting>>() {}.type
            """.trimIndent()
        )

        val messages: List<String> = lintFile(sourceFile)
        assertEquals(listOf("GeoTargeting.cityId must declare @SerializedName."), messages)
    }

    @Test
    fun `reports data classes used by wrapper methods`(): Unit {
        val sourceFile: File = writeSourceFile(
            relativePath = "com/example/GeoTargeting.kt",
            content = """
                package com.example

                data class FromJsonModel(
                    val fromJsonValue: String,
                )

                data class ToJsonModel(
                    val toJsonValue: String,
                )

                data class OperationBodyModel(
                    val operationBodyValue: String,
                )

                data class ConvertJsonModel(
                    val convertJsonValue: String,
                )

                fun useWrappers(json: String): String {
                    fromJsonTyped<FromJsonModel>(json)
                    toJsonTyped<ToJsonModel>(ToJsonModel(toJsonValue = "1"))
                    operationBodyJson<OperationBodyModel>(OperationBodyModel(operationBodyValue = "2"))
                    convertJsonToBody(json, ConvertJsonModel::class.java)
                    return json
                }
            """.trimIndent()
        )

        val messages: List<String> = lintFile(sourceFile)
        assertEquals(
            listOf(
                "FromJsonModel.fromJsonValue must declare @SerializedName.",
                "ToJsonModel.toJsonValue must declare @SerializedName.",
                "OperationBodyModel.operationBodyValue must declare @SerializedName.",
                "ConvertJsonModel.convertJsonValue must declare @SerializedName."
            ),
            messages
        )
    }

    @Test
    fun `reports data class used only as field type of Gson-serialized class`(): Unit {
        val innerFile: File = writeSourceFile(
            relativePath = "com/example/InnerData.kt",
            content = """
                package com.example

                data class InnerData(
                    val value: String,
                )
            """.trimIndent()
        )
        writeSourceFile(
            relativePath = "com/example/OuterData.kt",
            content = """
                package com.example

                import com.google.gson.annotations.SerializedName

                data class OuterData(
                    @SerializedName("inner") val inner: InnerData,
                )
            """.trimIndent()
        )
        writeSourceFile(
            relativePath = "com/example/Serializer.kt",
            content = """
                package com.example

                import com.google.gson.Gson

                class Serializer {
                    fun serialize(data: OuterData): String = Gson().toJson(data)
                }
            """.trimIndent()
        )

        val messages: List<String> = lintFile(innerFile)
        assertEquals(listOf("InnerData.value must declare @SerializedName."), messages)
    }

    @Test
    fun `reports data class reachable via multi-level field nesting`(): Unit {
        val deepFile: File = writeSourceFile(
            relativePath = "com/example/DeepData.kt",
            content = """
                package com.example

                data class DeepData(
                    val label: String,
                )
            """.trimIndent()
        )
        writeSourceFile(
            relativePath = "com/example/MidData.kt",
            content = """
                package com.example

                import com.google.gson.annotations.SerializedName

                data class MidData(
                    @SerializedName("deep") val deep: DeepData,
                )
            """.trimIndent()
        )
        writeSourceFile(
            relativePath = "com/example/RootData.kt",
            content = """
                package com.example

                import com.google.gson.annotations.SerializedName

                data class RootData(
                    @SerializedName("mid") val mid: MidData,
                )
            """.trimIndent()
        )
        writeSourceFile(
            relativePath = "com/example/Api.kt",
            content = """
                package com.example

                import com.google.gson.Gson

                fun serialize(data: RootData): String = Gson().toJson(data)
            """.trimIndent()
        )

        val messages: List<String> = lintFile(deepFile)
        assertEquals(listOf("DeepData.label must declare @SerializedName."), messages)
    }

    @Test
    fun `reports data class used as generic field type of Gson-serialized class`(): Unit {
        val itemFile: File = writeSourceFile(
            relativePath = "com/example/Item.kt",
            content = """
                package com.example

                data class Item(
                    val label: String,
                )
            """.trimIndent()
        )
        writeSourceFile(
            relativePath = "com/example/Container.kt",
            content = """
                package com.example

                import com.google.gson.annotations.SerializedName

                data class Container(
                    @SerializedName("items") val items: List<Item>,
                )
            """.trimIndent()
        )
        writeSourceFile(
            relativePath = "com/example/Api.kt",
            content = """
                package com.example

                import com.google.gson.Gson

                fun serialize(c: Container): String = Gson().toJson(c)
            """.trimIndent()
        )

        val messages: List<String> = lintFile(itemFile)
        assertEquals(listOf("Item.label must declare @SerializedName."), messages)
    }

    @Test
    fun `reports data class that serializes itself via toJson(this)`(): Unit {
        val sourceFile: File = writeSourceFile(
            relativePath = "com/example/Payload.kt",
            content = """
                package com.example

                import com.google.gson.Gson

                data class Payload(
                    val id: String,
                    val value: Int,
                ) {
                    fun toJson(): String = Gson().toJson(this)
                }
            """.trimIndent()
        )

        val messages: List<String> = lintFile(sourceFile)
        assertEquals(
            listOf(
                "Payload.id must declare @SerializedName.",
                "Payload.value must declare @SerializedName.",
            ),
            messages
        )
    }

    @Test
    fun `does not report data class that is only a field type of a non-Gson class`(): Unit {
        val innerFile: File = writeSourceFile(
            relativePath = "com/example/InternalState.kt",
            content = """
                package com.example

                data class InternalState(
                    val flag: Boolean,
                )
            """.trimIndent()
        )
        writeSourceFile(
            relativePath = "com/example/ViewModel.kt",
            content = """
                package com.example

                data class ViewModel(
                    val state: InternalState,
                )
            """.trimIndent()
        )

        val messages: List<String> = lintFile(innerFile)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `reports only missing fields for partially annotated data class`(): Unit {
        val sourceFile: File = writeSourceFile(
            relativePath = "com/example/PartiallyAnnotated.kt",
            content = """
                package com.example

                import com.google.gson.annotations.SerializedName

                data class PartiallyAnnotated(
                    @SerializedName("id") val id: String,
                    val name: String,
                )
            """.trimIndent()
        )

        val messages: List<String> = lintFile(sourceFile)
        assertEquals(listOf("PartiallyAnnotated.name must declare @SerializedName."), messages)
    }

    @Test
    fun `does not report fully annotated data class`(): Unit {
        val sourceFile: File = writeSourceFile(
            relativePath = "com/example/FullyAnnotated.kt",
            content = """
                package com.example

                import com.google.gson.annotations.SerializedName

                data class FullyAnnotated(
                    @SerializedName("id") val id: String,
                    @SerializedName("name") val name: String,
                )
            """.trimIndent()
        )

        val messages: List<String> = lintFile(sourceFile)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `does not report data class that is not used by Gson`(): Unit {
        val sourceFile: File = writeSourceFile(
            relativePath = "com/example/InternalState.kt",
            content = """
                package com.example

                data class InternalState(
                    val value: String,
                )
            """.trimIndent()
        )

        val messages: List<String> = lintFile(sourceFile)
        assertTrue(messages.isEmpty())
    }

    private fun lintFile(file: File): List<String> {
        return GsonSerializedNameRule(config = TestConfig())
            .lint(file.toPath())
            .map { finding -> finding.message }
    }

    private fun writeSourceFile(relativePath: String, content: String): File {
        val sourceFile: File = temporaryFolder.root
            .resolve("src/main/java")
            .resolve(relativePath)
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(content)
        return sourceFile
    }
}
