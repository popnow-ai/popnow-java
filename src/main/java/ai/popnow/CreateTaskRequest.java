package ai.popnow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * A task submission.
 *
 * <p>{@code input} is a map rather than a typed object because it genuinely is model-specific:
 * the service validates it against the selected model's {@link ApiModel#inputSchema()}, and no
 * field set is shared across models. Values may be any type Jackson can serialize.
 *
 * <p>Media fields accept a public HTTPS URL, a committed {@code popnow://f/fil_...} URI from
 * {@link PopNowClient#uploadBytes}, or a Base64 data URI for small images.
 *
 * <p>To confirm a price, bind a quote with {@link #withQuote(TaskQuote)}: it sends the quote token
 * and {@code expectedCostUsd}, the quote's exact {@link TaskQuote#maxChargeUsd()}.
 *
 * @param model           an identifier from the catalogue
 * @param input           payload for that model's schema
 * @param callBackUrl     optional public HTTPS endpoint for terminal delivery; verify what arrives
 *                        there with {@link CallbackSignatureVerifier}
 * @param quoteId         optional quote token from {@link PopNowClient#quote}
 * @param expectedCost    deprecated legacy name of {@code expectedCostUsd}; when both are set they
 *                        must be equal
 * @param expectedCredits deprecated confirmation in legacy display credits; it never overrides a
 *                        USD amount. Use {@code expectedCostUsd}
 * @param expectedCostUsd optional exact USD hold to confirm, sent as {@code expectedCostUsd}: the
 *                        quote's {@link TaskQuote#maxChargeUsd()}, at most nine decimal places
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateTaskRequest(String model, Map<String, Object> input, String callBackUrl, String quoteId,
                                @JsonSerialize(using = UsdAmountSerializer.class) BigDecimal expectedCost,
                                Long expectedCredits,
                                @JsonSerialize(using = UsdAmountSerializer.class) BigDecimal expectedCostUsd) {

    // input is defensively copied here: mutating that map after submission must not change what
    // has already gone out.
    public CreateTaskRequest {
        model = Internal.requireText(model, "model");
        input = Internal.unmodifiableCopy(Objects.requireNonNull(input, "input"));
    }

    /**
     * The 0.1 shape, without {@code expectedCostUsd}.
     *
     * @param model           an identifier from the catalogue
     * @param input           payload for that model's schema
     * @param callBackUrl     optional public HTTPS endpoint for terminal delivery
     * @param quoteId         optional quote token
     * @param expectedCost    optional legacy USD confirmation
     * @param expectedCredits optional legacy credit confirmation
     * @deprecated use the canonical constructor and confirm with {@code expectedCostUsd}, or
     *             {@link #withQuote(TaskQuote)}
     */
    @Deprecated
    public CreateTaskRequest(String model, Map<String, Object> input, String callBackUrl, String quoteId,
                             BigDecimal expectedCost, Long expectedCredits) {
        this(model, input, callBackUrl, quoteId, expectedCost, expectedCredits, null);
    }

    /**
     * A submission with no callback and no quote.
     *
     * @param model an identifier from the catalogue
     * @param input payload for that model's schema
     * @return the request
     * @throws IllegalArgumentException when {@code model} is blank
     * @throws NullPointerException     when {@code input} is null
     */
    public static CreateTaskRequest of(String model, Map<String, Object> input) {
        return new CreateTaskRequest(model, input, null, null, null, null, null);
    }

    /**
     * The legacy name of {@link #expectedCostUsd()}.
     *
     * @return the legacy USD confirmation, or {@code null} when it is not set
     * @deprecated use {@link #expectedCostUsd()}
     */
    @Deprecated
    @JsonSerialize(using = UsdAmountSerializer.class)
    public BigDecimal expectedCost() {
        return expectedCost;
    }

    /**
     * The confirmation in legacy credits.
     *
     * @return the legacy credit confirmation, or {@code null} when it is not set
     * @deprecated credits are a display projection, not a billing unit; use
     *             {@link #expectedCostUsd()}
     */
    @Deprecated
    public Long expectedCredits() {
        return expectedCredits;
    }

    /**
     * Adds a terminal-delivery callback.
     *
     * @param url a public HTTPS endpoint; {@code http://} is refused by the service because the
     *            delivery carries the prompt and signed result links
     * @return a new request
     */
    public CreateTaskRequest withCallBackUrl(String url) {
        return new CreateTaskRequest(model, input, url, quoteId, expectedCost, expectedCredits, expectedCostUsd);
    }

    /**
     * Binds this submission to a quote, so that a price which moved is refused rather than
     * charged.
     *
     * <p>Sends the quote token and {@code expectedCostUsd} set to the quote's exact
     * {@link TaskQuote#maxChargeUsd()}, which is exactly the amount the submission will hold.
     * Any earlier confirmation on this request, including a legacy credit one, is replaced.
     *
     * @param quote a quote obtained for this same request
     * @return a new request carrying the quote token and confirmed USD amount
     * @throws NullPointerException when {@code quote} is null
     */
    public CreateTaskRequest withQuote(TaskQuote quote) {
        Objects.requireNonNull(quote, "quote");
        return new CreateTaskRequest(model, input, callBackUrl, quote.quoteId(), null, null, quote.confirmableUsd());
    }
}
