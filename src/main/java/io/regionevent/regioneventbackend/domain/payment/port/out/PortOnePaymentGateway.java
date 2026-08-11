package io.regionevent.regioneventbackend.domain.payment.port.out;

public interface PortOnePaymentGateway {

    PortOnePayment findByPaymentId(String paymentId);

    record PortOnePayment(
        String paymentId,
        String transactionId,
        String storeId,
        long amount,
        String currency,
        String status
    ) {

        public PortOnePayment(
            String paymentId,
            String transactionId,
            long amount,
            String currency,
            String status
        ) {
            this(paymentId, transactionId, null, amount, currency, status);
        }

        public boolean isPaid() {
            return "PAID".equals(status);
        }

        public boolean isExplicitlyDeclined() {
            return "DECLINED".equals(status);
        }
    }
}
