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
    fun `validate targeting dto is TrueNode with correct type`() {
        assertTrue(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingTrueNodeDto().copy(type = "true"),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is TrueNode with incorrect type`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingTrueNodeDto().copy(type = null),
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
                            segmentExternalId = "123"))),
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
                            segmentExternalId = "123"))),
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
                            segmentExternalId = "123"))),
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
                            segmentExternalId = "123"))),
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
                            segmentExternalId = "123"))),
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
                            segmentExternalId = "123"))),
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
                            segmentExternalId = null))),
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
                            segmentExternalId = "123"))),
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
                            segmentExternalId = "123"))),
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
                            segmentExternalId = null))),
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
                            segmentExternalId = "asds"))),
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
                            segmentExternalId = "asds"))),
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
                            segmentExternalId = "asds"))),
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
                            segmentExternalId = "asds"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is Country node and type is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingCountryNodeDto()
                .copy(type = null, kind = "positive", ids = listOf("123", "456")),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is Country node and kind not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingCountryNodeDto()
                .copy(type = "country", kind = null, ids = listOf("123", "456"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is Country node and ids not null`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingCountryNodeDto()
                .copy(type = "country", kind = "positive", ids = null)),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is Country node and ids is empty`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingCountryNodeDto()
                .copy(type = "country", kind = "positive", ids = emptyList())),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is Country node and its valid with negative kind`() {
        assertTrue(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingCountryNodeDto()
                .copy(type = "country", kind = "negative", ids = listOf("123", "456"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is Country node and its valid with positive kind`() {
        assertTrue(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingCountryNodeDto()
                .copy(type = "country", kind = "positive", ids = listOf("123", "456"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is city node and type is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingCityNodeDto()
                .copy(type = null, kind = "positive", ids = listOf("123", "456")),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is city node and kind not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingCityNodeDto()
                .copy(type = "country", kind = null, ids = listOf("123", "456"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is city node and ids not null`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingCityNodeDto()
                .copy(type = "country", kind = "positive", ids = null)),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is city node and ids is empty`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingCityNodeDto()
                .copy(type = "country", kind = "positive", ids = emptyList())),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is city node and its valid with negative kind`() {
        assertTrue(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingCityNodeDto()
                .copy(type = "country", kind = "negative", ids = listOf("123", "456"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is city node and its valid with positive kind`() {
        assertTrue(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingCityNodeDto()
                .copy(type = "country", kind = "positive", ids = listOf("123", "456"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is region node and type is not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = InAppStub.getTargetingRegionNodeDto()
                .copy(type = null, kind = "positive", ids = listOf("123", "456")),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is region node and kind not valid`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingRegionNodeDto()
                .copy(type = "country", kind = null, ids = listOf("123", "456"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is region node and ids not null`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingRegionNodeDto()
                .copy(type = "country", kind = "positive", ids = null)),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is region node and ids is empty`() {
        assertFalse(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingRegionNodeDto()
                .copy(type = "country", kind = "positive", ids = emptyList())),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is region node and its valid with negative kind`() {
        assertTrue(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingRegionNodeDto()
                .copy(type = "country", kind = "negative", ids = listOf("123", "456"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }

    @Test
    fun `validate targeting dto is region node and its valid with positive kind`() {
        assertTrue(inAppValidator.validateInApp(InAppStub.getInAppDto()
            .copy(targeting = (InAppStub.getTargetingRegionNodeDto()
                .copy(type = "country", kind = "positive", ids = listOf("123", "456"))),
                form = InAppStub.getInAppDto().form?.copy(variants = listOf(InAppStub.getSimpleImageDto()
                    .copy(type = "def", imageUrl = "abc"))))))
    }
}