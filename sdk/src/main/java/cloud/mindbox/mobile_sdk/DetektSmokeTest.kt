package cloud.mindbox.mobile_sdk

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

// Temporary file to verify detekt rules catch violations. Delete after CI check.

// GsonMissingSerializedName: caught via fromJson with class literal (smokeFromJson below)
internal data class SmokeTestDto(
    val id: String,
    val name: String,
)

// GsonMissingSerializedName: caught via toJson first-arg type (smokeToJson below)
// Also caught transitively: SmokeTestNestedDto.dto is SmokeTestDto → recurses into SmokeTestDto
internal data class SmokeTestNestedDto(
    val value: Int,
    val dto: SmokeTestDto,
)

// GsonMissingSerializedName: caught via visitClass — partial @SerializedName contract
// (some fields annotated, some not → always flagged, even without a call site)
internal data class SmokeTestPartialDto(
    @SerializedName("title") val title: String,
    val subtitle: String,
)

// GsonMissingSerializedName: caught via TypeToken type argument (smokeTypeToken below)
internal data class SmokeTestTypeTokenDto(
    val key: String,
    val payload: String,
)

// GsonMissingSerializedName: caught via TypeToken<List<T>> — item type extracted from List argument
internal data class SmokeTestListItemDto(
    val item: String,
)

// GsonMissingSerializedName: caught via auto-detected generic wrapper (checkIfIndirectGsonCall)
// smokeGenericWrapper is generic + contains Gson().toJson() → rule resolves its source and flags callers
internal data class SmokeTestWrapperDto(
    val x: Int,
)

// --- Call sites that trigger GsonMissingSerializedName ---

// fromJson with class literal → catches SmokeTestDto
private fun smokeFromJson(json: String): SmokeTestDto =
    Gson().fromJson(json, SmokeTestDto::class.java)

// toJson with first-arg type resolution → catches SmokeTestNestedDto + transitive SmokeTestDto
private fun smokeToJson(dto: SmokeTestNestedDto): String =
    Gson().toJson(dto)

// TypeToken direct type argument → catches SmokeTestTypeTokenDto
private val smokeTypeToken = object : TypeToken<SmokeTestTypeTokenDto>() {}

// TypeToken with List<T> → extracts SmokeTestListItemDto from the List argument
private val smokeListToken = object : TypeToken<List<SmokeTestListItemDto>>() {}

// Generic wrapper auto-detection → catches SmokeTestWrapperDto
private fun <T> smokeGenericWrapper(obj: T): String = Gson().toJson(obj)

private val smokeWrapped = smokeGenericWrapper(SmokeTestWrapperDto(1))
