package io.regionevent.regioneventbackend.domain.payment.repository;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentWebhook;

public interface PaymentWebhookRepository extends JpaRepository<PaymentWebhook, Long> {

    Optional<PaymentWebhook> findByProviderEventId(String providerEventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT paymentWebhook FROM PaymentWebhook paymentWebhook "
        + "WHERE paymentWebhook.providerEventId = :providerEventId")
    Optional<PaymentWebhook> findByProviderEventIdForUpdate(@Param("providerEventId") String providerEventId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        INSERT INTO payment_webhook (
            provider_event_id,
            payment_id,
            authentication_result,
            processing_result,
            payload_hash,
            received_at
        ) VALUES (
            :providerEventId,
            :paymentId,
            :authenticationResult,
            :processingResult,
            :payloadHash,
            :receivedAt
        )
        ON DUPLICATE KEY UPDATE provider_event_id = provider_event_id
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("providerEventId") String providerEventId,
        @Param("paymentId") Long paymentId,
        @Param("authenticationResult") String authenticationResult,
        @Param("processingResult") String processingResult,
        @Param("payloadHash") String payloadHash,
        @Param("receivedAt") Instant receivedAt
    );
}
