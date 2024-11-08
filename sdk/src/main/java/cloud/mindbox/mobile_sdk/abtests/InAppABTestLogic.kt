package cloud.mindbox.mobile_sdk.abtests

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.ABTest
import cloud.mindbox.mobile_sdk.logger.MindboxLog
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences

internal class InAppABTestLogic(
    private val mixer: CustomerAbMixer,
    private val repository: MobileConfigRepository,
) : MindboxLog {

    suspend fun getInAppsPool(allInApps: List<String>): Set<String> {
        val abtests = repository.getABTests()
        val uuid = MindboxPreferences.deviceUuid

        if (abtests.isEmpty()) {
            logI("Abtests is empty. Use all inApps.")
            return allInApps.toSet()
        }
        if (allInApps.isEmpty()) {
            return emptySet()
        }

        val inAppsForAbtest: List<Set<String>> = abtests.map { abtest ->

            val hash = mixer.stringModulusHash(
                identifier = uuid,
                salt = abtest.salt.uppercase(),
            )

            logI("Mixer calculate hash $hash for abtest ${abtest.id} with salt ${abtest.salt} and deviceUuid $uuid")

            val targetVariantInApps: MutableSet<String> = mutableSetOf()
            val otherVariantInApps: MutableSet<String> = mutableSetOf()

            abtest.variants.onEach { variant ->
                val inApps: List<String> = when (variant.kind) {
                    ABTest.Variant.VariantKind.ALL -> allInApps
                    ABTest.Variant.VariantKind.CONCRETE -> variant.inapps
                }
                if ((variant.lower until variant.upper).contains(hash)) {
                    targetVariantInApps.addAll(inApps)
                    logI("Selected variant $variant for ${abtest.id}")
                } else {
                    otherVariantInApps.addAll(inApps)
                }
            }

            (targetVariantInApps + (allInApps - otherVariantInApps)).also { inappsSet ->
                logI("For abtest ${abtest.id} determined $inappsSet")
            }
        }

        if (inAppsForAbtest.isEmpty()) {
            logW("No inApps after calculation abtests logic. InApp will not be shown.")
            return setOf()
        }

        return if (inAppsForAbtest.size == 1) {
            inAppsForAbtest.first()
        } else {
            getIntersectionForAllABTests(inAppsForAbtest)
        }
    }

    private fun getIntersectionForAllABTests(inAppsForAbtest: List<Set<String>>): Set<String> =
        inAppsForAbtest.reduce { acc, list -> acc.intersect(list) }
}
