package ai.popnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One callable model endpoint, with its API price in USD.
 *
 * @param model                    the exact identifier {@link CreateTaskRequest} expects
 * @param family                   stable product family; endpoint variants share it
 * @param displayName              the model's human-readable name
 * @param provider                 the model's creator, such as the lab that built it
 * @param modality                 {@code image}, {@code video} or {@code audio}
 * @param tasks                    internal task classification, always exactly one element, and
 *                                 not always the suffix of {@code model}: image editing is
 *                                 published as {@code .../edit} but classified
 *                                 {@code image-to-image}. Call with {@code model}; use this only
 *                                 to group or filter
 * @param async                    whether the endpoint is executed as an asynchronous task
 * @param toolkit                  the toolkit this endpoint belongs to, such as
 *                                 {@code studio-tools}, when it belongs to one
 * @param taskTimeoutSeconds       platform execution deadline, unrelated to media duration
 * @param maxOutputDurationSeconds longest output the schema permits, when it declares one
 * @param enabled                  whether the model is switched on for this account
 * @param available                whether it can currently run
 * @param quantityField            the input field that sets the billable quantity, when one does
 * @param credits                  deprecated credit-scale rate snapshot; it may lose precision.
 *                                 Use {@code usd}
 * @param inputSchema              JSON Schema 2020-12 for {@link CreateTaskRequest#input()}.
 *                                 Kept as a {@link JsonNode} because it is a schema document,
 *                                 different for every model, and is meant to be walked or handed
 *                                 to a validator rather than mapped to fixed fields
 * @param version                  the endpoint's own version identifier
 * @param availability             {@code planned}, {@code available}, {@code preview} or
 *                                 {@code maintenance}
 * @param badges                   capability and licensing vocabulary; ignore unknown entries
 * @param relatedModels            curated alternatives or workflow neighbours
 * @param examples                 validated example inputs, when they were requested
 * @param updatedAt                when the catalogue entry last changed
 * @param usd                      the API price as an exact USD rate snapshot; use
 *                                 {@link PopNowClient#quote} for the amount of one request
 */
// ModelCredits stays in the record for 0.1 compatibility; using that deprecated type here is
// deliberate, so its warnings are suppressed for this declaration only.
@SuppressWarnings("deprecation")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiModel(String model, String family, String displayName, String provider, String modality,
                       List<String> tasks, boolean async, String toolkit,
                       int taskTimeoutSeconds, Integer maxOutputDurationSeconds, boolean enabled,
                       boolean available, String quantityField, ModelCredits credits,
                       JsonNode inputSchema, String version, String availability,
                       List<String> badges, List<String> relatedModels, List<ModelExample> examples,
                       Instant updatedAt, ModelUsd usd) {

    // Arrays the server omitted are normalised to empty lists here, so callers need not null-check
    // every collection.
    public ApiModel {
        tasks = Internal.emptyIfNull(tasks);
        badges = Internal.emptyIfNull(badges);
        relatedModels = Internal.emptyIfNull(relatedModels);
        examples = Internal.emptyIfNull(examples);
    }

    /**
     * The 0.1 shape, without {@code usd}.
     *
     * @param model                    the call identifier
     * @param family                   the product family
     * @param displayName              the human-readable name
     * @param provider                 the model's creator
     * @param modality                 image, video or audio
     * @param tasks                    the task classification
     * @param async                    whether it runs as an asynchronous task
     * @param toolkit                  the toolkit, when there is one
     * @param taskTimeoutSeconds       the execution deadline
     * @param maxOutputDurationSeconds the longest output, when declared
     * @param enabled                  whether it is switched on
     * @param available                whether it can run now
     * @param quantityField            the field that sets the billable quantity
     * @param credits                  the legacy credit price
     * @param inputSchema              the input JSON Schema
     * @param version                  the endpoint version
     * @param availability             the availability label
     * @param badges                   capability badges
     * @param relatedModels            related model identifiers
     * @param examples                 example inputs
     * @param updatedAt                when the entry last changed
     * @deprecated use the canonical constructor, which also takes {@code usd}
     */
    @Deprecated
    public ApiModel(String model, String family, String displayName, String provider, String modality,
                    List<String> tasks, boolean async, String toolkit,
                    int taskTimeoutSeconds, Integer maxOutputDurationSeconds, boolean enabled,
                    boolean available, String quantityField, ModelCredits credits,
                    JsonNode inputSchema, String version, String availability,
                    List<String> badges, List<String> relatedModels, List<ModelExample> examples,
                    Instant updatedAt) {
        this(model, family, displayName, provider, modality, tasks, async, toolkit, taskTimeoutSeconds,
                maxOutputDurationSeconds, enabled, available, quantityField, credits, inputSchema, version,
                availability, badges, relatedModels, examples, updatedAt, null);
    }

    /**
     * The deprecated credit-scale rate snapshot.
     *
     * @return the legacy credit price, or {@code null} when the service did not send one
     * @deprecated credits are a display projection, not a billing unit; use {@link #usd()}
     */
    @Deprecated
    public ModelCredits credits() {
        return credits;
    }

    /**
     * The API price in USD, when the service sent one.
     *
     * @return the USD rate snapshot, or empty when it is absent
     */
    public Optional<ModelUsd> usdPrice() {
        return Optional.ofNullable(usd);
    }

    /**
     * Whether a task submitted to this model right now would be accepted.
     *
     * @return true when a task submitted right now would be accepted
     */
    public boolean callable() {
        return enabled && available;
    }

    /**
     * The single task classification, or empty for a model that declares none.
     *
     * @return the value of {@code tasks[0]}, or empty when the model declares no task
     */
    public Optional<String> task() {
        return tasks.isEmpty() ? Optional.empty() : Optional.of(tasks.get(0));
    }
}
