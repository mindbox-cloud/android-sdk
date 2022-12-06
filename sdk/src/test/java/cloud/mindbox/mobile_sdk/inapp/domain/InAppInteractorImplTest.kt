package cloud.mindbox.mobile_sdk.inapp.domain

import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.inapp.domain.models.Kind
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationCheckResult
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.SegmentationCheckInAppStub
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigStub
import com.android.volley.VolleyError
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class InAppInteractorImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var inAppRepository: InAppRepository

    @OverrideMockKs
    private lateinit var inAppInteractor: InAppInteractorImpl

    @Before
    fun onTestStart() {
        every { inAppRepository.listenInAppEvents() } returns flowOf(InAppEventType.AppStartup)
    }


    @Test
    fun `should choose in-app without targeting`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            inAppRepository.listenInAppConfig()
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
    fun `should choose in-app with targeting`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp().copy(status = "success",
            customerSegmentations = listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                        externalId = "999")),
                    segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                            externalId = "777")))))
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingTrueNode(),
                            InAppStub.getTargetingSegmentNode()
                                .copy(type = "segment",
                                    kind = Kind.POSITIVE,
                                    segmentationExternalId = "999",
                                    segment_external_id = "777",
                                    segmentationInternalId = "434"))),
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } throws VolleyError()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } throws Error()
        every {
            inAppRepository.listenInAppConfig()
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
        inAppInteractor.javaClass.getDeclaredMethod("getConfigWithTargeting",
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
                if (inApp.targeting.preCheckTargeting() == SegmentationCheckResult.TRUE) {
                    rez = false
                }
            }
        }
        assertTrue(rez)
    }

    @Test
    fun `validate in-app was shown list is empty`() = runTest {
        every { inAppRepository.getShownInApps() } returns HashSet()
        val validId = "123456"
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        every {
            inAppRepository.listenInAppConfig()
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
            every { inAppRepository.getShownInApps() } returns hashSetOf("71110297-58ad-4b3c-add1-60df8acb9e5e",
                "ad487f74-924f-44f0-b4f7-f239ea5643c5")
            val validId = "123456"
            coEvery {
                inAppRepository.fetchSegmentations(any())

            } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            every {
                inAppRepository.listenInAppConfig()
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
        every { inAppRepository.getShownInApps() } returns hashSetOf("71110297-58ad-4b3c-add1-60df8acb9e5e",
            "ad487f74-924f-44f0-b4f7-f239ea5643c5", "123")
        val validId = "123456"
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                        "123")),
                    segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                            "123")
                    ))))
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingTrueNode(), id = "123"),
                    InAppStub.getInApp()
                        .copy(id = validId,
                            targeting = InAppStub.getTargetingUnionNode().copy("or",
                                nodes = listOf(InAppStub.getTargetingSegmentNode()
                                    .copy("segment", kind = Kind.NEGATIVE, "456", "132", "456"),
                                    InAppStub.getTargetingSegmentNode().copy("segment",
                                        kind = Kind.POSITIVE,
                                        "123",
                                        "132",
                                        "123")))))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of two positives segmentation success`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                        "123")),
                    segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                            "123")
                    ))))
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.POSITIVE, "123", "132", "123"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "123", "132", "123"))),
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy(
                "Success",
                listOf(
                    SegmentationCheckInAppStub.getCustomerSegmentation()
                        .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                            ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                                "345")),
                            segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                                ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                                    "345")
                            )),
                    SegmentationCheckInAppStub.getCustomerSegmentation()
                        .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                            ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                                "123")),
                            segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                                ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                                    "123")))))
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig()
                    .copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingIntersectionNode()
                            .copy(type = "and",
                                nodes = listOf(InAppStub.getTargetingSegmentNode()
                                    .copy("segment",
                                        kind = Kind.POSITIVE,
                                        "345",
                                        "132",
                                        "345"),
                                    InAppStub.getTargetingSegmentNode()
                                        .copy("segment",
                                            kind = Kind.POSITIVE,
                                            "123",
                                            "132",
                                            "123"))),
                            id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of two negatives segmentation success`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                        "456")),
                    segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                            null)
                    )), SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                        "789")),
                    segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                            null)
                    ))))
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.NEGATIVE, "456", "132", "456"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.NEGATIVE, "789", "132", "789"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in intersection of one positive and one negative segmentation`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                        "456")),
                    segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                            "123")
                    )), SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                        "123")),
                    segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                            null)
                    ))))
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.POSITIVE, "456", "132", "123"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.NEGATIVE, "123", "132", "456"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in union of two positives segmentation`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                        "456")),
                    segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                            "123")
                    )), SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                        "123")),
                    segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                            "124")
                    ))))
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "or",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.POSITIVE, "456", "132", "123"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "123", "132", "456"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in union of two negatives segmentation`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                        "456")),
                    segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                            "567")
                    )), SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                        "123")),
                    segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                            null)
                    ))))
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.NEGATIVE, "456", "132", "789"),
                            InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.NEGATIVE, "123", "132", "123"))),
                        id = validId))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is in union of one positive and one negative segmentation positive check`() =
        runTest {
            val validId = "123456"
            every {
                inAppRepository.getShownInApps()
            } returns HashSet()
            coEvery {
                inAppRepository.fetchSegmentations(any())

            } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
                .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                            "234")),
                        segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                            ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                                "123")
                        ))))
            every {
                inAppRepository.listenInAppConfig()
            } answers {
                flow {
                    emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "234", "132", "123"),
                                InAppStub.getTargetingSegmentNode()
                                    .copy("segment", kind = Kind.NEGATIVE, "234", "132", "123"))),
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
                inAppRepository.getShownInApps()
            } returns HashSet()
            coEvery {
                inAppRepository.fetchSegmentations(any())

            } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
                .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                        ids = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                            "234")),
                        segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                            ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                                null)
                        ))))
            every {
                inAppRepository.listenInAppConfig()
            } answers {
                flow {
                    emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "345", "132", "233"),
                                InAppStub.getTargetingSegmentNode()
                                    .copy("segment", kind = Kind.NEGATIVE, "234", "132", "123"))),
                            id = validId))))
                }
            }
            inAppInteractor.processEventAndConfig().test {
                assertEquals(validId, awaitItem().inAppId)
                awaitComplete()
            }
        }


    @Test
    fun `customer is in segmentation`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                        "123")
                ))))
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.POSITIVE, "456", "132", "123"),
                            InAppStub.getTargetingTrueNode())),
                        id = validId),
                    InAppStub.getInApp()
                        .copy(id = "123", targeting = InAppStub.getTargetingUnionNode().copy("or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.NEGATIVE, "123", "132", "456"),
                                InAppStub.getTargetingSegmentNode()))))))
            }
        }
        inAppInteractor.processEventAndConfig().test {
            assertEquals(validId, awaitItem().inAppId)
            awaitComplete()
        }
    }

    @Test
    fun `customer is not in segmentation`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy("Success", listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                        "123")
                ))))
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingSegmentNode()
                            .copy("segment", kind = Kind.NEGATIVE, "123", "132", "456"),
                            InAppStub.getTargetingTrueNode())), id = validId),
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
}


