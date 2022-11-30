package cloud.mindbox.mobile_sdk.inapp.data

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.di.dataModule
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigStub
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.*
import io.mockk.junit4.MockKRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.get
import org.koin.test.mock.MockProviderRule

internal class InAppRepositoryImplTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(dataModule)
    }

    private lateinit var inAppMessageMapper: InAppMessageMapper

    private lateinit var gson: Gson

    private lateinit var context: Context

    private lateinit var inAppRepository: InAppRepositoryImpl

    @Before
    fun onTestStart() {
        gson = get()
        inAppRepository = InAppRepositoryImpl(mockkClass(InAppMessageMapper::class),
            gson,
            mockk())
        mockkObject(MindboxPreferences)
    }


    @Test
    fun `shown inApp ids is not empty and is a valid json`() {
        val testHashSet = hashSetOf("71110297-58ad-4b3c-add1-60df8acb9e5e",
            "ad487f74-924f-44f0-b4f7-f239ea5643c5")
        every { MindboxPreferences.shownInAppIds } returns
                "[\"71110297-58ad-4b3c-add1-60df8acb9e5e\",\"ad487f74-924f-44f0-b4f7-f239ea5643c5\"]"
        assertTrue(inAppRepository.getShownInApps().containsAll(testHashSet))
    }

    @Test
    fun `shownInApp ids returns null`() {
        every { MindboxPreferences.shownInAppIds } returns "a"
        assertNotNull(inAppRepository.getShownInApps())
    }

    @Test
    fun `shown inApp ids empty`() {
        val expectedIds = hashSetOf<String>()
        every { MindboxPreferences.shownInAppIds } returns ""
        val actualIds = inAppRepository.getShownInApps()
        assertTrue(expectedIds.containsAll(actualIds))
    }

    @Test
    fun `shown inApp ids is not empty and is not a json`() {
        every { MindboxPreferences.shownInAppIds } returns "123"
        val expectedResult = hashSetOf<String>()
        val actualResult = inAppRepository.getShownInApps()
        assertTrue(actualResult.containsAll(expectedResult))
    }

    @Test
    fun `save shown inApp success`() {
        val expectedJson = """
            ["123","456"]
        """.trimIndent()
        every { MindboxPreferences.shownInAppIds } returns "[123]"
        val test = inAppRepository.getShownInApps()
        inAppRepository.saveShownInApp("456")
        verify (exactly = 1) {
            MindboxPreferences.shownInAppIds = expectedJson
        }
    }

    /*@Test
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
        assertEquals(expectedResult, actualResult)
    }*/

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