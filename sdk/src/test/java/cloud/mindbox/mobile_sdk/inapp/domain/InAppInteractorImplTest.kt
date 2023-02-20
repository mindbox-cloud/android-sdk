/*
package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.di.dataModule
import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.di.MindboxKoin
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.inapp.domain.models.Kind
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationCheckResult
import cloud.mindbox.mobile_sdk.models.GeoTargetingStub
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.SegmentationCheckInAppStub
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigStub
import com.android.volley.VolleyError
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.mockkClass
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declareMock

@OptIn(ExperimentalCoroutinesApi::class)
internal class InAppInteractorImplTest : KoinTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(dataModule)
    }

    @get:Rule
    val mockProvider = MockProviderRule.create { clazz ->
        mockkClass(clazz)
    }

    @MockK
    private lateinit var mobileConfigRepository: MobileConfigRepository

    @OverrideMockKs
    private lateinit var inAppInteractor: InAppInteractorImpl

    @MockK
    private lateinit var inAppGeoRepository: InAppGeoRepository

    @Before
    fun onTestStart() {
        mockkObject(MindboxKoin)
        every { MindboxKoin.koin } returns getKoin()
        every { mobileConfigRepository.listenInAppEvents() } returns flowOf(InAppEventType.AppStartup)
        every { mobileConfigRepository.sendInAppTargetingHit(any()) } just runs
        inAppGeoRepository = declareMock {
            coEvery { inAppGeoRepository.fetchGeo() } just runs
        }
        every {
            inAppGeoRepository.getGeo()
        } returns GeoTargetingStub.getGeoTargeting().copy(cityId = "123",
            regionId = "456",
            countryId = "789")
    }


    @Test
    fun `should choose in-app without targeting`() = runTest {
        val validId = "123456"
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(inApps = listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingTrueNode(), id = validId),
                    InAppStub.getInApp()
                        .copy(id = "123", targeting = InAppStub.getTargetingUnionNode().copy("or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode(),
                                InAppStub.getTargetingSegmentNode()))))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }


    @Test
    fun `should choose in-app with targeting`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp().copy(status = "success",
            customerSegmentations = listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "999",
                    segment = "777")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingTrueNode(),
                            InAppStub.getTargetingSegmentNode()
                                .copy(type = "segment",
                                    kind = Kind.POSITIVE,
                                    segmentationExternalId = "999",
                                    segmentExternalId = "777"))),
                        id = validId),
                    InAppStub.getInApp()
                        .copy(id = "123", targeting = InAppStub.getTargetingUnionNode().copy("or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode(),
                                InAppStub.getTargetingSegmentNode()))))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `should return null if no in-apps present`() = runTest {
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(emptyList()))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            awaitComplete()
        }
    }

    @Test
    fun `should return null if network exception`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } throws VolleyError()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingTrueNode(),
                            InAppStub.getTargetingSegmentNode())), id = validId),
                    InAppStub.getInApp()
                        .copy(id = "123", targeting = InAppStub.getTargetingUnionNode().copy("or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode(),
                                InAppStub.getTargetingSegmentNode()))))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            awaitComplete()
        }
    }

    @Test
    fun `should throw exception if non network error`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } throws Error()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingTrueNode(),
                            InAppStub.getTargetingSegmentNode())), id = validId),
                    InAppStub.getInApp()
                        .copy(id = "123", targeting = InAppStub.getTargetingUnionNode().copy("or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode(),
                                InAppStub.getTargetingSegmentNode()))))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertTrue(awaitError() is Error)
        }
    }

    @Test
    fun `config has only targeting in-apps`() {
        var rez = true
        inAppInteractor.javaClass.getDeclaredMethod("getConfigWithInAppsBeforeFirstPendingPreCheck",
            InAppConfig::class.java).apply {
            isAccessible = true
            val invocationRez = invoke(inAppInteractor, InAppConfigStub.getConfig()
                .copy(inApps = listOf(
                    InAppStub.getInApp().copy(targeting = InAppStub.getTargetingTrueNode()),
                    InAppStub.getInApp().copy(targeting = InAppStub.getTargetingUnionNode()
                        .copy("or",
                            nodes = listOf(InAppStub.getTargetingTrueNode(),
                                InAppStub.getTargetingSegmentNode()))),
                    InAppStub.getInApp().copy(targeting = InAppStub.getTargetingIntersectionNode()
                        .copy("or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode(),
                                InAppStub.getTargetingSegmentNode()))),
                    InAppStub.getInApp().copy(targeting = InAppStub.getTargetingUnionNode()
                        .copy("or",
                            nodes = listOf(InAppStub.getTargetingIntersectionNode()
                                .copy(type = "and",
                                    nodes = listOf(InAppStub.getTargetingSegmentNode())),
                                InAppStub.getTargetingSegmentNode()))),
                ))) as InAppConfig
            invocationRez.inApps.forEach { inApp ->
                if (inApp.targeting.fetchTargetingInfo() == SegmentationCheckResult.PENDING) {
                    rez = false
                }
            }
        }
        assertTrue(rez)
    }

    @Test
    fun `validate in-app was shown list is empty`() = runTest {
        every { mobileConfigRepository.getShownInApps() } returns HashSet()
        val validId = "123456"
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingTrueNode(), id = validId),
                    InAppStub.getInApp()
                        .copy(id = "123", targeting = InAppStub.getTargetingUnionNode().copy("or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode(),
                                InAppStub.getTargetingSegmentNode()))))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `validate in-app was shown list isn't empty but does not contain current in-app id`() =
        runTest {
            every { mobileConfigRepository.getShownInApps() } returns hashSetOf("71110297-58ad-4b3c-add1-60df8acb9e5e",
                "ad487f74-924f-44f0-b4f7-f239ea5643c5")
            val validId = "123456"
            coEvery {
                mobileConfigRepository.fetchSegmentations(any())

            } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            every {
                mobileConfigRepository.listenInAppConfig()
            } answers {
                flow {
                    emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingTrueNode(), id = validId),
                        InAppStub.getInApp()
                            .copy(id = "123",
                                targeting = InAppStub.getTargetingUnionNode().copy("or",
                                    nodes = listOf(InAppStub.getTargetingSegmentNode(),
                                        InAppStub.getTargetingSegmentNode()))))))
                }
            }
            inAppInteractor.processEventAndConfig().test {
                assertEquals(validId, awaitItem().inAppId)
                awaitComplete()
            }
        }

    @Test
    fun `validate in-app was shown list isn't empty and contains current in-app id`() = runTest {
        every { mobileConfigRepository.getShownInApps() } returns hashSetOf("71110297-58ad-4b3c-add1-60df8acb9e5e",
            "ad487f74-924f-44f0-b4f7-f239ea5643c5", "123")
        val validId = "123456"
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "456", segment = "123"),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "123", segment = "132")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingTrueNode(), id = "123"),
                    InAppStub.getInApp()
                        .copy(id = validId,
                            targeting = InAppStub.getTargetingUnionNode().copy("or",
                                nodes = listOf(InAppStub.getTargetingSegmentNode()
                                    .copy(type = "segment",
                                        kind = Kind.NEGATIVE,
                                        segmentationExternalId = "456",
                                        segmentExternalId = "132"),
                                    InAppStub.getTargetingSegmentNode().copy(
                                        type = "segment",
                                        kind = Kind.POSITIVE,
                                        segmentationExternalId = "123",
                                        segmentExternalId = "132",
                                    )))))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of two positives segmentation both success`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "123", segment = "132"),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "456", segment = "132")
            ))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.POSITIVE, "123", "132"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "456", "132"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of two positives segmentation error`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy(
                "Success",
                listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "345", segment = "345"),
                    SegmentationCheckInAppStub.getCustomerSegmentation()
                        .copy(segmentation = "123", segment = "132")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig()
                    .copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingIntersectionNode()
                            .copy(type = "and",
                                nodes = listOf(InAppStub.getTargetingSegmentNode()
                                    .copy(type = "segment",
                                        kind = Kind.POSITIVE,
                                        segmentationExternalId = "345",
                                        segmentExternalId = "132"),
                                    InAppStub.getTargetingSegmentNode()
                                        .copy(type = "segment",
                                            kind = Kind.POSITIVE,
                                            segmentationExternalId = "123",
                                            segmentExternalId = "132"))),
                            id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of two negatives segmentation success`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "456", segment = "133"),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "789", segment = "")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.NEGATIVE, "456", "132"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.NEGATIVE, "789", "132"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of two negatives segmentation error`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "456", segment = "132"),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "789", segment = "")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.NEGATIVE, "456", "132"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.NEGATIVE, "789", "132"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of one positive and one negative segmentation success`() =
        runTest {
            val validId = "123456"
            every {
                mobileConfigRepository.getShownInApps()
            } returns HashSet()
            coEvery {
                mobileConfigRepository.fetchSegmentations(any())

            } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
                .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "456", segment = "123"),
                    SegmentationCheckInAppStub.getCustomerSegmentation()
                        .copy(segmentation = "123", segment = "")))
            every {
                mobileConfigRepository.listenInAppConfig()
            } answers {
                flow {
                    emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingIntersectionNode()
                            .copy(type = "and",
                                nodes = listOf(InAppStub.getTargetingSegmentNode()
                                    .copy("segment", kind = Kind.POSITIVE, "456", "123"),
                                    InAppStub.getTargetingSegmentNode()
                                        .copy("segment",
                                            kind = Kind.NEGATIVE,
                                            "123",
                                            "132"))),
                            id = validId))))
                }
            }
            inAppInteractor.processEventAndConfig().test {
                assertEquals(validId, awaitItem().inAppId)
                awaitComplete()
            }
        }

    @Test
    fun `customer is in intersection of one positive and one negative segmentation positive error`() =
        runTest {
            val validId = "123456"
            every {
                mobileConfigRepository.getShownInApps()
            } returns HashSet()
            coEvery {
                mobileConfigRepository.fetchSegmentations(any())

            } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
                .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "456", segment = ""),
                    SegmentationCheckInAppStub.getCustomerSegmentation()
                        .copy(segmentation = "123", segment = "")))
            every {
                mobileConfigRepository.listenInAppConfig()
            } answers {
                flow {
                    emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingIntersectionNode()
                            .copy(type = "and",
                                nodes = listOf(InAppStub.getTargetingSegmentNode()
                                    .copy("segment", kind = Kind.POSITIVE, "456", "123"),
                                    InAppStub.getTargetingSegmentNode()
                                        .copy("segment",
                                            kind = Kind.NEGATIVE,
                                            "123",
                                            "132"))),
                            id = validId))))
                }
            }
            inAppInteractor.processEventAndConfig().test {
                awaitComplete()
            }
        }

    @Test
    fun `customer is in intersection of one positive and one negative segmentation negative error`() =
        runTest {
            val validId = "123456"
            every {
                mobileConfigRepository.getShownInApps()
            } returns HashSet()
            coEvery {
                mobileConfigRepository.fetchSegmentations(any())

            } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
                .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "456", segment = "123"),
                    SegmentationCheckInAppStub.getCustomerSegmentation()
                        .copy(segmentation = "123", segment = "123")))
            every {
                mobileConfigRepository.listenInAppConfig()
            } answers {
                flow {
                    emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingIntersectionNode()
                            .copy(type = "and",
                                nodes = listOf(InAppStub.getTargetingSegmentNode()
                                    .copy("segment", kind = Kind.POSITIVE, "456", "132"),
                                    InAppStub.getTargetingSegmentNode()
                                        .copy("segment",
                                            kind = Kind.NEGATIVE,
                                            "123",
                                            "132"))),
                            id = validId))))
                }
            }
            inAppInteractor.processEventAndConfig().test {
                awaitComplete()
            }
        }

    @Test
    fun `customer is in union of two positives segmentation first segmentation true`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "456", segment = "132"),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "123", segment = "")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "or",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.POSITIVE, "456", "132"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "123", "132"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in union of two positives segmentation second segmentation true`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "456", segment = ""),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "123", segment = "456")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "or",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.POSITIVE, "456", "132"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "123", "456"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in union of two positives segmentation both segmentation true`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "456", segment = "123"),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "123", segment = "124")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "or",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.POSITIVE, "456", "123"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "123", "124"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in union of two positives segmentation both segmentation false`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "456", segment = ""),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "123", segment = "")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "or",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.POSITIVE, "456", "132"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "123", "132"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            awaitComplete()
        }
    }

    @Test
    fun `customer is in union of two negatives second true segmentation`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "456", segment = "456"),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "123", segment = "")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.NEGATIVE, "456", "456"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.NEGATIVE, "123", "132"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in union of two negatives first true segmentation`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "456", segment = "243"),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "132", segment = "123")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.NEGATIVE, "456", "132"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.NEGATIVE, "132", "132"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in union of two negatives both true segmentation`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "456", segment = "133"),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "123", segment = "133")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.NEGATIVE, "456", "132"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.NEGATIVE, "123", "132"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in union of two negatives both false segmentation`() = runTest {
        val validId = "123456"
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "456", segment = "132"),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "123", segment = "132")))
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.NEGATIVE, "456", "132"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.NEGATIVE, "123", "132"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            awaitComplete()
        }
    }

    @Test
    fun `customer is in union of one positive and one negative segmentation positive check`() =
        runTest {
            val validId = "123456"
            every {
                mobileConfigRepository.getShownInApps()
            } returns HashSet()
            coEvery {
                mobileConfigRepository.fetchSegmentations(any())

            } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
                .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "234", segment = "234"),
                    SegmentationCheckInAppStub.getCustomerSegmentation()
                        .copy(segmentation = "345", segment = "345")))
            every {
                mobileConfigRepository.listenInAppConfig()
            } answers {
                flow {
                    emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "234", "234"),
                                InAppStub.getTargetingSegmentNode()
                                    .copy("segment", kind = Kind.NEGATIVE, "345", "345"))),
                            id = validId))))
                }
            }
            inAppInteractor.processEventAndConfig().test {
                assertEquals(validId, awaitItem().inAppId)
                awaitComplete()
            }
        }

    @Test
    fun `customer is in union of one positive and one negative segmentation both true check`() =
        runTest {
            val validId = "123456"
            every {
                mobileConfigRepository.getShownInApps()
            } returns HashSet()
            coEvery {
                mobileConfigRepository.fetchSegmentations(any())

            } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
                .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "345", segment = "345"),
                    SegmentationCheckInAppStub.getCustomerSegmentation()
                        .copy(segmentation = "234", segment = "")))
            every {
                mobileConfigRepository.listenInAppConfig()
            } answers {
                flow {
                    emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "345", "345"),
                                InAppStub.getTargetingSegmentNode()
                                    .copy("segment", kind = Kind.NEGATIVE, "234", "123"))),
                            id = validId))))
                }
            }
            inAppInteractor.processEventAndConfig().test {
                assertEquals(validId, awaitItem().inAppId)
                awaitComplete()
            }
        }

    @Test
    fun `customer is in union of one positive and one negative segmentation negative check`() =
        runTest {
            val validId = "123456"
            every {
                mobileConfigRepository.getShownInApps()
            } returns HashSet()
            coEvery {
                mobileConfigRepository.fetchSegmentations(any())

            } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
                .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "345", segment = ""),
                    SegmentationCheckInAppStub.getCustomerSegmentation()
                        .copy(segmentation = "234", segment = "")))
            every {
                mobileConfigRepository.listenInAppConfig()
            } answers {
                flow {
                    emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "345", "132"),
                                InAppStub.getTargetingSegmentNode()
                                    .copy("segment", kind = Kind.NEGATIVE, "234", "132"))),
                            id = validId))))
                }
            }
            inAppInteractor.processEventAndConfig().test {
                assertEquals(validId, awaitItem().inAppId)
                awaitComplete()
            }
        }

    @Test
    fun `customer is in union of one positive and one negative segmentation both false check`() =
        runTest {
            val validId = "123456"
            every {
                mobileConfigRepository.getShownInApps()
            } returns HashSet()
            coEvery {
                mobileConfigRepository.fetchSegmentations(any())

            } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
                .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "345", segment = ""),
                    SegmentationCheckInAppStub.getCustomerSegmentation()
                        .copy(segmentation = "234", segment = "132")))
            every {
                mobileConfigRepository.listenInAppConfig()
            } answers {
                flow {
                    emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "345", "132"),
                                InAppStub.getTargetingSegmentNode()
                                    .copy("segment", kind = Kind.NEGATIVE, "234", "132"))),
                            id = validId))))
                }
            }
            inAppInteractor.processEventAndConfig().test {
                awaitComplete()
            }
        }


    @Test
    fun `customer is not in segmentation for second in-app`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "456", segment = ""),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "123", segment = "")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(id = "123",
                        targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                            nodes = listOf(InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "456", "132")))),
                    InAppStub.getInApp()
                        .copy(
                            id = validId, targeting = InAppStub.getTargetingUnionNode().copy("or",
                                nodes = listOf(InAppStub.getTargetingSegmentNode()
                                    .copy("segment",
                                        kind = Kind.NEGATIVE,
                                        "123",
                                        "132")))))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in segmentation for second in-app`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = "123", segment = "132"),
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = "124", segment = "132")))
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode()
                        .copy(type = "and",
                            nodes = listOf(InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.NEGATIVE, "123", "132"),
                                InAppStub.getTargetingTrueNode())), id = "123"),
                    InAppStub.getInApp()
                        .copy(id = validId,
                            targeting = InAppStub.getTargetingUnionNode().copy("or",
                                nodes = listOf(InAppStub.getTargetingSegmentNode().copy("segment",
                                    kind = Kind.POSITIVE,
                                    "124",
                                    "132")))))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of positive country success`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingCountryNode()
                            .copy("country", kind = Kind.POSITIVE, listOf("789", "456")))),
                        id = validId)
                )))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of positive country error`() = runTest {
        val validId = "123456"
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingCountryNode()
                            .copy("country", kind = Kind.POSITIVE, listOf("124", "456")))),
                        id = validId)
                )))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of negative country error`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingCountryNode()
                            .copy(type = "country", kind = Kind.NEGATIVE,
                                ids = listOf("789", "456")))),
                        id = validId)
                )))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of negative country success`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingCountryNode()
                            .copy("country", kind = Kind.NEGATIVE, listOf("124", "456")))),
                        id = validId)
                )))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of positive region success`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingRegionNode()
                            .copy("region", kind = Kind.POSITIVE, listOf("123", "456")))),
                        id = validId)
                )))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of positive region error`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingRegionNode()
                            .copy("region", kind = Kind.POSITIVE, listOf("123", "455")))),
                        id = validId)
                )))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of negative region error`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingRegionNode()
                            .copy("region", kind = Kind.NEGATIVE, listOf("123", "456")))),
                        id = validId)
                )))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of negative region success`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingRegionNode()
                            .copy("segment", kind = Kind.NEGATIVE, listOf("123", "455")))),
                        id = validId)
                )))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of positive city success`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingCityNode()
                            .copy("segment", kind = Kind.POSITIVE, listOf("123", "456")))),
                        id = validId)
                )))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of positive city error`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingCityNode()
                            .copy("segment", kind = Kind.POSITIVE, listOf("124", "456")))),
                        id = validId)
                )))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of negative city error`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingCityNode()
                            .copy("segment", kind = Kind.NEGATIVE, listOf("123", "456")))),
                        id = validId)
                )))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of negative city success`() = runTest {
        val validId = "123456"
        every {
            mobileConfigRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            mobileConfigRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            mobileConfigRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingCityNode()
                            .copy("segment", kind = Kind.NEGATIVE, listOf("124", "456")))),
                        id = validId)
                )))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

}


*/
