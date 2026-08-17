package io.regionevent.regioneventbackend.domain.payment.port.out;

public class PortOneResponseException extends RuntimeException {

    private final String externalStatus;
    private final String resultHash;

    public PortOneResponseException(
        String externalStatus,
        String resultHash,
        Throwable cause
    ) {
        super(cause);
        this.externalStatus = externalStatus;
        this.resultHash = resultHash;
    }

    public String getExternalStatus() {
        return externalStatus;
    }

    public String getResultHash() {
        return resultHash;
    }
}
