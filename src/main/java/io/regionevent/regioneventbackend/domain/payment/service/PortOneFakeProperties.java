package io.regionevent.regioneventbackend.domain.payment.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portone.fake")
public class PortOneFakeProperties {

    private boolean enabled;
    private long paymentAmount = 10_000L;
    private String currency = "KRW";
    private String storeId = "perf-store";
    private String transactionIdPrefix = "perf-transaction-";
    private String cancellationIdPrefix = "perf-cancellation-";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(long paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getTransactionIdPrefix() {
        return transactionIdPrefix;
    }

    public void setTransactionIdPrefix(String transactionIdPrefix) {
        this.transactionIdPrefix = transactionIdPrefix;
    }

    public String getCancellationIdPrefix() {
        return cancellationIdPrefix;
    }

    public void setCancellationIdPrefix(String cancellationIdPrefix) {
        this.cancellationIdPrefix = cancellationIdPrefix;
    }
}
