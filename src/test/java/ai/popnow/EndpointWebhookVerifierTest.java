package ai.popnow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Standard Webhooks deliveries to account endpoints. */
class EndpointWebhookVerifierTest {

    private static final String SECRET =
            "whsec_" + Base64.getEncoder().encodeToString("endpoint-signing-key-0123456789".getBytes(StandardCharsets.UTF_8));
    private static final long NOW = 1_787_045_531L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
    private static final byte[] BODY = """
            {"type":"task.succeeded","timestamp":"2026-08-18T09:32:11Z","data":{"object":
            {"taskId":"job_1","model":"m","state":"succeeded","cost":"0.34","credits":34,"settled":true,
             "createdAt":"2026-08-18T09:30:00Z"}}}""".getBytes(StandardCharsets.UTF_8);

    private static EndpointWebhookVerifier verifier() {
        return new EndpointWebhookVerifier(SECRET, EndpointWebhookVerifier.DEFAULT_TOLERANCE, CLOCK,
                PopNowClient.newObjectMapper());
    }

    @Test
    @DisplayName("a correctly signed, fresh delivery verifies and maps data.object to a task record")
    void validDelivery() {
        String signature = EndpointWebhookVerifier.sign("msg_1", NOW, BODY, SECRET);
        Map<String, String> headers = Map.of("webhook-id", "msg_1", "webhook-timestamp", Long.toString(NOW),
                "webhook-signature", signature);

        EndpointEvent event = verifier().verify(BODY, headers::get);

        assertEquals("msg_1", event.webhookId());
        assertEquals("task.succeeded", event.type());
        assertEquals(34, event.task().orElseThrow().credits());
        assertTrue(signature.startsWith("v1,"));
    }

    @Test
    @DisplayName("the signature matches an independent computation of the Standard Webhooks scheme")
    void matchesTheSpecification() throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        byte[] key = Base64.getDecoder().decode(SECRET.substring("whsec_".length()));
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        String content = "msg_1." + NOW + "." + new String(BODY, StandardCharsets.UTF_8);
        String expected = "v1," + Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));

        assertEquals(expected, EndpointWebhookVerifier.sign("msg_1", NOW, BODY, SECRET));
    }

    @Test
    @DisplayName("during a rotation either of two space-separated signatures is accepted")
    void rotation() {
        String other = "whsec_" + Base64.getEncoder().encodeToString("old-key".getBytes(StandardCharsets.UTF_8));
        String header = EndpointWebhookVerifier.sign("msg_1", NOW, BODY, other) + " "
                + EndpointWebhookVerifier.sign("msg_1", NOW, BODY, SECRET);

        assertEquals("msg_1", verifier().verify(BODY, "msg_1", Long.toString(NOW), header).webhookId());
    }

    @Test
    @DisplayName("a tampered body, a wrong id and a stale timestamp are all rejected, each for its own reason")
    void rejections() {
        String signature = EndpointWebhookVerifier.sign("msg_1", NOW, BODY, SECRET);
        byte[] tampered = new String(BODY, StandardCharsets.UTF_8).replace("0.34", "0.01").getBytes(StandardCharsets.UTF_8);

        assertEquals(PopNowWebhookException.Reason.SIGNATURE_MISMATCH, assertThrows(PopNowWebhookException.class,
                () -> verifier().verify(tampered, "msg_1", Long.toString(NOW), signature)).reason());
        assertEquals(PopNowWebhookException.Reason.SIGNATURE_MISMATCH, assertThrows(PopNowWebhookException.class,
                () -> verifier().verify(BODY, "msg_2", Long.toString(NOW), signature)).reason());

        long old = NOW - Duration.ofMinutes(6).toSeconds();
        String oldSignature = EndpointWebhookVerifier.sign("msg_1", old, BODY, SECRET);
        assertEquals(PopNowWebhookException.Reason.TIMESTAMP_OUTSIDE_TOLERANCE, assertThrows(PopNowWebhookException.class,
                () -> verifier().verify(BODY, "msg_1", Long.toString(old), oldSignature)).reason());

        assertEquals(PopNowWebhookException.Reason.MISSING_HEADER, assertThrows(PopNowWebhookException.class,
                () -> verifier().verify(BODY, "msg_1", Long.toString(NOW), null)).reason());
        assertEquals(PopNowWebhookException.Reason.MALFORMED_TIMESTAMP, assertThrows(PopNowWebhookException.class,
                () -> verifier().verify(BODY, "msg_1", "+1", signature)).reason());
    }

    @Test
    @DisplayName("a secret that is not whsec_ plus Base64 is refused without echoing it")
    void badSecret() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new EndpointWebhookVerifier("whsec_not base64!"));
        assertTrue(!failure.getMessage().contains("not base64!"));
    }
}
