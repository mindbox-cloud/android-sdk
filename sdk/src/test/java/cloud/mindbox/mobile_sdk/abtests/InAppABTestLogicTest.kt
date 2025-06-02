package cloud.mindbox.mobile_sdk.abtests

import cloud.mindbox.mobile_sdk.abmixer.CustomerAbMixer
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.ABTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(BlockJUnit4ClassRunner::class)
internal class InAppABTestLogicTest {

    private val threeInapps = listOf(
        "655f5ffa-de86-4224-a0bf-229fe208ed0d",
        "6f93e2ef-0615-4e63-9c80-24bcb9e83b83",
        "b33ca779-3c99-481f-ad46-91282b0caf04",
    )
    private val fourInapps = listOf(
        "655f5ffa-de86-4224-a0bf-229fe208ed0d",
        "6f93e2ef-0615-4e63-9c80-24bcb9e83b83",
        "b33ca779-3c99-481f-ad46-91282b0caf04",
        "d1b312bd-aa5c-414c-a0d8-8126376a2a9b",
    )

    private val abtest = ABTest("", null, null, "", listOf())
    private val variant =
        ABTest.Variant("", "inapps", ABTest.Variant.VariantKind.ALL, 0, 100, listOf())

    @Test
    fun `abtest logic is empty variants`() = runTest {
        assertEquals(threeInapps.toSet(), calculateInApps(25, listOf(), threeInapps))
    }

    @Test
    fun `abtest logic two variants with config of three inapps`() = runTest {
        val abtests = listOf(
            abtest.copy(
                variants = listOf(
                    variant.copy(
                        lower = 0,
                        upper = 50,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf()
                    ),
                    variant.copy(
                        lower = 50,
                        upper = 100,
                        kind = ABTest.Variant.VariantKind.ALL,
                        inapps = listOf()
                    )
                )
            )
        )

        assertEquals(setOf<String>(), calculateInApps(25, abtests, threeInapps))
        assertEquals(threeInapps.toSet(), calculateInApps(75, abtests, threeInapps))

        val inapps1withExtra = threeInapps + "test"
        assertEquals(setOf<String>(), calculateInApps(25, abtests, inapps1withExtra))
        assertEquals(threeInapps.toSet() + "test", calculateInApps(75, abtests, inapps1withExtra))
    }

    @Test
    fun `abtest logic two variants with config of four inapps`() = runTest {
        val abtests = listOf(
            abtest.copy(
                variants = listOf(
                    variant.copy(
                        lower = 0,
                        upper = 50,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf()
                    ),
                    variant.copy(
                        lower = 50,
                        upper = 100,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf(
                            "655f5ffa-de86-4224-a0bf-229fe208ed0d",
                            "b33ca779-3c99-481f-ad46-91282b0caf04"
                        )
                    )
                )
            )
        )

        assertEquals(
            setOf("6f93e2ef-0615-4e63-9c80-24bcb9e83b83", "d1b312bd-aa5c-414c-a0d8-8126376a2a9b"),
            calculateInApps(0, abtests, fourInapps)
        )
        assertEquals(
            fourInapps.toSet(),
            calculateInApps(99, abtests, fourInapps)
        )

        val inapps2withExtra = fourInapps + "test"
        assertEquals(
            setOf(
                "6f93e2ef-0615-4e63-9c80-24bcb9e83b83",
                "d1b312bd-aa5c-414c-a0d8-8126376a2a9b",
                "test"
            ),
            calculateInApps(0, abtests, inapps2withExtra)
        )
        assertEquals(
            fourInapps.toSet() + "test",
            calculateInApps(99, abtests, inapps2withExtra)
        )
    }

    @Test
    fun `abtest logic three variants config of three inapps`() = runTest {
        val abtests = listOf(
            abtest.copy(
                variants = listOf(
                    variant.copy(
                        lower = 0,
                        upper = 30,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf()
                    ),
                    variant.copy(
                        lower = 30,
                        upper = 65,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf(
                            "655f5ffa-de86-4224-a0bf-229fe208ed0d",
                            "b33ca779-3c99-481f-ad46-91282b0caf04"
                        )
                    ),
                    variant.copy(
                        lower = 65,
                        upper = 100,
                        kind = ABTest.Variant.VariantKind.ALL,
                        inapps = listOf()
                    )
                )
            )
        )

        assertEquals(
            emptySet<String>(),
            calculateInApps(1, abtests, threeInapps)
        )
        assertEquals(
            setOf("655f5ffa-de86-4224-a0bf-229fe208ed0d", "b33ca779-3c99-481f-ad46-91282b0caf04"),
            calculateInApps(30, abtests, threeInapps)
        )
        assertEquals(
            threeInapps.toSet(),
            calculateInApps(65, abtests, threeInapps)
        )
    }

    @Test
    fun `abtest logic three variants with config of four inapps`() = runTest {
        val abtests = listOf(
            abtest.copy(
                variants = listOf(
                    variant.copy(
                        lower = 0,
                        upper = 27,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf()
                    ),
                    variant.copy(
                        lower = 27,
                        upper = 65,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf("655f5ffa-de86-4224-a0bf-229fe208ed0d")
                    ),
                    variant.copy(
                        lower = 65,
                        upper = 100,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf("b33ca779-3c99-481f-ad46-91282b0caf04")
                    )
                )
            )
        )

        assertEquals(
            setOf("6f93e2ef-0615-4e63-9c80-24bcb9e83b83", "d1b312bd-aa5c-414c-a0d8-8126376a2a9b"),
            calculateInApps(10, abtests, fourInapps)
        )
        assertEquals(
            setOf(
                "6f93e2ef-0615-4e63-9c80-24bcb9e83b83",
                "d1b312bd-aa5c-414c-a0d8-8126376a2a9b",
                "655f5ffa-de86-4224-a0bf-229fe208ed0d"
            ),
            calculateInApps(64, abtests, fourInapps)
        )
        assertEquals(
            setOf(
                "6f93e2ef-0615-4e63-9c80-24bcb9e83b83",
                "d1b312bd-aa5c-414c-a0d8-8126376a2a9b",
                "b33ca779-3c99-481f-ad46-91282b0caf04"
            ),
            calculateInApps(65, abtests, fourInapps)
        )

        val inapps2withExtra = fourInapps + "!"
        assertEquals(
            setOf(
                "6f93e2ef-0615-4e63-9c80-24bcb9e83b83",
                "d1b312bd-aa5c-414c-a0d8-8126376a2a9b",
                "!"
            ),
            calculateInApps(10, abtests, inapps2withExtra)
        )
        assertEquals(
            setOf(
                "6f93e2ef-0615-4e63-9c80-24bcb9e83b83",
                "d1b312bd-aa5c-414c-a0d8-8126376a2a9b",
                "655f5ffa-de86-4224-a0bf-229fe208ed0d",
                "!"
            ),
            calculateInApps(64, abtests, inapps2withExtra)
        )
        assertEquals(
            setOf(
                "6f93e2ef-0615-4e63-9c80-24bcb9e83b83",
                "d1b312bd-aa5c-414c-a0d8-8126376a2a9b",
                "b33ca779-3c99-481f-ad46-91282b0caf04",
                "!"
            ),
            calculateInApps(65, abtests, inapps2withExtra)
        )
    }

    @Test
    fun `abtest logic two concrete with config of three inapps`() = runTest {
        val abtests = listOf(
            abtest.copy(
                variants = listOf(
                    variant.copy(
                        lower = 0,
                        upper = 99,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf(
                            "655f5ffa-de86-4224-a0bf-229fe208ed0d",
                            "b33ca779-3c99-481f-ad46-91282b0caf04"
                        )
                    ),
                    variant.copy(
                        lower = 99,
                        upper = 100,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf("6f93e2ef-0615-4e63-9c80-24bcb9e83b83")
                    )
                )
            )
        )

        assertEquals(
            setOf("655f5ffa-de86-4224-a0bf-229fe208ed0d", "b33ca779-3c99-481f-ad46-91282b0caf04"),
            calculateInApps(98, abtests, threeInapps)
        )
        assertEquals(
            setOf("6f93e2ef-0615-4e63-9c80-24bcb9e83b83"),
            calculateInApps(99, abtests, threeInapps)
        )

        val inapps1withExtra = threeInapps + "??"
        assertEquals(
            setOf(
                "655f5ffa-de86-4224-a0bf-229fe208ed0d",
                "b33ca779-3c99-481f-ad46-91282b0caf04",
                "??"
            ),
            calculateInApps(98, abtests, inapps1withExtra)
        )
        assertEquals(
            setOf("6f93e2ef-0615-4e63-9c80-24bcb9e83b83", "??"),
            calculateInApps(99, abtests, inapps1withExtra)
        )
    }

    @Test
    fun `abtest logic two concrete variants without inapps`() = runTest {
        val abtests = listOf(
            abtest.copy(
                variants = listOf(
                    variant.copy(
                        lower = 0,
                        upper = 99,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf(
                            "655f5ffa-de86-4224-a0bf-229fe208ed0d",
                            "b33ca779-3c99-481f-ad46-91282b0caf04"
                        )
                    ),
                    variant.copy(
                        lower = 99,
                        upper = 100,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf("6f93e2ef-0615-4e63-9c80-24bcb9e83b83")
                    )
                )
            )
        )

        assertEquals(
            emptySet<String>(),
            calculateInApps(0, abtests, listOf())
        )
        assertEquals(
            emptySet<String>(),
            calculateInApps(99, abtests, listOf())
        )
    }

    @Test
    fun `abtest logic five variants with config of four inapps`() = runTest {
        val abtests = listOf(
            abtest.copy(
                variants = listOf(
                    variant.copy(
                        lower = 0,
                        upper = 10,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf("655f5ffa-de86-4224-a0bf-229fe208ed0d")
                    ),
                    variant.copy(
                        lower = 10,
                        upper = 20,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf("6f93e2ef-0615-4e63-9c80-24bcb9e83b83")
                    ),
                    variant.copy(
                        lower = 20,
                        upper = 30,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf("b33ca779-3c99-481f-ad46-91282b0caf04")
                    ),
                    variant.copy(
                        lower = 30,
                        upper = 70,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf("d1b312bd-aa5c-414c-a0d8-8126376a2a9b")
                    ),
                    variant.copy(
                        lower = 70,
                        upper = 100,
                        kind = ABTest.Variant.VariantKind.ALL,
                        inapps = listOf()
                    )
                )
            )
        )

        assertEquals(
            setOf("655f5ffa-de86-4224-a0bf-229fe208ed0d"),
            calculateInApps(5, abtests, fourInapps)
        )
        assertEquals(
            setOf("6f93e2ef-0615-4e63-9c80-24bcb9e83b83"),
            calculateInApps(15, abtests, fourInapps)
        )
        assertEquals(
            setOf("b33ca779-3c99-481f-ad46-91282b0caf04"),
            calculateInApps(25, abtests, fourInapps)
        )
        assertEquals(
            setOf("d1b312bd-aa5c-414c-a0d8-8126376a2a9b"),
            calculateInApps(35, abtests, fourInapps)
        )
        assertEquals(
            fourInapps.toSet(),
            calculateInApps(75, abtests, fourInapps)
        )
    }

    @Test
    fun `two abtests logic with config of three inapps`() = runTest {
        val abtests = listOf(
            abtest.copy(
                salt = "saLt1",
                variants = listOf(
                    variant.copy(
                        lower = 0,
                        upper = 25,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf()
                    ),
                    variant.copy(
                        lower = 25,
                        upper = 100,
                        kind = ABTest.Variant.VariantKind.ALL,
                        inapps = listOf()
                    )
                )
            ),
            abtest.copy(
                salt = "SALT2",
                variants = listOf(
                    variant.copy(
                        lower = 0,
                        upper = 75,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf()
                    ),
                    variant.copy(
                        lower = 75,
                        upper = 100,
                        kind = ABTest.Variant.VariantKind.CONCRETE,
                        inapps = listOf(
                            "655f5ffa-de86-4224-a0bf-229fe208ed0d",
                            "6f93e2ef-0615-4e63-9c80-24bcb9e83b83"
                        )
                    )
                )
            )
        )

        val mixer24and74 = mockk<CustomerAbMixer> {
            every { stringModulusHash(any(), "SALT1") } returns (24)
            every { stringModulusHash(any(), "SALT2") } returns (74)
        }

        assertEquals(
            setOf<String>(),
            calculateInApps(mixer24and74, abtests, threeInapps)
        )

        val mixer24and99 = mockk<CustomerAbMixer> {
            every { stringModulusHash(any(), "SALT1") } returns (24)
            every { stringModulusHash(any(), "SALT2") } returns (99)
        }
        assertEquals(
            setOf<String>(),
            calculateInApps(mixer24and99, abtests, threeInapps)
        )

        val mixer99and74 = mockk<CustomerAbMixer> {
            every { stringModulusHash(any(), "SALT1") } returns (99)
            every { stringModulusHash(any(), "SALT2") } returns (74)
        }
        assertEquals(
            setOf("b33ca779-3c99-481f-ad46-91282b0caf04"),
            calculateInApps(mixer99and74, abtests, threeInapps)
        )

        val mixer99and99 = mockk<CustomerAbMixer> {
            every { stringModulusHash(any(), "SALT1") } returns (99)
            every { stringModulusHash(any(), "SALT2") } returns (99)
        }
        assertEquals(
            threeInapps.toSet(),
            calculateInApps(mixer99and99, abtests, threeInapps)
        )
    }

    private suspend fun calculateInApps(
        hash: Int,
        abtests: List<ABTest>,
        inapps: List<String>
    ): Set<String> {
        val repository: MobileConfigRepository = mockk {
            coEvery { getABTests() } returns (abtests)
        }
        val mixer: CustomerAbMixer = mockk {
            every { stringModulusHash(any(), any()) } returns (hash)
        }
        return InAppABTestLogic(mixer, repository).getInAppsPool(inapps)
    }

    private suspend fun calculateInApps(
        mixer: CustomerAbMixer,
        abtests: List<ABTest>,
        inapps: List<String>
    ): Set<String> {
        val repository: MobileConfigRepository = mockk {
            coEvery { getABTests() } returns (abtests)
        }
        return InAppABTestLogic(mixer, repository).getInAppsPool(inapps)
    }
}
