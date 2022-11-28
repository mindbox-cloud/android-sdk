package cloud.mindbox.mobile_sdk.inapp.data

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigStub
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class InAppRepositoryImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var inAppMessageMapper: InAppMessageMapper

    @MockK
    private lateinit var gson: Gson

    @MockK
    private lateinit var context: Context

    @OverrideMockKs
    private lateinit var inAppRepository: InAppRepositoryImpl

    @Before
    fun onTestStart() {
        mockkObject(MindboxPreferences)
    }

    @Test
    fun `shown inApp ids is not empty and is a valid json`() {
        val testHashSet = hashSetOf("71110297-58ad-4b3c-add1-60df8acb9e5e",
            "ad487f74-924f-44f0-b4f7-f239ea5643c5")
        every { MindboxPreferences.shownInAppIds } returns
                "[\"71110297-58ad-4b3c-add1-60df8acb9e5e\",\"ad487f74-924f-44f0-b4f7-f239ea5643c5\"]"
        every {
            gson.fromJson<HashSet<String>>(any<String>(),
                object : TypeToken<HashSet<String>>() {}.type)
        } returns
                hashSetOf("71110297-58ad-4b3c-add1-60df8acb9e5e",
                    "ad487f74-924f-44f0-b4f7-f239ea5643c5")
        assertTrue(inAppRepository.shownInApps.containsAll(testHashSet))
    }

    @Test
    fun `shownInApp ids returns null`() {
        every {
            gson.fromJson<HashSet<String>>(any<String>(),
                object : TypeToken<HashSet<String>>() {}.type)
        } returns null
        every { MindboxPreferences.shownInAppIds } returns "a"
        assertNotNull(inAppRepository.shownInApps)
    }

    @Test
    fun `shown inApp ids empty`() {
        val expectedIds = hashSetOf<String>()
        every { MindboxPreferences.shownInAppIds } returns ""
        val actualIds = inAppRepository.shownInApps
        assertTrue(expectedIds.containsAll(actualIds))
    }

    @Test
    fun `shown inApp ids is not empty and is not a json`() {
        every { MindboxPreferences.shownInAppIds } returns "123"
        every {
            gson.fromJson<HashSet<String>>(any<String>(),
                object : TypeToken<HashSet<String>>() {}.type)
        } returns hashSetOf()
        val expectedResult = hashSetOf<String>()
        val actualResult = inAppRepository.shownInApps
        assertTrue(actualResult.containsAll(expectedResult))
    }

    @Test
    fun `simple image response mapping success test`() {
        val successJson =
            """{"inapps":[
                |{
                |"id":"040810aa-d135-49f4-8916-7e68dcc61c71",
                |"sdkVersion":
                |{
                |"min":1,
                |"max":null
                |},
                |"targeting":{
                |"segmentation":"af30f24d-5097-46bd-94b9-4274424a87a7",
                |"segment":"af30f24d-5097-46bd-94b9-4274424a87a7",
                |"${"$"}type":"simple"
                |},
                |"form":{
                |"variants":[
                |{
                |"imageUrl":"https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg",
                |"redirectUrl":"https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71",
                |"intentPayload":"123",
                |"${"$"}type":"simpleImage"
                |}]}}]}"""
                .trimMargin()

        val expectedResult = InAppConfigStub.getConfigDto()
            .copy(inApps = listOf(InAppStub.getInAppDto()
                .copy(id = "040810aa-d135-49f4-8916-7e68dcc61c71",
                    sdkVersion = InAppStub.getInAppDto().sdkVersion?.copy(minVersion = 1,
                        maxVersion = null),
                    targeting = InAppStub.getInAppDto().targeting?.copy(type = "simple",
                        segmentation = "af30f24d-5097-46bd-94b9-4274424a87a7",
                        segment = "af30f24d-5097-46bd-94b9-4274424a87a7"),
                    form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                        .copy(
                            type = "simpleImage",
                            imageUrl = "https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg",
                            redirectUrl = "https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71",
                            intentPayload = "123"))))))
        val actualResult =
            inAppRepository.deserializeConfigToConfigDto(successJson)
        assertNotNull(actualResult)
        assertEquals(actualResult, expectedResult)
    }

    @Test
    fun `simple image response mapping invalid json test`() {
        val errorJson = "123"
        assertNull(inAppRepository.deserializeConfigToConfigDto(errorJson))

    }

    @Test
    fun `simple image response mapping unknown type test`() {
        val errorJson =
            """{"inapps":[
                |{
                |"id":"040810aa-d135-49f4-8916-7e68dcc61c71",
                |"sdkVersion":
                |{
                |"min":1,
                |"max":null
                |},
                |"targeting":{
                |"segmentation":"af30f24d-5097-46bd-94b9-4274424a87a7",
                |"segment":"af30f24d-5097-46bd-94b9-4274424a87a7",
                |"${"$"}type":"simple"
                |},
                |"form":{
                |"variants":[
                |{
                |"imageUrl":"https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg",
                |"redirectUrl":"https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71",
                |"intentPayload":"123",
                |"${"$"}type":"sim5pleImage"
                |}]}}]}"""
                .trimMargin()
        assertNull(inAppRepository.deserializeConfigToConfigDto(errorJson))
    }

    @Test
    fun `simple image response mapping empty string test`() {
        assertNull(inAppRepository.deserializeConfigToConfigDto(""))
    }

    @Test
    fun `simple image response mapping malformed test`() {
        val malformedJson =
            """{"inapps":[
                |{
                |"id":"040810aa-d135-49f4-8916-7e68dcc61c71",
                |"sdkVersion":
                |{
                |"min":1,
                |"max":null
                |},
                |"targeting":{
                |"segmentation":"af30f24d-5097-46bd-94b9-4274424a87a7",
                |"segment":"af30f24d-5097-46bd-94b9-4274424a87a7",
                |"${"$"}type":"simple"
                |},
                |"form":{
                |"variants":[
                |{
                |"imageUrl":"https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg",
                |"redirectUrl":"https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71",
                |"intentPayload":"123",
                |"${"$"}type":"simpleImage"
                |}]}]}"""
                .trimMargin()
        assertNull(inAppRepository.deserializeConfigToConfigDto(malformedJson))
    }


}