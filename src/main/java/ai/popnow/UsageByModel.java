package ai.popnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * One model's share of a {@link UsageReport}.
 *
 * <p>Models with no activity in the period are absent rather than present with zeros.
 *
 * @param model     the public model identifier
 * @param calls     tasks created against it, whatever became of them
 * @param succeeded how many of them produced a result
 * @param failed    how many of them ended without one
 * @param spend     settled charges for those tasks; unsettled holds are not counted
 * @param credits   deprecated display projection of {@code spend} in legacy credits; use
 *                  {@link #spend()}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsageByModel(String model, long calls, long succeeded, long failed, BigDecimal spend, long credits) {

    /**
     * Settled spend in legacy credits, rounded up.
     *
     * @return the deprecated credit projection of {@link #spend()}
     * @deprecated credits are a display projection, not a billing unit; use {@link #spend()}, which
     *             is in USD
     */
    @Deprecated
    public long credits() {
        return credits;
    }
}
