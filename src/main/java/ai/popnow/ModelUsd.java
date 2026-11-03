package ai.popnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * What a model costs on the API, in exact USD.
 *
 * <p>A rate snapshot for display and for choosing between models. The exact amount for one request
 * depends on its input (resolution, duration, count...), so the authoritative number for a request
 * is always {@link PopNowClient#quote}, whose {@link TaskQuote#maxChargeUsd()} is what will be
 * held. Amounts arrive as decimal strings and are kept as {@link BigDecimal}, never as binary
 * floating point.
 *
 * @param unit        what {@code perUnit} is charged per, for example {@code per_image},
 *                    {@code per_second} or {@code per_request}. Left as a string because the
 *                    platform may add units, and an unrecognised unit should still display
 * @param perUnit     the base price per unit in USD; {@code null} for chat models, which are priced
 *                    per token instead
 * @param blocks      how a duration is rounded into billing blocks, when it is
 * @param input       chat models only: USD per 1M input tokens
 * @param output      chat models only: USD per 1M output tokens (reasoning tokens count as output)
 * @param cachedInput chat models only: USD per 1M cached input tokens
 * @param cacheWrite  chat models only: USD per 1M tokens written to the prompt cache
 * @param longContext chat models only: the prices for requests whose input passes a token threshold
 * @param rules       input-dependent prices that replace {@code perUnit} when their {@code when}
 *                    matches the request; empty when the price does not vary
 * @param currency    always {@code USD}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelUsd(String unit, BigDecimal perUnit, Blocks blocks, BigDecimal input, BigDecimal output,
                       BigDecimal cachedInput, BigDecimal cacheWrite, LongContext longContext,
                       List<Rule> rules, String currency, JsonNode perMillionTokens) {

    public ModelUsd(String unit, BigDecimal perUnit, Blocks blocks, BigDecimal input, BigDecimal output,
                    BigDecimal cachedInput, BigDecimal cacheWrite, LongContext longContext, List<Rule> rules, String currency) {
        this(unit, perUnit, blocks, input, output, cachedInput, cacheWrite, longContext, rules, currency, null);
    }

    public ModelUsd {
        rules = Internal.emptyIfNull(rules);
    }

    /**
     * The billing-block rounding, when the price has one.
     *
     * @return the block rule, or empty when durations are not rounded into blocks
     */
    public Optional<Blocks> billingBlocks() {
        return Optional.ofNullable(blocks);
    }

    /**
     * Duration rounding: a duration is rounded up to whole blocks of {@code seconds}, subject to the
     * stated minimum and cap. The model's page says whether the duration is the input's or the
     * output's.
     *
     * @param seconds the length of one billing block
     * @param min     the minimum number of blocks charged, when there is one
     * @param cap     the maximum number of blocks charged, when there is one
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Blocks(int seconds, Integer min, Integer cap) {
    }

    /**
     * Chat prices for requests whose input exceeds {@code thresholdTokens}, or reaches it when
     * {@code inclusive} is true.
     *
     * @param thresholdTokens the input size at which these prices start
     * @param inclusive       whether a request of exactly {@code thresholdTokens} already counts
     * @param input           USD per 1M input tokens
     * @param cachedInput     USD per 1M cached input tokens
     * @param cacheWrite      USD per 1M tokens written to the prompt cache
     * @param output          USD per 1M output tokens
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LongContext(Long thresholdTokens, Boolean inclusive, BigDecimal input, BigDecimal cachedInput,
                              BigDecimal cacheWrite, BigDecimal output) {
    }

    /**
     * One input-dependent price.
     *
     * @param when    the input values this price applies to, for example
     *                {@code {"resolution":"1080p"}}; kept as a {@link JsonNode} because its fields
     *                are model-specific, and {@code "*"} matches the field that sets the tier
     * @param perUnit the price per unit in USD when {@code when} matches
     * @param unit    the unit, when it differs from {@link ModelUsd#unit()}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rule(JsonNode when, BigDecimal perUnit, String unit) {

        /**
         * The unit of this rule, when it overrides the model's.
         *
         * @return the rule's own unit, or empty when it uses {@link ModelUsd#unit()}
         */
        public Optional<String> ownUnit() {
            return unit == null || unit.isEmpty() ? Optional.empty() : Optional.of(unit);
        }
    }
}
