package ai.popnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * The service accepted a task. Acceptance is not completion.
 *
 * <p>{@code estimatedCostUsd} is the exact USD held from the API balance on acceptance;
 * {@code estimatedCost} is its legacy alias.
 *
 * @param taskId           the identifier to poll, and the one to persist before anything else
 * @param state            {@code QUEUED} for a new submission; an idempotent replay reports the
 *                         original task's current state
 * @param estimatedCost    legacy USD alias of {@code estimatedCostUsd}
 * @param deadlineAt       the service's execution deadline, fixed at acceptance and unrelated to
 *                         any local wait
 * @param sourceTaskId     present when this task was created by {@link PopNowClient#retryTask}
 * @param estimatedCredits deprecated display projection of the hold in legacy credits; use
 *                         {@link #estimatedCostUsd()}
 * @param completedTask    the terminal task record, present only when the submission asked the
 *                         service to wait ({@link CreateTaskOptions#withWait}) and the task
 *                         finished inside that budget; never part of the JSON acceptance itself
 * @param estimatedCostUsd funds held on acceptance, in exact USD, and the ceiling for this task's
 *                         charge; unused funds are released and nothing above the hold is
 *                         collected later. {@code null} when {@link #completed()} is present: a
 *                         finished task has a real cost instead, in {@link TaskRecord#costUsd()}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AcceptedTask(String taskId, TaskState state, BigDecimal estimatedCost, Instant deadlineAt,
                           String sourceTaskId, long estimatedCredits, TaskRecord completedTask,
                           BigDecimal estimatedCostUsd) {

    /**
     * The 0.1 shape, without {@code estimatedCostUsd}.
     *
     * @param taskId           the task identifier
     * @param state            the state at acceptance
     * @param estimatedCost    legacy USD alias of the hold
     * @param deadlineAt       the service's execution deadline
     * @param sourceTaskId     the retried task, when this is a retry
     * @param estimatedCredits the hold in legacy credits
     * @param completedTask    the terminal record, when the wait reached it
     * @deprecated use the canonical constructor, which also takes {@code estimatedCostUsd}
     */
    @Deprecated
    public AcceptedTask(String taskId, TaskState state, BigDecimal estimatedCost, Instant deadlineAt,
                        String sourceTaskId, long estimatedCredits, TaskRecord completedTask) {
        this(taskId, state, estimatedCost, deadlineAt, sourceTaskId, estimatedCredits, completedTask, null);
    }

    /**
     * The hold in legacy credits, rounded up.
     *
     * @return the deprecated credit projection of {@link #estimatedCostUsd()}; {@code 0} when
     *         {@link #completed()} is present
     * @deprecated credits are a display projection, not a billing unit; use
     *             {@link #estimatedCostUsd()}
     */
    @Deprecated
    public long estimatedCredits() {
        return estimatedCredits;
    }

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
        return new AcceptedTask(taskId, state, estimatedCost, deadlineAt, sourceTaskId, estimatedCredits, record,
                estimatedCostUsd);
    }
}
