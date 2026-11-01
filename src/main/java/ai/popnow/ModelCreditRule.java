package ai.popnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * One input-dependent price of a model, in deprecated legacy credits.
 *
 * @param when    the input values this price applies to, for example {@code {"resolution":"1080p"}};
 *                kept as a {@link JsonNode} because its fields are model-specific
 * @param perUnit the price per unit, in legacy credits, when {@code when} matches
 * @param unit    the unit, when it differs from {@link ModelCredits#unit()}
 * @deprecated credits are a display projection, not a billing unit; use {@link ModelUsd.Rule}
 */
@Deprecated
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelCreditRule(JsonNode when, BigDecimal perUnit, String unit) {

    /**
     * The unit of this rule, when it overrides the model's.
     *
     * @return the rule's own unit, or empty when it uses {@link ModelCredits#unit()}
     */
    public Optional<String> ownUnit() {
        return unit == null || unit.isEmpty() ? Optional.empty() : Optional.of(unit);
    }
}
