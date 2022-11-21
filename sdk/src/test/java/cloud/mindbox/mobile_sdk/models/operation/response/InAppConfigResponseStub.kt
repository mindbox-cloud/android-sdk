package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.*

internal class InAppConfigResponseStub {
    companion object {
        fun getConfig() =
            InAppConfig(inApps = listOf(InApp(id = "040810aa-d135-49f4-8916-7e68dcc61c71",
                minVersion = 1,
                maxVersion = null,
                targeting = Targeting(type = "simple",
                    segmentation = "af30f24d-5097-46bd-94b9-4274424a87a7",
                    segment = "af30f24d-5097-46bd-94b9-4274424a87a7"),
                form = Form(variants = listOf(Payload.SimpleImage(type = "simpleImage",
                    imageUrl = "https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg",
                    redirectUrl = "https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71",
                    intentPayload = "123"))))))

        fun getConfigResponseMalformedJson() =
            "\"inapps\":[{\"id\":\"040810aa-d135-49f4-8916-7e68dcc61c71\",\"sdkVersion\":{\"min\":1,\"max\":null},\"targeting\":{\"segmentation\":\"af30f24d-5097-46bd-94b9-4274424a87a7\",\"segment\":\"af30f24d-5097-46bd-94b9-4274424a87a7\",\"$\type\":\"simple\"},\"form\":{\"variants\":[{\"imageUrl\":\"https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg\",\"redirectUrl\":\"https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71\",\"intentPayload\":\"123\",\"\$type\":\"simpleImage\"}]}}]}"


        fun getConfigResponseErrorJson() =
            "{\"inapps\":[{\"id\":\"040810aa-d135-49f4-8916-7e68dcc61c71\",\"sdkVersion\":{\"min\":1,\"max\":null},\"targeting\":{\"segmentation\":\"af30f24d-5097-46bd-94b9-4274424a87a7\",\"segment\":\"af30f24d-5097-46bd-94b9-4274424a87a7\",\"$\type\":\"simple\"},\"form\":{\"variants\":[{\"imageUrl\":\"https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg\",\"redirectUrl\":\"https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71\",\"intentPayload\":\"123\",\"\$type\":\"simpleI5mage\"}]}}]}"


        fun getConfigResponseJson() =
            "{\"inapps\":[{\"id\":\"040810aa-d135-49f4-8916-7e68dcc61c71\",\"sdkVersion\":{\"min\":1,\"max\":null},\"targeting\":{\"segmentation\":\"af30f24d-5097-46bd-94b9-4274424a87a7\",\"segment\":\"af30f24d-5097-46bd-94b9-4274424a87a7\",\"$\type\":\"simple\"},\"form\":{\"variants\":[{\"imageUrl\":\"https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg\",\"redirectUrl\":\"https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71\",\"intentPayload\":\"123\",\"\$type\":\"simpleImage\"}]}}]}"
    }


}