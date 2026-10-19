package ai.popnow;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

/**
 * A verified account endpoint event (Standard Webhooks format).
 *
 * @param webhookId  the {@code webhook-id} header, stable across retries: store it and ignore
 *                   repeats
 * @param timestamp  the {@code webhook-timestamp} header, in Unix seconds
 * @param type       the event type: {@code task.completed}, {@code task.succeeded},
 *                   {@code task.failed}, {@code task.expired} or {@code task.canceled};
 *                   {@code null} when the body carried none
 * @param taskRecord {@code data.object}, the task record at the moment the event was created;
 *                   {@code null} when absent
 * @param body       the parsed body, for any field this release does not map
 */
public record EndpointEvent(String webhookId, long timestamp, String type, TaskRecord taskRecord, JsonNode body) {

    /**
     * The task the event is about.
     *
     * @return the task record, or empty when the event carried none
     */
    public Optional<TaskRecord> task() {
        return Optional.ofNullable(taskRecord);
    }
}
