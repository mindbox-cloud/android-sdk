package cloud.mindbox.mobile_sdk.inapp.data

import cloud.mindbox.mobile_sdk.models.InAppStub
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppValidatorImplTest {

    private val inAppValidator = InAppValidatorImpl()


    @Test
    fun `validate form dto variants null`() {
        assertFalse(inAppValidator.validateInApp(inApp = InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy("segment", kind = "positive", "asd", "def", "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = null))))
    }

    @Test
    fun `validate form dto variants empty list`() {
        assertFalse(inAppValidator.validateInApp(inApp = InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy("segment", kind = "positive", "asd", "def", "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = emptyList()))))
    }

    @Test
    fun `validate form dto variants variant is null`() {
        assertFalse(inAppValidator.validateInApp(inApp = InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy("segment", kind = "positive", "asd", "def", "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(null)))))
    }

    @Test
    fun `validate form dto variants variant is not null but type is null`() {
        assertFalse(inAppValidator.validateInApp(inApp = InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy("segment", kind = "positive", "asd", "def", "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = null))))))
    }

    @Test
    fun `validate form dto variants variant is not null but type is imageUrl is null`() {
        assertFalse(inAppValidator.validateInApp(inApp = InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy("segment", kind = "positive", "asd", "def", "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "", imageUrl = null))))))
    }

    @Test
    fun `validate form dto variants variant is not null`() {
        assertTrue(inAppValidator.validateInApp(inApp = InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy("segment", kind = "positive", "asd", "def", "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is null`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto().copy(targeting = null,
            form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is TrueNode`() {
        assertTrue(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingTrueNodeDto(),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes is empty`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or", nodes = emptyList()),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes is valid with positive kind`() {
        assertTrue(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy("segment", kind = "positive", "asd", "def", "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes is valid with negative kind`() {
        assertTrue(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = "segment", kind = "negative",
                            segmentationExternalId = "asd",
                            segmentationInternalId = "def",
                            segment_external_id = "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes type is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = null, kind = "positive",
                            segmentationExternalId = "asd",
                            segmentationInternalId = "def",
                            segment_external_id = "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes kind is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = "rrr", kind = "lll",
                            segmentationExternalId = "asd",
                            segmentationInternalId = "def",
                            segment_external_id = "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes kind is null`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = "rrr", kind = null,
                            segmentationExternalId = "asd",
                            segmentationInternalId = "def",
                            segment_external_id = "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes segmentation external is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = "rrr", kind = "lll",
                            segmentationExternalId = null,
                            segmentationInternalId = "def",
                            segment_external_id = "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes segmentation internal is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = "rrr", kind = "lll",
                            segmentationExternalId = "asd",
                            segmentationInternalId = null,
                            segment_external_id = "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is UnionNode and nodes segment is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingUnionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = "rrr", kind = "lll",
                            segmentationExternalId = "asd",
                            segmentationInternalId = "def",
                            segment_external_id = null))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes is empty`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingIntersectionNodeDto()
                .copy(type = "or", nodes = emptyList()),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes is valid with positive kind`() {
        assertTrue(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingIntersectionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = "segment", kind = "positive",
                            segmentationExternalId = "asd",
                            segmentationInternalId = "def",
                            segment_external_id = "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes is valid with negative kind`() {
        assertTrue(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingIntersectionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = "segment", kind = "negative",
                            segmentationExternalId = "asd",
                            segmentationInternalId = "def",
                            segment_external_id = "123"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes segment is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingIntersectionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = "segment", kind = "positive",
                            segmentationExternalId = "asd",
                            segmentationInternalId = "def",
                            segment_external_id = null))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes segmentation external is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingIntersectionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = "segment", kind = "positive",
                            segmentationExternalId = null,
                            segmentationInternalId = "def",
                            segment_external_id = "asds"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes segmentation internal is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingIntersectionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = "segment", kind = "positive",
                            segmentationExternalId = "asda",
                            segmentationInternalId = null,
                            segment_external_id = "asds"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes type is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingIntersectionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = null, kind = "positive",
                            segmentationExternalId = "asda",
                            segmentationInternalId = "234",
                            segment_external_id = "asds"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is IntersectionNode and nodes kind is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingIntersectionNodeDto()
                .copy(type = "or",
                    nodes = listOf(InAppStub.getTargetingSegmentNodeDto()
                        .copy(type = "asd1", kind = "ads",
                            segmentationExternalId = "asda",
                            segmentationInternalId = "234",
                            segment_external_id = "asds"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }
}