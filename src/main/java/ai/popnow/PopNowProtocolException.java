package ai.popnow;

/**
 * A response arrived but did not match the contract: not JSON, not the
 * {@code {code,msg,data,request_id}} envelope, or a {@code data} payload that could not be
 * mapped.
 *
 * <p>Distinct from {@link PopNowApiException} on purpose. That one means the service said no;
 * this one means something in between spoke for it, which in practice is a proxy, a captive
 * portal or a misconfigured base URL.
 */
public final class PopNowProtocolException extends PopNowException {

    private static final long serialVersionUID = 1L;

    /**
     * @serial the status of the response that could not be understood, or 0
     */
    private final int httpStatus;

    /**
     * @param message    what was expected and what arrived
     * @param httpStatus status of the offending response, or 0 when unknown
     * @param cause      the parse failure, when there was one
     */
    public PopNowProtocolException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /**
     * Status of the response that could not be understood, or 0.
     *
     * @return the HTTP status of the response that could not be understood, or 0
     */
    public int httpStatus() {
        return httpStatus;
    }
}
