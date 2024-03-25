package cloud.mindbox.mobile_sdk.inapp.data

import cloud.mindbox.mobile_sdk.inapp.data.validators.*
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.utils.Constants
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class InAppValidatorTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var modalWindowValidator: ModalWindowValidator

    private val sdkVersionValidator = SdkVersionValidator()

    @MockK
    private lateinit var snackbarValidator: SnackbarValidator

    @OverrideMockKs
    private lateinit var inAppValidator: InAppValidatorImpl

    @Before
    fun onTestStart() {
        every { modalWindowValidator.isValid(any()) } returns true
    }

    @Test
    fun `validate form dto variants null`() {
        assertFalse(
            inAppValidator.validateInApp(
                inApp = InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy("segment", kind = "positive", "asd", "def", "123")
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(variants = null)
                    )
            )
        )
    }

    @Test
    fun `validate form dto variants empty list`() {
        assertFalse(
            inAppValidator.validateInApp(
                inApp = InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy("segment", kind = "positive", "asd", "def", "123")
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(variants = emptyList())
                    )
            )
        )
    }

    @Test
    fun `validate form dto variants variant is null`() {
        assertFalse(
            inAppValidator.validateInApp(
                inApp = InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy("segment", kind = "positive", "asd", "def", "123")
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(variants = listOf(null))
                    )
            )
        )
    }

    @Test
    fun `validate form dto variants variant is not null but type is null`() {
        inAppValidator =
            InAppValidatorImpl(sdkVersionValidator, modalWindowValidator, snackbarValidator)

        every {
            modalWindowValidator.isValid(any())
        } returns false

        assertFalse(
            inAppValidator.validateInApp(
                inApp = InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy("segment", kind = "positive", "asd", "def", "123")
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = null)
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate form dto variants variant is not null but type is imageUrl is null`() {
        inAppValidator = InAppValidatorImpl(
            sdkVersionValidator = sdkVersionValidator,
            modalWindowValidator = modalWindowValidator,
            snackbarValidator = snackbarValidator
        )
        every {
            modalWindowValidator.isValid(any())
        } returns false
        assertFalse(
            inAppValidator.validateInApp(
                inApp = InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy("segment", kind = "positive", "asd", "def", "123")
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate form dto variants variant is not null`() {
        assertTrue(
            inAppValidator.validateInApp(
                inApp = InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy("segment", kind = "positive", "asd", "def", "123")
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is null`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = null,
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is OperationNode with correct operation`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingOperationNodeDto()
                            .copy(type = "apiMethodCall", systemName = "notEmpty"),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product node with substring correctInfo`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductNodeDto().copy(
                        type = "viewProductId",
                        kind = "substring",
                        value = "notEmpty",
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product node with not substring correctInfo`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductNodeDto().copy(
                        type = "viewProductId",
                        kind = "notSubstring",
                        value = "notEmpty",
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product node with startsWith correctInfo`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductNodeDto().copy(
                        type = "viewProductId",
                        kind = "startsWith",
                        value = "notEmpty",
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product node with endsWith correctInfo`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductNodeDto().copy(
                        type = "viewProductId",
                        kind = "endsWith",
                        value = "notEmpty",
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product node with null kind`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductNodeDto().copy(
                        type = "viewProductId",
                        kind = null,
                        value = "notEmpty",
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product node with incorrect kind`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductNodeDto().copy(
                        type = "viewProductId",
                        kind = "incorrectKind",
                        value = "notEmpty",
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product node with empty kind`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductNodeDto().copy(
                        type = "viewProductId",
                        kind = "",
                        value = "notEmpty",
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product node with empty type`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductNodeDto().copy(
                        type = "",
                        kind = "incorrectKind",
                        value = "notEmpty",
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product node with null type`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductNodeDto().copy(
                        type = null,
                        kind = "incorrectKind",
                        value = "notEmpty",
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }


    @Test
    fun `validate targetingDto is view product node with null value`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductNodeDto().copy(
                        type = "viewProductId",
                        kind = "incorrectKind",
                        value = null,
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product node with empty value`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductNodeDto().copy(
                        type = "viewProductId",
                        kind = "incorrectKind",
                        value = "",
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with correct positive kind`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = "viewProductSegment",
                        kind = "positive",
                        segmentationExternalId = "notEmpty",
                        segmentationInternalId = "notEmpty",
                        segmentExternalId = "notEmpty"
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with correct negative kind`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = "viewProductSegment",
                        kind = "negative",
                        segmentationExternalId = "notEmpty",
                        segmentationInternalId = "notEmpty",
                        segmentExternalId = "notEmpty"
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with empty kind`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = "viewProductSegment",
                        kind = "",
                        segmentationExternalId = "notEmpty",
                        segmentationInternalId = "notEmpty",
                        segmentExternalId = "notEmpty"
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with null kind`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = "viewProductSegment",
                        kind = null,
                        segmentationExternalId = "notEmpty",
                        segmentationInternalId = "notEmpty",
                        segmentExternalId = "notEmpty"
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with incorrect kind`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = "viewProductSegment",
                        kind = "wrong kind",
                        segmentationExternalId = "notEmpty",
                        segmentationInternalId = "notEmpty",
                        segmentExternalId = "notEmpty"
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with null type`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = null,
                        kind = "wrong kind",
                        segmentationExternalId = "notEmpty",
                        segmentationInternalId = "notEmpty",
                        segmentExternalId = "notEmpty"
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with empty type`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = "",
                        kind = "positive",
                        segmentationExternalId = "notEmpty",
                        segmentationInternalId = "notEmpty",
                        segmentExternalId = "notEmpty"
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with empty segmentation external id`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = "viewProductSegment",
                        kind = "positive",
                        segmentationExternalId = "",
                        segmentationInternalId = "notEmpty",
                        segmentExternalId = "notEmpty"
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with empty segmentation internal id`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = "viewProductSegment",
                        kind = "positive",
                        segmentationExternalId = "notEmpty",
                        segmentationInternalId = "",
                        segmentExternalId = "notEmpty"
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with empty segment external id`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = "viewProductSegment",
                        kind = "positive",
                        segmentationExternalId = "notEmpty",
                        segmentationInternalId = "notEmpty",
                        segmentExternalId = ""
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with null segmentation external id`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = "viewProductSegment",
                        kind = "positive",
                        segmentationExternalId = null,
                        segmentationInternalId = "notEmpty",
                        segmentExternalId = "notEmpty"
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with null segmentation internal id`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = "viewProductSegment",
                        kind = "positive",
                        segmentationExternalId = "notEmpty",
                        segmentationInternalId = null,
                        segmentExternalId = "notEmpty"
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targetingDto is view product segment node with null segment external id`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
                        type = "viewProductSegment",
                        kind = "positive",
                        segmentationExternalId = "notEmpty",
                        segmentationInternalId = "notEmpty",
                        segmentExternalId = null
                    ),
                    form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }


    @Test
    fun `validate targetingDto is OperationNode with empty operation`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingOperationNodeDto()
                            .copy(type = "apiMethodCall", systemName = ""),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targetingDto is OperationNode with null operation`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingOperationNodeDto()
                            .copy(type = "apiMethodCall", systemName = null),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is TrueNode with correct type`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingTrueNodeDto().copy(type = "true"),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is TrueNode with incorrect type`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingTrueNodeDto().copy(type = null),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes is empty`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(type = "or", nodes = emptyList()),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes is valid with positive kind`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy("segment", kind = "positive", "asd", "def", "123")
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes is valid with negative kind`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = "segment", kind = "negative",
                                            segmentationExternalId = "asd",
                                            segmentationInternalId = "def",
                                            segmentExternalId = "123"
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes type is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = null, kind = "positive",
                                            segmentationExternalId = "asd",
                                            segmentationInternalId = "def",
                                            segmentExternalId = "123"
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes kind is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = "rrr", kind = "lll",
                                            segmentationExternalId = "asd",
                                            segmentationInternalId = "def",
                                            segmentExternalId = "123"
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes kind is null`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = "rrr", kind = null,
                                            segmentationExternalId = "asd",
                                            segmentationInternalId = "def",
                                            segmentExternalId = "123"
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes segmentation external is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = "rrr", kind = "lll",
                                            segmentationExternalId = null,
                                            segmentationInternalId = "def",
                                            segmentExternalId = "123"
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes segmentation internal is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = "rrr", kind = "lll",
                                            segmentationExternalId = "asd",
                                            segmentationInternalId = null,
                                            segmentExternalId = "123"
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes segment is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingUnionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = "rrr", kind = "lll",
                                            segmentationExternalId = "asd",
                                            segmentationInternalId = "def",
                                            segmentExternalId = null
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes is empty`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingIntersectionNodeDto()
                            .copy(type = "or", nodes = emptyList()),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes is valid with positive kind`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingIntersectionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = "segment", kind = "positive",
                                            segmentationExternalId = "asd",
                                            segmentationInternalId = "def",
                                            segmentExternalId = "123"
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes is valid with negative kind`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingIntersectionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = "segment", kind = "negative",
                                            segmentationExternalId = "asd",
                                            segmentationInternalId = "def",
                                            segmentExternalId = "123"
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes segment is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingIntersectionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = "segment", kind = "positive",
                                            segmentationExternalId = "asd",
                                            segmentationInternalId = "def",
                                            segmentExternalId = null
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes segmentation external is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingIntersectionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = "segment", kind = "positive",
                                            segmentationExternalId = null,
                                            segmentationInternalId = "def",
                                            segmentExternalId = "asds"
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes segmentation internal is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingIntersectionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = "segment", kind = "positive",
                                            segmentationExternalId = "asda",
                                            segmentationInternalId = null,
                                            segmentExternalId = "asds"
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes type is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingIntersectionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = null, kind = "positive",
                                            segmentationExternalId = "asda",
                                            segmentationInternalId = "234",
                                            segmentExternalId = "asds"
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes kind is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingIntersectionNodeDto()
                            .copy(
                                type = "or",
                                nodes = listOf(
                                    InAppStub.getTargetingSegmentNodeDto()
                                        .copy(
                                            type = "asd1", kind = "ads",
                                            segmentationExternalId = "asda",
                                            segmentationInternalId = "234",
                                            segmentExternalId = "asds"
                                        )
                                )
                            ),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is Country node and type is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingCountryNodeDto()
                            .copy(type = null, kind = "positive", ids = listOf("123", "456")),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is Country node and kind not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingCountryNodeDto()
                            .copy(type = "country", kind = null, ids = listOf("123", "456"))),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is Country node and ids not null`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingCountryNodeDto()
                            .copy(type = "country", kind = "positive", ids = null)),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is Country node and ids is empty`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingCountryNodeDto()
                            .copy(type = "country", kind = "positive", ids = emptyList())),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is Country node and its valid with negative kind`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingCountryNodeDto()
                            .copy(type = "country", kind = "negative", ids = listOf("123", "456"))),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is Country node and its valid with positive kind`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingCountryNodeDto()
                            .copy(type = "country", kind = "positive", ids = listOf("123", "456"))),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is city node and type is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingCityNodeDto()
                            .copy(type = null, kind = "positive", ids = listOf("123", "456")),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is city node and kind not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingCityNodeDto()
                            .copy(type = "country", kind = null, ids = listOf("123", "456"))),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is city node and ids not null`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingCityNodeDto()
                            .copy(type = "country", kind = "positive", ids = null)),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is city node and ids is empty`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingCityNodeDto()
                            .copy(type = "country", kind = "positive", ids = emptyList())),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is city node and its valid with negative kind`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingCityNodeDto()
                            .copy(type = "country", kind = "negative", ids = listOf("123", "456"))),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is city node and its valid with positive kind`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingCityNodeDto()
                            .copy(type = "country", kind = "positive", ids = listOf("123", "456"))),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is region node and type is not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = InAppStub.getTargetingRegionNodeDto()
                            .copy(type = null, kind = "positive", ids = listOf("123", "456")),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is region node and kind not valid`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingRegionNodeDto()
                            .copy(type = "country", kind = null, ids = listOf("123", "456"))),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is region node and ids not null`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingRegionNodeDto()
                            .copy(type = "country", kind = "positive", ids = null)),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is region node and ids is empty`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingRegionNodeDto()
                            .copy(type = "country", kind = "positive", ids = emptyList())),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is region node and its valid with negative kind`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingRegionNodeDto()
                            .copy(type = "country", kind = "negative", ids = listOf("123", "456"))),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is region node and its valid with positive kind`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto()
                    .copy(
                        targeting = (InAppStub.getTargetingRegionNodeDto()
                            .copy(type = "country", kind = "positive", ids = listOf("123", "456"))),
                        form = InAppStub.getInAppDto().form?.copy(
                            variants = listOf(
                                InAppStub.getModalWindowDto()
                                    .copy(type = "def")
                            )
                        )
                    )
            )
        )
    }

    @Test
    fun `validate targeting dto is visit node and its valid with gte`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingVisitNodeDto().copy(
                        type = "notBlank", kind = "gte", value = 1L
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is visit node and its valid with lte`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingVisitNodeDto().copy(
                        type = "notBlank", kind = "lte", value = 1L
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is visit node and its valid with equals`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingVisitNodeDto().copy(
                        type = "notBlank", kind = "equals", value = 1L
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is visit node and its valid with not equals`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingVisitNodeDto().copy(
                        type = "notBlank", kind = "notEquals", value = 1L
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is visit node and type is null`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingVisitNodeDto().copy(
                        type = null, kind = "notBlank", value = 1L
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is visit node and type is blank`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingVisitNodeDto().copy(
                        type = "", kind = "notBlank", value = 1L
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is visit node and kind is null`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingVisitNodeDto().copy(
                        type = "notBlank", kind = null, value = 1L
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is visit node and kind is blank`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingVisitNodeDto().copy(
                        type = "notBlank", kind = "", value = 1L
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is visit node and kind is unknown`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingVisitNodeDto().copy(
                        type = "notBlank", kind = "notBlank", value = 1L
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is visit node and value is null`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingVisitNodeDto().copy(
                        type = "notBlank", kind = "notBlank", value = null
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is visit node and value is less than 1`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingVisitNodeDto().copy(
                        type = "notBlank", kind = "notBlank", value = 0
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is pushPermissionNode and value is null`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingPushPermissionNodeDto().copy(
                        type = "notBlank", value = null
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is pushPermissionNode and value is true`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingPushPermissionNodeDto().copy(
                        type = "notBlank", value = true
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is pushPermissionNode and value is false`() {
        assertTrue(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingPushPermissionNodeDto().copy(
                        type = "notBlank", value = false
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `validate targeting dto is pushPermissionNode and type is null`() {
        assertFalse(
            inAppValidator.validateInApp(
                InAppStub.getInAppDto().copy(
                    targeting = InAppStub.getTargetingPushPermissionNodeDto().copy(
                        type = null, value = true
                    ), form = InAppStub.getInAppDto().form?.copy(
                        variants = listOf(
                            InAppStub.getModalWindowDto()
                                .copy(type = "def")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `in-app version is lower than required`() {
        val lowInAppVersion = Constants.SDK_VERSION_NUMERIC - 1
        assertFalse(
            inAppValidator.validateInAppVersion(
                InAppStub.getInAppDtoBlank().copy(
                    sdkVersion = InAppStub.getSdkVersion()
                        .copy(minVersion = null, maxVersion = lowInAppVersion)
                )
            )
        )
    }

    @Test
    fun `in-app version is higher than required`() {
        val highInAppVersion = Constants.SDK_VERSION_NUMERIC + 1
        assertFalse(
            inAppValidator.validateInAppVersion(
                InAppStub.getInAppDtoBlank().copy(
                    sdkVersion = InAppStub.getSdkVersion()
                        .copy(minVersion = highInAppVersion, maxVersion = null)
                )
            )
        )
    }

    @Test
    fun `in-app version is out of range`() {
        val lowInAppVersion = Constants.SDK_VERSION_NUMERIC - 1
        val highInAppVersion = Constants.SDK_VERSION_NUMERIC + 1
        assertFalse(
            inAppValidator.validateInAppVersion(
                InAppStub.getInAppDtoBlank().copy(
                    sdkVersion = InAppStub.getSdkVersion()
                        .copy(minVersion = highInAppVersion, maxVersion = lowInAppVersion)
                )
            )
        )
    }

    @Test
    fun `in-app version no min version`() {
        val highInAppVersion = Constants.SDK_VERSION_NUMERIC + 1
        assertTrue(
            inAppValidator.validateInAppVersion(
                InAppStub.getInAppDtoBlank().copy(
                    sdkVersion = InAppStub.getSdkVersion()
                        .copy(minVersion = null, maxVersion = highInAppVersion)
                )
            )
        )
    }

    @Test
    fun `in-app version no max version`() {
        val lowInAppVersion = Constants.SDK_VERSION_NUMERIC - 1
        assertTrue(
            inAppValidator.validateInAppVersion(
                InAppStub.getInAppDtoBlank().copy(
                    sdkVersion = InAppStub.getSdkVersion()
                        .copy(minVersion = lowInAppVersion, maxVersion = null)
                )
            )
        )
    }

    @Test
    fun `in-app version no limitations`() {
        assertTrue(
            inAppValidator.validateInAppVersion(
                InAppStub.getInAppDtoBlank().copy(
                    sdkVersion = InAppStub.getSdkVersion()
                        .copy(minVersion = null, maxVersion = null)
                )
            )
        )
    }

    @Test
    fun `in-app version is in range`() {
        val lowInAppVersion = Constants.SDK_VERSION_NUMERIC - 1
        val highInAppVersion = Constants.SDK_VERSION_NUMERIC + 1
        assertTrue(
            inAppValidator.validateInAppVersion(
                InAppStub.getInAppDtoBlank().copy(
                    sdkVersion = InAppStub.getSdkVersion()
                        .copy(minVersion = lowInAppVersion, maxVersion = highInAppVersion)
                )
            )
        )
    }
}