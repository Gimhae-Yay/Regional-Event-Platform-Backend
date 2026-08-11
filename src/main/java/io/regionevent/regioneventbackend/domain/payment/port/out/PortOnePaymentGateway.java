package io.regionevent.regioneventbackend.domain.payment.port.out;

public interface PortOnePaymentGateway {

    PortOnePayment findByPaymentId(String paymentId);

    PortOneCancellation cancelPayment(String paymentId, long amount, String reason);

    record PortOneCancellation(
        String cancellationId,
        String status,
        String resultHash
    ) {
        public boolean isSucceeded() {
            return "SUCCEEDED".equals(status);
        }
    }

    record PortOnePayment(
        String paymentId,
        String transactionId,
        String storeId,
        long amount,
        String currency,
        String status,
        String resultHash
    ) {

        public PortOnePayment(
            String paymentId,
            String transactionId,
            String storeId,
            long amount,
            String currency,
            String status
        ) {
            this(paymentId, transactionId, storeId, amount, currency, status, null);
        }

        public PortOnePayment(
            String paymentId,
            String transactionId,
            long amount,
            String currency,
            String status
        ) {
            this(paymentId, transactionId, null, amount, currency, status, null);
        }

        public boolean isPaid() {
            return "PAID".equals(status);
        }

        public boolean isExplicitlyDeclined() {
            return "DECLINED".equals(status);
        }
    }
}
