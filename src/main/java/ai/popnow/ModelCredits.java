package ai.popnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * What a model costs, in credits. 1 credit is $0.01.
 *
 * <p>The exact amount for one request depends on its input (resolution, duration, count...), so
 * the authoritative number for a request is always {@link PopNowClient#quote}; this is for display
 * and for choosing between models.
 *
 * @param unit    what {@code perUnit} is charged per, for example {@code per_image},
 *                {@code per_second} or {@code per_request}. Left as a string because the platform
 *                may add units, and an unrecognised unit should still display
 * @param perUnit the base price per unit, in credits; may be fractional
 * @param rules   input-dependent prices that replace {@code perUnit} when their {@code when}
 *                matches the request; empty when the price does not vary
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelCredits(String unit, BigDecimal perUnit, List<ModelCreditRule> rules) {

    public ModelCredits {
        rules = Internal.emptyIfNull(rules);
    }
}
