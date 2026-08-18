package io.regionevent.regioneventbackend.domain.content.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@WebMvcTest({
    ContentController.class,
    ContentDeletionController.class,
    ContentHistoryController.class,
    ContentApprovalController.class,
    ContentRejectionController.class,
    ContentRevisionApprovalController.class,
    ContentRevisionController.class,
    ContentRevisionReviewController.class,
    ContentSessionApprovalController.class,
    ContentSessionController.class,
    ContentSessionRejectionController.class,
    ContentWithdrawalRequestController.class,
    ContentWithdrawalApprovalController.class,
    ContentWithdrawalRejectionController.class,
    EndContentReservationsController.class,
    MyContentDetailController.class,
    OperatorContentSessionController.class,
    OriginalContentReviewDetailController.class,
    PendingContentController.class,
    PendingContentWithdrawalRequestController.class,
    PendingSessionReviewController.class,
    PublicContentController.class,
    PublicContentDetailController.class,
    PublicContentSessionController.class,
    SessionRevisionRejectionController.class,
    SessionRevisionCreationController.class,
    SessionRevisionReviewController.class,
    SessionRevisionReviewDetailController.class,
    SubmitContentController.class,
    UpdateContentRevisionController.class,
    UpdateMyContentController.class,
    WithdrawContentRevisionController.class
})
@interface ContentControllerWebMvcTest {
}
