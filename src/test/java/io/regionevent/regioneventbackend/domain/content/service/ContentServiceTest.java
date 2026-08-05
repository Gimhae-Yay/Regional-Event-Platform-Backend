package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ContentServiceTest {

    private static final Long CONTENT_ID = 200L;
    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Instant UPDATED_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final BigDecimal DATABASE_EPOCH_SECONDS = new BigDecimal("1754356800.123456");

    private final ContentRepository contentRepository = mock(ContentRepository.class);
    private final ContentService contentService = new ContentService(contentRepository);

    @Test
    void findCurrentDatabaseTime_MySQL_현재_시각을_Instant로_변환한다() {
        when(contentRepository.findCurrentEpochSeconds()).thenReturn(DATABASE_EPOCH_SECONDS);

        Instant result = contentService.findCurrentDatabaseTime();

        assertThat(result).isEqualTo(Instant.ofEpochSecond(1_754_356_800L, 123_456_000));
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void findOwnedContentForRevisionCreation_필수_식별자가_없거나_양수가_아니면_입력_오류를_반환한다(
        Long contentId,
        Long userId,
        Long regionId
    ) {
        assertThatThrownBy(() -> contentService.findOwnedContentForRevisionCreation(contentId, userId, regionId))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
        verifyNoInteractions(contentRepository);
    }

    @Test
    void findOwnedContentForRevisionCreation_대상이_없으면_찾을수없음을_반환한다() {
        when(contentRepository.findByContentIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contentService.findOwnedContentForRevisionCreation(CONTENT_ID, USER_ID, REGION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
    }

    @Test
    void findOwnedContentForRevisionCreation_소유자가_다르면_권한_오류를_반환한다() {
        Content content = ownedContent(false, true, ContentStatus.PENDING);
        when(contentRepository.findByContentIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.of(content));

        assertThatThrownBy(() -> contentService.findOwnedContentForRevisionCreation(CONTENT_ID, USER_ID, REGION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }

    @Test
    void findOwnedContentForRevisionCreation_지역이_다르면_권한_오류를_반환한다() {
        Content content = ownedContent(true, false, ContentStatus.PENDING);
        when(contentRepository.findByContentIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.of(content));

        assertThatThrownBy(() -> contentService.findOwnedContentForRevisionCreation(CONTENT_ID, USER_ID, REGION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }

    @Test
    void findOwnedContentForRevisionCreation_소유자와_지역이_일치하면_대상을_반환한다() {
        Content content = ownedContent(true, true, ContentStatus.PENDING);
        when(contentRepository.findByContentIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.of(content));

        assertThat(contentService.findOwnedContentForRevisionCreation(CONTENT_ID, USER_ID, REGION_ID))
            .isSameAs(content);
    }

    @Test
    void findRejectedOwnedContentForUpdate_반려_상태가_아니면_상태_충돌을_반환한다() {
        Content content = ownedContent(true, true, ContentStatus.APPROVED);
        when(contentRepository.findByContentIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.of(content));

        assertThatThrownBy(() -> contentService.findRejectedOwnedContentForUpdate(CONTENT_ID, USER_ID, REGION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );
    }

    @Test
    void findRejectedOwnedContentForUpdate_반려_콘텐츠를_반환한다() {
        Content content = ownedContent(true, true, ContentStatus.REJECTED);
        when(contentRepository.findByContentIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.of(content));

        assertThat(contentService.findRejectedOwnedContentForUpdate(CONTENT_ID, USER_ID, REGION_ID))
            .isSameAs(content);
    }

    @Test
    void markPrePublicationRevisionPending_승인_상태가_아니면_상태_충돌을_반환한다() {
        Content content = mock(Content.class);
        when(content.getStatus()).thenReturn(ContentStatus.PENDING);

        assertThatThrownBy(() -> contentService.markPrePublicationRevisionPending(content))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );
    }

    @Test
    void markPrePublicationRevisionPending_승인_콘텐츠를_대기_상태로_저장한다() {
        Content content = mock(Content.class);
        when(content.getStatus()).thenReturn(ContentStatus.APPROVED);
        when(contentRepository.saveAndFlush(content)).thenReturn(content);

        assertThat(contentService.markPrePublicationRevisionPending(content)).isSameAs(content);
        verify(content).requestPrePublicationRevision();
    }

    @Test
    void updateRejectedContent_반려_상태가_아니면_상태_충돌을_반환한다() {
        Content content = mock(Content.class);
        when(content.getStatus()).thenReturn(ContentStatus.APPROVED);

        assertThatThrownBy(() -> contentService.updateRejectedContent(content, command(), null, UPDATED_AT))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );
    }

    @Test
    void updateRejectedContent_교체_이미지가_있으면_수정_필드와_이미지를_저장한다() {
        Content content = mock(Content.class);
        ImageObject replacementImageObject = mock(ImageObject.class);
        when(content.getStatus()).thenReturn(ContentStatus.REJECTED);
        when(contentRepository.saveAndFlush(content)).thenReturn(content);

        assertThat(contentService.updateRejectedContent(
            content,
            command(),
            replacementImageObject,
            UPDATED_AT
        )).isSameAs(content);

        verify(content).replaceEditableFields(
            "수정 제목",
            "수정 설명",
            "김해",
            "10:00-18:00",
            "055-0000-0000",
            "주의사항",
            "전체",
            "준비물",
            "취소 정책",
            Instant.parse("2026-08-10T01:00:00Z")
        );
        verify(content).assignRepresentativeImage(replacementImageObject, UPDATED_AT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "image", "titleNull", "titleBlank", "description", "location", "hours", "contact", "precautions",
        "age", "materials", "policy", "publishAt"
    })
    void validateSubmitRequirements_필수값이_하나라도_없으면_입력_오류를_반환한다(String missingField) {
        Content content = submittableContent();
        makeMissing(content, missingField);

        assertThatThrownBy(() -> contentService.validateSubmitRequirements(content))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
    }

    @Test
    void validateSubmitRequirements_모든_필수값이_있으면_통과한다() {
        contentService.validateSubmitRequirements(submittableContent());
    }

    @ParameterizedTest
    @MethodSource("updateCounts")
    void 상태_전이_갱신_결과가_한건이_아니면_충돌을_반환한다(
        String operation,
        int updatedCount,
        ErrorCode expectedError
    ) {
        Content content = mock(Content.class);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        switch (operation) {
            case "reject" -> when(contentRepository.rejectPendingByContentId(CONTENT_ID, UPDATED_AT))
                .thenReturn(updatedCount);
            case "submit" -> when(contentRepository.submitRejectedByContentId(CONTENT_ID, UPDATED_AT))
                .thenReturn(updatedCount);
            case "end" -> when(contentRepository.endPublishedByContentId(CONTENT_ID, UPDATED_AT))
                .thenReturn(updatedCount);
            case "suspend" -> when(contentRepository.suspendPublishedByContentId(CONTENT_ID, UPDATED_AT))
                .thenReturn(updatedCount);
            default -> throw new IllegalArgumentException(operation);
        }

        assertThatThrownBy(() -> invokeStateTransition(operation, content))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(expectedError)
            );
    }

    @Test
    void 상태_전이_갱신_결과가_한건이면_엔티티_상태를_전이한다() {
        Content content = mock(Content.class);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(contentRepository.rejectPendingByContentId(CONTENT_ID, UPDATED_AT)).thenReturn(1);
        when(contentRepository.submitRejectedByContentId(CONTENT_ID, UPDATED_AT)).thenReturn(1);
        when(contentRepository.endPublishedByContentId(CONTENT_ID, UPDATED_AT)).thenReturn(1);
        when(contentRepository.suspendPublishedByContentId(CONTENT_ID, UPDATED_AT)).thenReturn(1);

        contentService.reject(content, UPDATED_AT);
        contentService.submitForReview(content, UPDATED_AT);
        contentService.end(content, UPDATED_AT);
        contentService.suspend(content, UPDATED_AT);

        verify(content).reject();
        verify(content).submitForReview();
        verify(content).end();
        verify(content).suspend();
    }

    @Test
    void softDelete_허용되지_않은_상태면_삭제_충돌을_반환한다() {
        Content content = mock(Content.class);
        when(content.getStatus()).thenReturn(ContentStatus.REJECTED);

        assertThatThrownBy(() -> contentService.softDelete(content, UPDATED_AT))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_DELETE_CONFLICT)
            );
    }

    @Test
    void softDelete_이미_삭제됐으면_삭제_충돌을_반환한다() {
        Content content = mock(Content.class);
        when(content.getStatus()).thenReturn(ContentStatus.PENDING);
        when(content.getDeletedAt()).thenReturn(UPDATED_AT);

        assertThatThrownBy(() -> contentService.softDelete(content, UPDATED_AT))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_DELETE_CONFLICT)
            );
    }

    @Test
    void softDelete_대표_이미지가_없으면_불변식_위반을_반환한다() {
        Content content = mock(Content.class);
        when(content.getStatus()).thenReturn(ContentStatus.APPROVED);

        assertThatThrownBy(() -> contentService.softDelete(content, UPDATED_AT))
            .isInstanceOf(IllegalStateException.class);
        verify(content).softDelete(UPDATED_AT);
    }

    @Test
    void softDelete_대기_콘텐츠의_대표_이미지를_분리하고_저장한다() {
        Content content = mock(Content.class);
        ImageObject imageObject = mock(ImageObject.class);
        when(content.getStatus()).thenReturn(ContentStatus.PENDING);
        when(content.detachRepresentativeImage()).thenReturn(imageObject);

        assertThat(contentService.softDelete(content, UPDATED_AT)).isSameAs(imageObject);
        verify(content).softDelete(UPDATED_AT);
        verify(contentRepository).saveAndFlush(content);
    }

    private static Stream<Arguments> invalidIds() {
        return Stream.of(
            Arguments.of(null, USER_ID, REGION_ID),
            Arguments.of(0L, USER_ID, REGION_ID),
            Arguments.of(CONTENT_ID, null, REGION_ID),
            Arguments.of(CONTENT_ID, 0L, REGION_ID),
            Arguments.of(CONTENT_ID, USER_ID, null),
            Arguments.of(CONTENT_ID, USER_ID, 0L)
        );
    }

    private static Stream<Arguments> updateCounts() {
        return Stream.of(
            Arguments.of("reject", 0, ErrorCode.CONTENT_STATE_CONFLICT),
            Arguments.of("submit", 2, ErrorCode.CONTENT_STATE_CONFLICT),
            Arguments.of("end", 0, ErrorCode.CONTENT_END_CONFLICT),
            Arguments.of("suspend", 2, ErrorCode.CONTENT_SUSPEND_CONFLICT)
        );
    }

    private void invokeStateTransition(String operation, Content content) {
        switch (operation) {
            case "reject" -> contentService.reject(content, UPDATED_AT);
            case "submit" -> contentService.submitForReview(content, UPDATED_AT);
            case "end" -> contentService.end(content, UPDATED_AT);
            case "suspend" -> contentService.suspend(content, UPDATED_AT);
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private static Content ownedContent(boolean owned, boolean scoped, ContentStatus status) {
        Content content = mock(Content.class);
        when(content.isOwnedBy(USER_ID)).thenReturn(owned);
        when(content.isScopedTo(REGION_ID)).thenReturn(scoped);
        when(content.getStatus()).thenReturn(status);
        return content;
    }

    private static Content submittableContent() {
        Content content = mock(Content.class);
        when(content.getRepresentativeImageObject()).thenReturn(mock(ImageObject.class));
        when(content.getTitle()).thenReturn("제목");
        when(content.getDescription()).thenReturn("설명");
        when(content.getLocationText()).thenReturn("김해");
        when(content.getOperatingHoursText()).thenReturn("10:00-18:00");
        when(content.getContactText()).thenReturn("055-0000-0000");
        when(content.getPrecautions()).thenReturn("주의사항");
        when(content.getAgeRequirement()).thenReturn("전체");
        when(content.getMaterials()).thenReturn("준비물");
        when(content.getCancellationPolicyText()).thenReturn("취소 정책");
        when(content.getPublishAt()).thenReturn(Instant.parse("2026-08-10T01:00:00Z"));
        return content;
    }

    private static void makeMissing(Content content, String field) {
        switch (field) {
            case "image" -> when(content.getRepresentativeImageObject()).thenReturn(null);
            case "titleNull" -> when(content.getTitle()).thenReturn(null);
            case "titleBlank" -> when(content.getTitle()).thenReturn(" ");
            case "description" -> when(content.getDescription()).thenReturn(" ");
            case "location" -> when(content.getLocationText()).thenReturn(" ");
            case "hours" -> when(content.getOperatingHoursText()).thenReturn(" ");
            case "contact" -> when(content.getContactText()).thenReturn(" ");
            case "precautions" -> when(content.getPrecautions()).thenReturn(" ");
            case "age" -> when(content.getAgeRequirement()).thenReturn(" ");
            case "materials" -> when(content.getMaterials()).thenReturn(" ");
            case "policy" -> when(content.getCancellationPolicyText()).thenReturn(" ");
            case "publishAt" -> when(content.getPublishAt()).thenReturn(null);
            default -> throw new IllegalArgumentException(field);
        }
    }

    private static ContentService.UpdateContentCommand command() {
        return new ContentService.UpdateContentCommand(
            "수정 제목",
            "수정 설명",
            "김해",
            "10:00-18:00",
            "055-0000-0000",
            "주의사항",
            "전체",
            "준비물",
            "취소 정책",
            Instant.parse("2026-08-10T01:00:00Z")
        );
    }
}
