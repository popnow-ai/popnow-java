package ai.popnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A signed price for one exact request, valid for five minutes.
 *
 * <p>Quoting reserves nothing and creates nothing. Carrying the quote into
 * {@link PopNowClient#createTask(CreateTaskRequest, String)} makes a price that moved in the
 * meantime a rejection with business code 40901, raised before any credits are held, rather than
 * a silent charge at the new price.
 *
 * <p>1 credit is $0.01. Every amount is returned twice: as whole credits and as the equivalent USD
 * decimal.
 *
 * @param quoteId         opaque signed token; pass it through unchanged and do not log it
 * @param model           the endpoint the quote resolved to
 * @param estimatedCost   what this request is expected to cost, in USD
 * @param maxCharge       the ceiling in USD; the final charge never exceeds it
 * @param currency        always {@code USD}
 * @param quantity        estimated billable quantity, expressed in {@code unit}
 * @param unit            what {@code quantity} counts
 * @param expiresAt       when the quote stops being accepted
 * @param maxCredits      the ceiling in credits, and the amount held on submission
 * @param expectedCredits what this request is expected to cost, in credits
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskQuote(String quoteId, String model, BigDecimal estimatedCost, BigDecimal maxCharge,
                        String currency, String quantity, String unit, Instant expiresAt,
                        long maxCredits, long expectedCredits) {

    /**
     * Whether this quote can still be submitted.
     *
     * @param now the reference instant, normally from the same clock as the rest of the caller
     * @return true when the quote is no longer accepted
     */
    public boolean expired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
