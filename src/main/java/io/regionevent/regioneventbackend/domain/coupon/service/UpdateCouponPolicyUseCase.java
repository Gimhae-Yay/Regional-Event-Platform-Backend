package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.coupon.dto.UpdateCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyUpdateHistory;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyUpdateSnapshot;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService.UpdateCouponPolicyCommand;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class UpdateCouponPolicyUseCase {

    private static final int MAXIMUM_NAME_LENGTH = 255;
    private static final int MAXIMUM_DESCRIPTION_LENGTH = 1_000;

    private final AppUserService appUserService;
    private final OperatorAuthorizationService operatorAuthorizationService;
    private final CouponPolicyService couponPolicyService;
    private final CouponPolicyUpdateHistoryService couponPolicyUpdateHistoryService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public UpdateCouponPolicyUseCase(
        AppUserService appUserService,
        OperatorAuthorizationService operatorAuthorizationService,
        CouponPolicyService couponPolicyService,
        CouponPolicyUpdateHistoryService couponPolicyUpdateHistoryService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.appUserService = appUserService;
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.couponPolicyService = couponPolicyService;
        this.couponPolicyUpdateHistoryService = couponPolicyUpdateHistoryService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public UpdateCouponPolicyResult update(
        Long userId,
        Long couponPolicyId,
        UpdateCouponPolicyRequest request,
        UUID requestId
    ) {
        ParsedRequest parsedRequest = parseRequest(request);
        appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperatorForUpdate(userId);
        CouponPolicy couponPolicy = couponPolicyService.findForUpdate(couponPolicyId);
        validateOwnership(requestId, operator, couponPolicy);

        if (couponPolicy.getStatus() != CouponPolicyStatus.DRAFT) {
            recordFailure(requestId, operator, couponPolicy, ErrorCode.COUPON_POLICY_CONFLICT);
            throw new BusinessException(ErrorCode.COUPON_POLICY_CONFLICT);
        }

        Instant updatedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UpdateCouponPolicyCommand command = parsedRequest.toCommand(couponPolicy);
        validateCommand(command);
        if (isSamePolicy(couponPolicy, command)) {
            return UpdateCouponPolicyResult.from(couponPolicy, couponPolicy.getUpdatedAt());
        }

        CouponPolicyUpdateSnapshot previous = CouponPolicyUpdateSnapshot.from(couponPolicy);
        CouponPolicy updatedCouponPolicy = couponPolicyService.update(
            couponPolicy,
            command,
            updatedAt
        );
        AuditEventActor actor = new AuditEventActor(operator.roleAssignment());
        AuditEvent auditEvent = recordAuditEventUseCase.record(
            new AuditEventCommand(
                requestId,
                updatedCouponPolicy.getRegion(),
                AuditEventTargetType.COUPON_POLICY,
                updatedCouponPolicy.getCouponPolicyId(),
                CouponPolicyStatus.DRAFT.name(),
                CouponPolicyStatus.DRAFT.name(),
                AuditEventResult.SUCCESS,
                null,
                parsedRequest.reason(),
                null,
                actor,
                updatedAt
            )
        );
        couponPolicyUpdateHistoryService.create(new CouponPolicyUpdateHistory(
            updatedCouponPolicy,
            auditEvent,
            actor.getRoleName(),
            parsedRequest.reason(),
            requestId.toString(),
            updatedAt,
            previous,
            CouponPolicyUpdateSnapshot.from(updatedCouponPolicy)
        ));
        return UpdateCouponPolicyResult.from(updatedCouponPolicy, updatedAt);
    }

    private boolean isSamePolicy(
        CouponPolicy couponPolicy,
        UpdateCouponPolicyCommand command
    ) {
        return couponPolicy.getName().equals(command.name())
            && Objects.equals(couponPolicy.getDescription(), command.description())
            && couponPolicy.getDiscountAmount() == command.discountAmount()
            && couponPolicy.getMinimumPaymentAmount() == command.minimumPaymentAmount()
            && couponPolicy.getValidDays() == command.validDaysAfterIssue()
            && couponPolicy.getIssueStartsAt().equals(command.issueStartsAt())
            && couponPolicy.getIssueEndsAt().equals(command.issueEndsAt())
            && Objects.equals(couponPolicy.getTotalIssueLimit(), command.totalIssueLimit());
    }

    private void validateCommand(UpdateCouponPolicyCommand command) {
        if (command.minimumPaymentAmount() < command.discountAmount()
            || !command.issueStartsAt().isBefore(command.issueEndsAt())) {
            throw invalidInput();
        }
    }

    private ParsedRequest parseRequest(UpdateCouponPolicyRequest request) {
        if (request == null) {
            throw invalidInput();
        }

        PatchValue<String> name = parseName(request.name());
        PatchValue<String> description = parseDescription(request.description());
        PatchValue<Long> discountAmount = parseLong(request.discountAmount(), 1L);
        PatchValue<Long> minimumPaymentAmount = parseLong(request.minimumPaymentAmount(), 0L);
        PatchValue<Integer> validDaysAfterIssue = parseValidDays(request.validDaysAfterIssue());
        PatchValue<Instant> issueStartsAt = parseInstant(request.issueStartsAt());
        PatchValue<Instant> issueEndsAt = parseInstant(request.issueEndsAt());
        PatchValue<Long> totalIssueLimit = parseTotalIssueLimit(request.totalIssueLimit());
        String reason = parseReason(request.reason());
        if (!name.present() && !description.present() && !discountAmount.present()
            && !minimumPaymentAmount.present() && !validDaysAfterIssue.present()
            && !issueStartsAt.present() && !issueEndsAt.present() && !totalIssueLimit.present()) {
            throw invalidInput();
        }
        return new ParsedRequest(
            name,
            description,
            discountAmount,
            minimumPaymentAmount,
            validDaysAfterIssue,
            issueStartsAt,
            issueEndsAt,
            totalIssueLimit,
            reason
        );
    }

    private String parseReason(JsonNode value) {
        if (value == null || value.isNull() || !value.isString()) {
            throw invalidInput();
        }
        String reason = value.stringValue().strip();
        if (reason.isEmpty() || reason.length() > 500) {
            throw invalidInput();
        }
        return reason;
    }

    private PatchValue<String> parseName(JsonNode value) {
        if (value == null) {
            return PatchValue.absent();
        }
        if (value.isNull()) {
            throw invalidInput();
        }
        if (!value.isString()) {
            throw invalidInput();
        }
        String normalizedValue = value.stringValue().strip();
        if (normalizedValue.isEmpty() || normalizedValue.length() > MAXIMUM_NAME_LENGTH) {
            throw invalidInput();
        }
        return PatchValue.of(normalizedValue);
    }

    private PatchValue<String> parseDescription(JsonNode value) {
        if (value == null) {
            return PatchValue.absent();
        }
        if (value.isNull()) {
            return PatchValue.of(null);
        }
        if (!value.isString()) {
            throw invalidInput();
        }
        String description = value.stringValue();
        if (description.length() > MAXIMUM_DESCRIPTION_LENGTH) {
            throw invalidInput();
        }
        return PatchValue.of(description.isEmpty() ? null : description);
    }

    private PatchValue<Long> parseLong(
        JsonNode value,
        long minimumValue
    ) {
        if (value == null) {
            return PatchValue.absent();
        }
        if (value.isNull()) {
            throw invalidInput();
        }
        if (!value.isIntegralNumber()) {
            throw invalidInput();
        }
        if (!value.canConvertToLong()) {
            throw invalidInput();
        }
        long parsedValue = value.longValue();
        if (parsedValue < minimumValue) {
            throw invalidInput();
        }
        return PatchValue.of(parsedValue);
    }

    private PatchValue<Integer> parseValidDays(JsonNode value) {
        PatchValue<Long> parsedValue = parseLong(value, 1L);
        if (!parsedValue.present()) {
            return PatchValue.absent();
        }
        if (parsedValue.value() > 365) {
            throw invalidInput();
        }
        return PatchValue.of(parsedValue.value().intValue());
    }

    private PatchValue<Instant> parseInstant(JsonNode value) {
        if (value == null) {
            return PatchValue.absent();
        }
        if (value.isNull()) {
            throw invalidInput();
        }
        if (!value.isString()) {
            throw invalidInput();
        }
        try {
            return PatchValue.of(Instant.parse(value.stringValue()));
        } catch (DateTimeParseException exception) {
            throw invalidInput();
        }
    }

    private PatchValue<Long> parseTotalIssueLimit(JsonNode value) {
        if (value == null) {
            return PatchValue.absent();
        }
        if (value.isNull()) {
            return PatchValue.of(null);
        }
        return parseLong(value, 1L);
    }

    private void validateOwnership(
        UUID requestId,
        AuthorizedOperator operator,
        CouponPolicy couponPolicy
    ) {
        Content content = couponPolicy.getContent();
        if (!content.isOwnedBy(operator.user().getUserId())
            || !content.isScopedTo(operator.region().getRegionId())) {
            recordFailure(requestId, operator, couponPolicy, ErrorCode.FORBIDDEN);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void recordFailure(
        UUID requestId,
        AuthorizedOperator operator,
        CouponPolicy couponPolicy,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            couponPolicy.getRegion(),
            AuditEventTargetType.COUPON_POLICY,
            couponPolicy.getCouponPolicyId(),
            couponPolicy.getStatus().name(),
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            new AuditEventActor(operator.roleAssignment()),
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
    }

    private static BusinessException invalidInput() {
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }

    private record ParsedRequest(
        PatchValue<String> name,
        PatchValue<String> description,
        PatchValue<Long> discountAmount,
        PatchValue<Long> minimumPaymentAmount,
        PatchValue<Integer> validDaysAfterIssue,
        PatchValue<Instant> issueStartsAt,
        PatchValue<Instant> issueEndsAt,
        PatchValue<Long> totalIssueLimit,
        String reason
    ) {

        private UpdateCouponPolicyCommand toCommand(CouponPolicy couponPolicy) {
            return new UpdateCouponPolicyCommand(
                name.orElse(couponPolicy.getName()),
                description.orElse(couponPolicy.getDescription()),
                discountAmount.orElse(couponPolicy.getDiscountAmount()),
                minimumPaymentAmount.orElse(couponPolicy.getMinimumPaymentAmount()),
                validDaysAfterIssue.orElse(couponPolicy.getValidDays()),
                issueStartsAt.orElse(couponPolicy.getIssueStartsAt()),
                issueEndsAt.orElse(couponPolicy.getIssueEndsAt()),
                totalIssueLimit.orElse(couponPolicy.getTotalIssueLimit())
            );
        }
    }

    private record PatchValue<T>(
        boolean present,
        T value
    ) {

        private static <T> PatchValue<T> absent() {
            return new PatchValue<>(false, null);
        }

        private static <T> PatchValue<T> of(T value) {
            return new PatchValue<>(true, value);
        }

        private T orElse(T defaultValue) {
            return present ? value : defaultValue;
        }
    }
}
