package ai.popnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * A deprecated credit-scale rate snapshot of a model, kept so code written against 0.1 still
 * compiles and parses. It may lose precision.
 *
 * <p>The API bills in exact USD: read {@link ApiModel#usd()} for the rates, and
 * {@link PopNowClient#quote} for the exact amount of one request.
 *
 * @param unit    what {@code perUnit} is charged per, for example {@code per_image},
 *                {@code per_second} or {@code per_request}. Left as a string because the platform
 *                may add units, and an unrecognised unit should still display
 * @param perUnit the base price per unit, in legacy credits; may be fractional
 * @param rules   input-dependent prices that replace {@code perUnit} when their {@code when}
 *                matches the request; empty when the price does not vary
 * @deprecated credits are a display projection, not a billing unit; use {@link ModelUsd} from
 *             {@link ApiModel#usd()}
 */
@Deprecated
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelCredits(String unit, BigDecimal perUnit, List<ModelCreditRule> rules) {

    public ModelCredits {
        rules = Internal.emptyIfNull(rules);
    }
}
