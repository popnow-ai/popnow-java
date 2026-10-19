package ai.popnow;

/** No usable answer was obtained: DNS, TLS, a dropped connection, an interrupted thread. */
public sealed class PopNowTransportException extends PopNowException
        permits PopNowTimeoutException {

    private static final long serialVersionUID = 1L;

    /** @param message what failed */
    public PopNowTransportException(String message) {
        super(message);
    }

    /**
     * @param message what failed
     * @param cause   the underlying I/O or interruption failure
     */
    public PopNowTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
