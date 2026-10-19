package ai.popnow;

import java.time.Duration;
import java.util.Optional;

/**
 * Optional settings that travel with one task submission.
 *
 * <p>Immutable; every {@code with...} method returns a copy. Start from {@link #none()}.
 *
 * <pre>{@code
 * AcceptedTask accepted = client.createTask(request, idempotencyKey,
 *         CreateTaskOptions.none().withWait(Duration.ofSeconds(30)));
 * TaskRecord task = accepted.completed()
 *         .orElseGet(() -> client.waitForTerminal(accepted.taskId()));
 * }</pre>
 */
public final class CreateTaskOptions {

    private static final CreateTaskOptions NONE = new CreateTaskOptions(null, null);

    private final Duration retention;
    private final Duration wait;

    private CreateTaskOptions(Duration retention, Duration wait) {
        this.retention = retention;
        this.wait = wait;
    }

    /**
     * No options: the account's retention, and no server-side wait.
     *
     * @return the empty options
     */
    public static CreateTaskOptions none() {
        return NONE;
    }

    /**
     * Shortens how long this task's outputs and stored request text are kept, sent as
     * {@code X-PopNow-Retention}.
     *
     * <p>It only ever shortens: the effective value is the smallest of this, the account setting
     * and the platform maximum, and it is reported back in {@link TaskRecord#retention()}.
     * {@link Duration#ZERO} is valid and removes the outputs as soon as the task is terminal.
     *
     * @param value a whole number of seconds, not negative; {@code null} leaves the account
     *              setting in charge
     * @return a copy with the retention set
     * @throws IllegalArgumentException when the value is negative or not a whole number of seconds
     */
    public CreateTaskOptions withRetention(Duration value) {
        if (value != null) {
            if (value.isNegative()) {
                throw new IllegalArgumentException("retention must not be negative");
            }
            if (value.getNano() != 0) {
                throw new IllegalArgumentException("retention must be a whole number of seconds");
            }
        }
        return new CreateTaskOptions(value, wait);
    }

    /**
     * Asks the service to hold the connection open until the task finishes, sent as the
     * {@code wait} query parameter.
     *
     * <p><b>An optimisation, never a guarantee.</b> When the task finishes inside the budget,
     * {@link AcceptedTask#completed()} carries the terminal record; when it does not, the ordinary
     * acceptance comes back and the caller polls from there. The service clamps the value to its
     * own ceiling (60 seconds today), which is deliberately not copied here. This request's local
     * timeout is extended by the wait so that the client does not cut its own answer off.
     *
     * @param value a positive whole number of seconds; {@code null} for no wait
     * @return a copy with the wait set
     * @throws IllegalArgumentException when the value is not positive or not a whole number of
     *                                  seconds
     */
    public CreateTaskOptions withWait(Duration value) {
        if (value != null) {
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("wait must be positive");
            }
            if (value.getNano() != 0) {
                throw new IllegalArgumentException("wait must be a whole number of seconds");
            }
        }
        return new CreateTaskOptions(retention, value);
    }

    /**
     * The retention to request, when one was set.
     *
     * @return the retention, or empty to leave the account setting in charge
     */
    public Optional<Duration> retention() {
        return Optional.ofNullable(retention);
    }

    /**
     * The server-side wait to request, when one was set.
     *
     * @return the wait, or empty for none
     */
    public Optional<Duration> serverWait() {
        return Optional.ofNullable(wait);
    }
}
