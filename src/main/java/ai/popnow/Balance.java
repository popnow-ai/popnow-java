package ai.popnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * What this account's API balance can spend right now, in exact USD.
 *
 * <p>Free to read, and scoped to the account rather than to the API key: every key on the account
 * draws on the same API balance. It is separate from the website's credits; neither can pay for the
 * other. Add API funds at <a href="https://popnow.ai/api/console/billing">API billing</a>.
 *
 * <p><b>A positive {@link #availableUsd()} does not mean the next task will be admitted.</b> Part of
 * the money can be a promotional grant restricted to particular models, and part can be approved
 * credit rather than cash. Admission checks the funds that can actually pay for <em>that</em>
 * request, which is why a balance reading is a budget indicator and never a guarantee — the
 * authoritative answer for one request is {@link PopNowClient#quote}, which runs the same admission
 * rules without reserving anything.
 *
 * @param available        legacy USD alias of {@code availableUsd}
 * @param held             legacy USD alias of {@code heldUsd}
 * @param total            legacy USD alias of {@code totalUsd}
 * @param funding          where the money comes from, broken down by source; {@code null} when the
 *                         service did not include the breakdown
 * @param availableCredits deprecated display projection of {@code availableUsd} in legacy credits;
 *                         use {@link #availableUsd()}
 * @param heldCredits      deprecated display projection of {@code heldUsd}; use {@link #heldUsd()}
 * @param totalCredits     deprecated display projection of {@code totalUsd}; use {@link #totalUsd()}
 * @param availableUsd     exact USD a new task can draw on. Can be negative, when approved credit
 *                         has been drawn on or an external payment was recovered
 * @param heldUsd          exact USD reserved by accepted tasks that have not settled yet. Already
 *                         spoken for, and not available to a new task
 * @param totalUsd         {@code availableUsd} plus {@code heldUsd}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Balance(BigDecimal available, BigDecimal held, BigDecimal total, FundingOverview funding,
                      long availableCredits, long heldCredits, long totalCredits,
                      BigDecimal availableUsd, BigDecimal heldUsd, BigDecimal totalUsd) {

    /**
     * The 0.1 shape, without the USD fields added in 0.2.
     *
     * @param available        legacy USD available
     * @param held             legacy USD held
     * @param total            legacy USD total
     * @param funding          the funding breakdown
     * @param availableCredits available in legacy credits
     * @param heldCredits      held in legacy credits
     * @param totalCredits     total in legacy credits
     * @deprecated use the canonical constructor, which also takes the {@code *Usd} amounts
     */
    @Deprecated
    public Balance(BigDecimal available, BigDecimal held, BigDecimal total, FundingOverview funding,
                   long availableCredits, long heldCredits, long totalCredits) {
        this(available, held, total, funding, availableCredits, heldCredits, totalCredits, null, null, null);
    }

    /**
     * The available balance in legacy credits, truncated toward zero.
     *
     * @return the deprecated credit projection of {@link #availableUsd()}
     * @deprecated credits are a display projection, not a billing unit; use {@link #availableUsd()}
     */
    @Deprecated
    public long availableCredits() {
        return availableCredits;
    }

    /**
     * The held amount in legacy credits.
     *
     * @return the deprecated credit projection of {@link #heldUsd()}
     * @deprecated credits are a display projection, not a billing unit; use {@link #heldUsd()}
     */
    @Deprecated
    public long heldCredits() {
        return heldCredits;
    }

    /**
     * The sum of the legacy available and held credits; not an exact balance.
     *
     * @return the deprecated credit projection of {@link #totalUsd()}
     * @deprecated credits are a display projection, not a billing unit; use {@link #totalUsd()}
     */
    @Deprecated
    public long totalCredits() {
        return totalCredits;
    }

    /**
     * The funding breakdown, when the service sent one.
     *
     * @return the breakdown by source, or empty when it was not included
     */
    public Optional<FundingOverview> fundingSources() {
        return Optional.ofNullable(funding);
    }
}
