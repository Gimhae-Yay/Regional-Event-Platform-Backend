package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetOperatorStampbooksUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetOperatorStampbooksUseCase.class);

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final StampbookReadService stampbookReadService;

    public GetOperatorStampbooksUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        StampbookReadService stampbookReadService
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.stampbookReadService = stampbookReadService;
    }

    @Transactional(readOnly = true)
    public List<OperatorStampbookListResult> findAll(Long userId) {
        try {
            AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
            List<OperatorStampbookListResult> results = stampbookReadService.findOperatorStampbooks(
                operator.user().getUserId(),
                operator.region().getRegionId()
            );
            log.info(
                "Operator stampbook list read succeeded. requestId={}, operatorUserId={}, errorCode=SUCCESS",
                RequestIdFilter.currentRequestId(),
                operator.user().getUserId()
            );
            return results;
        } catch (BusinessException exception) {
            log.warn(
                "Operator stampbook list read failed. requestId={}, operatorUserId={}, errorCode={}",
                RequestIdFilter.currentRequestId(),
                userId,
                exception.getErrorCode().code()
            );
            throw exception;
        } catch (RuntimeException exception) {
            log.warn(
                "Operator stampbook list read failed. requestId={}, operatorUserId={}, errorCode={}",
                RequestIdFilter.currentRequestId(),
                userId,
                ErrorCode.INTERNAL_SERVER_ERROR.code()
            );
            throw exception;
        }
    }
}
