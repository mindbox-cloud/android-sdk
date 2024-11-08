package cloud.mindbox.mobile_sdk.inapp.data.mapper

import cloud.mindbox.mobile_sdk.inapp.domain.models.TreeTargeting
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppMapperTest {

    @Test
    fun `mapToInAppConfig ids type to string`() {
        val mapper = InAppMapper()

        val result = mapper.mapToInAppConfig(
            InAppConfigResponse(
                inApps = listOf(
                    InAppDto(
                        id = "id",
                        frequency = FrequencyDto.FrequencyOnceDto(
                            type = "once",
                            kind = "lifetime",
                        ),
                        sdkVersion = null,
                        targeting = TreeTargetingDto.UnionNodeDto(
                            type = "or",
                            nodes = listOf(
                                TreeTargetingDto.CountryNodeDto(
                                    type = "country",
                                    kind = "positive",
                                    ids = listOf(1L, 2L, 3L),
                                ),
                                TreeTargetingDto.RegionNodeDto(
                                    type = "region",
                                    kind = "positive",
                                    ids = listOf(4L, 5L, 6L),
                                ),
                                TreeTargetingDto.CityNodeDto(
                                    type = "city",
                                    kind = "positive",
                                    ids = listOf(7L, 8L, 9L),
                                ),
                            ),
                        ),
                        form = null,
                    )
                ),
                monitoring = null,
                abtests = null,
                settings = null,
            )
        )

        val targeting = result.inApps.first().targeting as TreeTargeting.UnionNode

        val country = targeting.nodes.first() as TreeTargeting.CountryNode
        assertTrue(country.ids.contains("1"))
        assertTrue(country.ids.contains("2"))
        assertTrue(country.ids.contains("3"))

        val region = targeting.nodes[1] as TreeTargeting.RegionNode
        assertTrue(region.ids.contains("4"))
        assertTrue(region.ids.contains("5"))
        assertTrue(region.ids.contains("6"))

        val city = targeting.nodes.last() as TreeTargeting.CityNode
        assertTrue(city.ids.contains("7"))
        assertTrue(city.ids.contains("8"))
        assertTrue(city.ids.contains("9"))
    }
}
