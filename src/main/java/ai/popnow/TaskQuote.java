package ai.popnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A signed price for one exact request, valid for five minutes.
 *
 * <p>Quoting reserves nothing and creates nothing. Carrying the quote into
 * {@link PopNowClient#createTask(CreateTaskRequest, String)} with
 * {@link CreateTaskRequest#withQuote(TaskQuote)} makes a price that moved in the meantime a
 * rejection with business code 40901, raised before anything is held, rather than a silent charge
 * at the new price.
 *
 * <p>Amounts are exact USD decimals from the API balance, never binary floating point.
 * {@link #maxChargeUsd()} is the amount held on acceptance and the one to confirm;
 * {@link #expectedChargeUsd()} is the typical charge and only informational.
 *
 * @param quoteId           opaque signed token; pass it through unchanged and do not log it
 * @param model             the endpoint the quote resolved to
 * @param estimatedCost     legacy USD alias of {@code maxChargeUsd}
 * @param maxCharge         legacy USD alias of {@code maxChargeUsd}
 * @param currency          always {@code USD}
 * @param quantity          estimated billable quantity, expressed in {@code unit}
 * @param unit              what {@code quantity} counts
 * @param expiresAt         when the quote stops being accepted
 * @param maxCredits        deprecated display projection of {@code maxChargeUsd} in legacy credits;
 *                          use {@link #maxChargeUsd()}
 * @param expectedCredits   deprecated display projection of {@code expectedChargeUsd} in legacy
 *                          credits; use {@link #expectedChargeUsd()}
 * @param maxChargeUsd      the exact maximum charge in USD, which is the amount held on acceptance
 *                          and the value {@link CreateTaskRequest#withQuote(TaskQuote)} confirms as
 *                          {@code expectedCostUsd}; the final charge never exceeds it
 * @param expectedChargeUsd the exact typical charge in USD, never above {@code maxChargeUsd};
 *                          informational, not the amount to confirm
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskQuote(String quoteId, String model, BigDecimal estimatedCost, BigDecimal maxCharge,
                        String currency, String quantity, String unit, Instant expiresAt,
                        long maxCredits, long expectedCredits,
                        BigDecimal maxChargeUsd, BigDecimal expectedChargeUsd) {

    /**
     * The 0.1 shape, without the USD fields added in 0.2.
     *
     * @param quoteId         opaque signed token
     * @param model           the endpoint the quote resolved to
     * @param estimatedCost   legacy USD alias of the maximum charge
     * @param maxCharge       legacy USD alias of the maximum charge
     * @param currency        always {@code USD}
     * @param quantity        estimated billable quantity
     * @param unit            what {@code quantity} counts
     * @param expiresAt       when the quote stops being accepted
     * @param maxCredits      the maximum charge in legacy credits
     * @param expectedCredits the typical charge in legacy credits
     * @deprecated use the canonical constructor, which also takes {@code maxChargeUsd} and
     *             {@code expectedChargeUsd}
     */
    @Deprecated
    public TaskQuote(String quoteId, String model, BigDecimal estimatedCost, BigDecimal maxCharge,
                     String currency, String quantity, String unit, Instant expiresAt,
                     long maxCredits, long expectedCredits) {
        this(quoteId, model, estimatedCost, maxCharge, currency, quantity, unit, expiresAt,
                maxCredits, expectedCredits, null, null);
    }

    /**
     * The maximum charge in legacy credits, rounded up.
     *
     * @return the deprecated credit projection of {@link #maxChargeUsd()}
     * @deprecated credits are a display projection, not a billing unit; use {@link #maxChargeUsd()}
     */
    @Deprecated
    public long maxCredits() {
        return maxCredits;
    }

    /**
     * The typical charge in legacy credits, rounded up.
     *
     * @return the deprecated credit projection of {@link #expectedChargeUsd()}
     * @deprecated credits are a display projection, not a billing unit; use
     *             {@link #expectedChargeUsd()}
     */
    @Deprecated
    public long expectedCredits() {
        return expectedCredits;
    }

    /**
     * Whether this quote can still be submitted.
     *
     * @param now the reference instant, normally from the same clock as the rest of the caller
     * @return true when the quote is no longer accepted
     */
    public boolean expired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    // The amount withQuote confirms. maxChargeUsd is the recommended field; maxCharge carries the
    // same value and only stands in for it on a quote built without the USD fields.
    BigDecimal confirmableUsd() {
        return maxChargeUsd != null ? maxChargeUsd : maxCharge;
    }
}
