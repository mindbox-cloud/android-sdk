package cloud.mindbox.mobile_sdk.abtests

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.ABTest
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences

internal class InAppABTestLogic(
    private val mixer: CustomerAbMixer,
    private val repository: MobileConfigRepository,
) {

    suspend fun getInAppsPool(allInApps: List<String>): Set<String> {
        val abtests = repository.getABTests()
        val uuid = MindboxPreferences.deviceUuid

        if (abtests.isEmpty()) {
            this@InAppABTestLogic.mindboxLogW("Abtests is empty. Skip logic.")
            return allInApps.toSet()
        }
        if (allInApps.isEmpty()) {
            this@InAppABTestLogic.mindboxLogW("Config inApps is empty. Skip logic.")
            return allInApps.toSet()
        }

        val inAppsForAbtest: List<Set<String>> = abtests.map { abtest ->

            val hash = mixer.stringModulusHash(
                identifier = uuid,
                salt = abtest.salt.uppercase(),
            )

            this@InAppABTestLogic.mindboxLogD("mixer calculate $hash for salt ${abtest.salt}")

            val targetBranchInApps: MutableSet<String> = mutableSetOf()
            val otherBranchInApps: MutableSet<String> = mutableSetOf()

            abtest.variants.onEach { variant ->
                val inApps: List<String> = when (variant.kind) {
                    ABTest.Variant.VariantKind.ALL -> allInApps
                    ABTest.Variant.VariantKind.CONCRETE -> variant.inapps
                }
                if ((variant.lower until variant.upper).contains(hash)) {
                    targetBranchInApps.addAll(inApps)
                    this@InAppABTestLogic.mindboxLogD("Selected variant $variant for ${abtest.id}")
                } else {
                    otherBranchInApps.addAll(inApps)
                }
            }

            (targetBranchInApps + (allInApps - otherBranchInApps)).also {
                this@InAppABTestLogic.mindboxLogD("Selected inapps $it for ${abtest.id}")
            }
        }

        return inAppsForAbtest.takeIf { it.isNotEmpty() }?.reduce { acc, list ->
            acc.intersect(list)
        } ?: setOf()
    }

}