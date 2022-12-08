package cloud.mindbox.mobile_sdk.inapp.domain

import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.inapp.domain.models.Kind
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationCheckResult
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigStub
import io.mockk.*
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

    @MockK
    private lateinit var inAppGeoRepository: InAppGeoRepository

    @Before
    fun onTestStart() {
        every { inAppRepository.listenInAppEvents() } returns flowOf(InAppEventType.AppStartup)
        every { inAppRepository.sendInAppTargetingHit(any()) } just runs
    }


    @Test
    fun `should choose in-app without targeting`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
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
                if (inApp.targeting.preCheckTargeting() == SegmentationCheckResult.IMMEDIATE) {
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
                inAppRepository.getShownInApps()
            } returns HashSet()
            every {
                inAppRepository.listenInAppConfig()
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
                inAppRepository.getShownInApps()
            } returns HashSet()
            every {
                inAppRepository.listenInAppConfig()
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
                inAppRepository.getShownInApps()
            } returns HashSet()
            every {
                inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
    fun `customer is in union of two positives segmentation both segmentation false`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
    fun `customer is in union of two negatives first true segmentation`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
    fun `customer is in union of two negatives both true segmentation`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
                inAppRepository.getShownInApps()
            } returns HashSet()
            every {
                inAppRepository.listenInAppConfig()
            } answers {
                flow {
                    emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getTargetingUnionNode().copy(type = "or",
                            nodes = listOf(InAppStub.getTargetingSegmentNode()
                                .copy("segment", kind = Kind.POSITIVE, "234", "132"),
                                InAppStub.getTargetingSegmentNode()
                                    .copy("segment", kind = Kind.NEGATIVE, "345", "132"))),
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
                inAppRepository.getShownInApps()
            } returns HashSet()
            every {
                inAppRepository.listenInAppConfig()
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
    fun `customer is in union of one positive and one negative segmentation negative check`() =
        runTest {
            val validId = "123456"
            every {
                inAppRepository.getShownInApps()
            } returns HashSet()
            every {
                inAppRepository.listenInAppConfig()
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
                inAppRepository.getShownInApps()
            } returns HashSet()
            every {
                inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingCountryNode()
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
    fun `customer is in intersection of positive country error`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingCountryNode()
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
    fun `customer is in intersection of negative country error`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingCountryNode()
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
    fun `customer is in intersection of negative country success`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingCountryNode()
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

    @Test
    fun `customer is in intersection of positive region success`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingRegionNode()
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
    fun `customer is in intersection of positive region error`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingRegionNode()
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
    fun `customer is in intersection of negative region error`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingRegionNode()
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
    fun `customer is in intersection of negative region success`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
        } answers {
            flow {
                emit(InAppConfigStub.getConfig().copy(listOf(InAppStub.getInApp()
                    .copy(targeting = InAppStub.getTargetingIntersectionNode().copy(type = "and",
                        nodes = listOf(InAppStub.getTargetingRegionNode()
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

    @Test
    fun `customer is in intersection of positive city success`() = runTest {
        val validId = "123456"
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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
            inAppRepository.getShownInApps()
        } returns HashSet()
        every {
            inAppRepository.listenInAppConfig()
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


