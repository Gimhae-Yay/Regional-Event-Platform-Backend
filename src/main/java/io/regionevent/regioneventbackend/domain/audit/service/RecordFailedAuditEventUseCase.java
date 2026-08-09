package io.regionevent.regioneventbackend.domain.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;

@Service
public class RecordFailedAuditEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordFailedAuditEventUseCase.class);

    private final AuditEventService auditEventService;
    private final AuditEventActorLinkService auditEventActorLinkService;
    private final TransactionTemplate failedAuditEventTransactionTemplate;

    public RecordFailedAuditEventUseCase(
        AuditEventService auditEventService,
        AuditEventActorLinkService auditEventActorLinkService,
        PlatformTransactionManager transactionManager
    ) {
        this.auditEventService = auditEventService;
        this.auditEventActorLinkService = auditEventActorLinkService;
        this.failedAuditEventTransactionTemplate = new TransactionTemplate(transactionManager);
        this.failedAuditEventTransactionTemplate.setPropagationBehavior(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEventCommand command) {
        if (command.result() != AuditEventResult.FAILURE) {
            throw new IllegalArgumentException("failed audit event must have FAILURE result");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    recordAfterRollback(command);
                }
            }
        });
    }

    private void recordAfterRollback(AuditEventCommand command) {
        try {
            failedAuditEventTransactionTemplate.executeWithoutResult(transactionStatus -> {
                AuditEvent auditEvent = auditEventService.record(command);
                auditEventActorLinkService.record(auditEvent, command.actor());
            });
        } catch (RuntimeException exception) {
            log.error(
                "독립 감사 기록에 실패했습니다. requestId={}, targetType={}, targetId={}, originalErrorCode={}, auditWriteResult={}",
                command.requestId(),
                command.targetType(),
                command.targetId(),
                command.reasonCode(),
                "FAILURE",
                exception
            );
        }
    }
}
