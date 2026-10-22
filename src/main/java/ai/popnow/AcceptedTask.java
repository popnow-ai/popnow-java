package ai.popnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * The service accepted a task. Acceptance is not completion.
 *
 * <p>1 credit is $0.001. {@code estimatedCredits} and {@code estimatedCost} are the same hold,
 * once in credits and once in USD.
 *
 * @param taskId           the identifier to poll, and the one to persist before anything else
 * @param state            {@code QUEUED} for a new submission; an idempotent replay reports the
 *                         original task's current state
 * @param estimatedCost    funds held on acceptance, in USD, and the ceiling for this task's charge;
 *                         unused funds are released and nothing above the hold is collected later.
 *                         {@code null} when {@link #completed()} is present: a finished task has a
 *                         real cost instead, in {@link TaskRecord#cost()}
 * @param deadlineAt       the service's execution deadline, fixed at acceptance and unrelated to
 *                         any local wait
 * @param sourceTaskId     present when this task was created by {@link PopNowClient#retryTask}
 * @param estimatedCredits the same hold in credits; {@code 0} when {@link #completed()} is present
 * @param completedTask    the terminal task record, present only when the submission asked the
 *                         service to wait ({@link CreateTaskOptions#withWait}) and the task
 *                         finished inside that budget; never part of the JSON acceptance itself
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AcceptedTask(String taskId, TaskState state, BigDecimal estimatedCost, Instant deadlineAt,
                           String sourceTaskId, long estimatedCredits, TaskRecord completedTask) {

    /**
     * The task this one was retried from, when it was a retry.
     *
     * @return the source task identifier, or empty when this task was not a retry
     */
    public Optional<String> sourceTask() {
        return Optional.ofNullable(sourceTaskId);
    }

    /**
     * The terminal record, when the service's wait reached it.
     *
     * <p><b>Empty is the ordinary outcome, not a failure</b>: the wait ran out, the task is still
     * running, and the next step is {@link PopNowClient#waitForTerminal(String)}. A present record
     * is terminal but not necessarily a success; read {@link TaskRecord#state()}.
     *
     * @return the terminal record, or empty when the task was still in flight
     */
    public Optional<TaskRecord> completed() {
        return Optional.ofNullable(completedTask);
    }

    AcceptedTask withCompleted(TaskRecord record) {
        return new AcceptedTask(taskId, state, estimatedCost, deadlineAt, sourceTaskId, estimatedCredits, record);
    }
}
