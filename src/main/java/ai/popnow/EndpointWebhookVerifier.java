package ai.popnow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Authenticates deliveries sent to an account endpoint, which follow the
 * <a href="https://www.standardwebhooks.com/">Standard Webhooks</a> format.
 *
 * <p>Account endpoints are registered once in the API console and receive events such as
 * {@code task.completed} for every task on the account. Each one has its own {@code whsec_}
 * secret. Their deliveries carry three headers:
 *
 * <pre>
 * webhook-id: msg_...
 * webhook-timestamp: 1787045531
 * webhook-signature: v1,base64( HMAC-SHA256( key, "&lt;webhook-id&gt;.&lt;webhook-timestamp&gt;.&lt;raw_body&gt;" ) )
 * </pre>
 *
 * <p>Two differences from {@link CallbackSignatureVerifier}, which handles {@code callBackUrl}
 * deliveries: the raw body itself is signed (no SHA-256 step), and the HMAC key is the
 * Base64-decoded part of the secret after {@code whsec_}, not the secret string. During a secret
 * rotation the signature header holds two space-separated signatures, and either one matching is
 * enough.
 *
 * <p>Build one per endpoint secret and reuse it; instances are immutable and thread-safe.
 *
 * <pre>{@code
 * EndpointWebhookVerifier verifier =
 *         new EndpointWebhookVerifier(System.getenv("POPNOW_ENDPOINT_SECRET"));
 *
 * byte[] body = request.getInputStream().readAllBytes();   // the raw bytes, never re-serialized
 * EndpointEvent event = verifier.verify(body, request::getHeader);
 * event.task().ifPresent(task -> System.out.println(event.type() + " " + task.state()));
 * }</pre>
 */
public final class EndpointWebhookVerifier {

    /** Header carrying the message id, stable across retries. */
    public static final String ID_HEADER = "webhook-id";

    /** Header carrying the Unix timestamp the signature covers. */
    public static final String TIMESTAMP_HEADER = "webhook-timestamp";

    /** Header carrying one or more space-separated {@code v1,<base64>} signatures. */
    public static final String SIGNATURE_HEADER = "webhook-signature";

    /** Default accepted clock skew in either direction. */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

    private final byte[] key;
    private final Duration tolerance;
    private final Clock clock;
    private final ObjectMapper mapper;

    /**
     * A verifier with the default five-minute replay window and the system clock.
     *
     * @param secret the endpoint secret, {@code whsec_} followed by Base64
     * @throws IllegalArgumentException when the secret is blank or not Base64 after the prefix
     */
    public EndpointWebhookVerifier(String secret) {
        this(secret, DEFAULT_TOLERANCE, Clock.systemUTC(), PopNowJson.DEFAULT);
    }

    /**
     * A fully configured verifier.
     *
     * @param secret    the endpoint secret, {@code whsec_} followed by Base64
     * @param tolerance accepted clock skew in either direction
     * @param clock     the clock to compare timestamps against; injectable for tests
     * @param mapper    the mapper used to read the body; must have
     *                  {@link PopNowClient#jacksonModule()} registered
     * @throws IllegalArgumentException when the secret is unusable or the tolerance is negative
     */
    public EndpointWebhookVerifier(String secret, Duration tolerance, Clock clock, ObjectMapper mapper) {
        this.key = signingKey(secret);
        Objects.requireNonNull(tolerance, "tolerance");
        if (tolerance.isNegative()) {
            throw new IllegalArgumentException("tolerance must not be negative");
        }
        this.tolerance = tolerance;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Verifies a delivery, taking the three headers from a case-insensitive lookup function such
     * as {@code request::getHeader}.
     *
     * @param rawBody      the exact bytes received
     * @param headerLookup returns a header value by name, or {@code null} when absent
     * @return the verified event
     * @throws NullPointerException   when either argument is null
     * @throws PopNowWebhookException when the delivery is not authentic, fresh and well-formed;
     *                                respond 401 and discard it
     */
    public EndpointEvent verify(byte[] rawBody, Function<String, String> headerLookup) {
        Objects.requireNonNull(headerLookup, "headerLookup");
        return verify(rawBody, headerLookup.apply(ID_HEADER), headerLookup.apply(TIMESTAMP_HEADER),
                headerLookup.apply(SIGNATURE_HEADER));
    }

    /**
     * Verifies a delivery from explicit header values.
     *
     * <p>The signature is checked before the timestamp, so a forged delivery is reported as a
     * forgery rather than as stale.
     *
     * @param rawBody   the exact bytes received
     * @param webhookId value of {@value #ID_HEADER}
     * @param timestamp value of {@value #TIMESTAMP_HEADER}
     * @param signature value of {@value #SIGNATURE_HEADER}
     * @return the verified event
     * @throws NullPointerException   when {@code rawBody} is null
     * @throws PopNowWebhookException when a header is missing or malformed, no signature matches,
     *                                the timestamp is outside the window, or the body is not a
     *                                JSON object
     */
    public EndpointEvent verify(byte[] rawBody, String webhookId, String timestamp, String signature) {
        Objects.requireNonNull(rawBody, "rawBody");
        String id = requireHeader(webhookId, ID_HEADER);
        String sentAtText = requireHeader(timestamp, TIMESTAMP_HEADER);
        String presented = requireHeader(signature, SIGNATURE_HEADER);
        if (!sentAtText.matches("[0-9]+")) {
            throw new PopNowWebhookException(PopNowWebhookException.Reason.MALFORMED_TIMESTAMP,
                    TIMESTAMP_HEADER + " is not a Unix timestamp");
        }
        long sentAt;
        try {
            sentAt = Long.parseLong(sentAtText);
        } catch (NumberFormatException overflow) {
            throw new PopNowWebhookException(PopNowWebhookException.Reason.MALFORMED_TIMESTAMP,
                    TIMESTAMP_HEADER + " is not a Unix timestamp");
        }

        byte[] expected = sign(id, sentAtText, rawBody, key).getBytes(StandardCharsets.UTF_8);
        boolean matched = false;
        for (String candidate : presented.trim().split("\\s+")) {
            // Compare every candidate, so the time taken does not reveal which one matched.
            matched |= MessageDigest.isEqual(expected, candidate.getBytes(StandardCharsets.UTF_8));
        }
        if (!matched) {
            throw new PopNowWebhookException(PopNowWebhookException.Reason.SIGNATURE_MISMATCH,
                    "no delivery signature matches; the request did not come from PopNow");
        }

        long skewSeconds = Math.abs(clock.instant().getEpochSecond() - sentAt);
        if (skewSeconds > tolerance.toSeconds()) {
            throw new PopNowWebhookException(PopNowWebhookException.Reason.TIMESTAMP_OUTSIDE_TOLERANCE,
                    "the delivery is signed correctly but its timestamp is " + skewSeconds
                            + "s away from now, outside the " + tolerance.toSeconds() + "s window");
        }

        JsonNode body;
        try {
            body = mapper.readTree(rawBody);
        } catch (IOException malformed) {
            throw new PopNowWebhookException(PopNowWebhookException.Reason.MALFORMED_BODY,
                    "the delivery body was not valid JSON");
        }
        if (body == null || !body.isObject()) {
            throw new PopNowWebhookException(PopNowWebhookException.Reason.MALFORMED_BODY,
                    "the delivery body was not a JSON object");
        }
        JsonNode type = body.get("type");
        return new EndpointEvent(id, sentAt, type != null && type.isTextual() ? type.asText() : null,
                readTask(body.path("data").get("object")), body);
    }

    /**
     * Computes the {@code v1,<base64>} signature for a delivery.
     *
     * <p>Exposed so that tests and local receivers can produce a delivery this verifier accepts.
     *
     * @param webhookId the message id
     * @param timestamp Unix timestamp in seconds
     * @param rawBody   the exact bytes of the body
     * @param secret    the endpoint secret, {@code whsec_} followed by Base64
     * @return the signature header value
     * @throws IllegalArgumentException when the id is blank or the secret is unusable
     */
    public static String sign(String webhookId, long timestamp, byte[] rawBody, String secret) {
        return sign(Internal.requireText(webhookId, "webhookId"), Long.toString(timestamp),
                Objects.requireNonNull(rawBody, "rawBody"), signingKey(secret));
    }

    private static String sign(String webhookId, String timestamp, byte[] rawBody, byte[] key) {
        byte[] prefix = (webhookId + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(prefix);
            return "v1," + Base64.getEncoder().encodeToString(mac.doFinal(rawBody));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException(
                    "HmacSHA256 is required of every Java platform but is unavailable here", unavailable);
        }
    }

    // The HMAC key is the Base64-decoded part of the secret after "whsec_", not the secret text.
    private static byte[] signingKey(String secret) {
        String trimmed = Internal.requireText(secret, "secret").trim();
        if (trimmed.startsWith("whsec_")) {
            trimmed = trimmed.substring("whsec_".length());
        }
        try {
            byte[] key = Base64.getDecoder().decode(trimmed);
            if (key.length == 0) {
                throw new IllegalArgumentException("the endpoint secret must be whsec_ followed by Base64");
            }
            return key;
        } catch (IllegalArgumentException malformed) {
            // Never echo the secret, not even in part.
            throw new IllegalArgumentException("the endpoint secret must be whsec_ followed by Base64");
        }
    }

    private static String requireHeader(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new PopNowWebhookException(PopNowWebhookException.Reason.MISSING_HEADER, name + " is missing");
        }
        return value.trim();
    }

    private TaskRecord readTask(JsonNode object) {
        if (object == null || !object.isObject()) {
            return null;
        }
        try {
            return mapper.treeToValue(object, TaskRecord.class);
        } catch (JsonProcessingException malformed) {
            throw new PopNowWebhookException(PopNowWebhookException.Reason.MALFORMED_BODY,
                    "the event's data.object was not a readable task record");
        }
    }
}
