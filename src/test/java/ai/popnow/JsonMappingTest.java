package ai.popnow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The mapping layer: where unknown values land, both timestamp spellings, amounts that must never
 * become floating point, and defensive copying. */
class JsonMappingTest {

    private static final ObjectMapper MAPPER = PopNowClient.newObjectMapper();

    @Test
    @DisplayName("a task state added after this release parses as UNKNOWN rather than failing")
    void unknownStateParses() throws Exception {
        TaskRecord record = MAPPER.readValue("""
                {"taskId":"t","model":"m","state":"quarantined","errorCode":"something_new_upstream",
                 "cost":"0","settled":false,"createdAt":"2026-09-20T08:30:00Z"}""", TaskRecord.class);

        assertEquals(TaskState.UNKNOWN, record.state());
        assertFalse(record.terminal(), "UNKNOWN is not terminal; the client must not call it finished");
        assertEquals(TaskErrorCode.UPSTREAM_FAILED, record.errorCode(),
                "the contract says an unrecognised error code is handled as upstream_failed");
    }

    @Test
    @DisplayName("the known states round-trip through their wire values")
    void stateWireValues() {
        assertEquals(TaskState.SUCCEEDED, TaskState.fromWireValue("succeeded"));
        assertEquals("succeeded", TaskState.SUCCEEDED.wireValue());
        assertNull(TaskState.fromWireValue(null));
        assertTrue(TaskState.QUEUED.active());
        assertTrue(TaskState.RUNNING.active());
        assertFalse(TaskState.SUCCEEDED.active());
        assertTrue(TaskState.EXPIRED.terminal());
        assertTrue(TaskState.CANCELED.terminal());
        assertFalse(TaskState.UNKNOWN.terminal());
        assertNull(TaskErrorCode.fromWireValue(null));
        assertEquals(TaskErrorCode.CONTENT_REJECTED, TaskErrorCode.fromWireValue("content_rejected"));
    }

    @Test
    @DisplayName("timestamps parse with and without an explicit offset")
    void timestamps() throws Exception {
        assertEquals(Instant.parse("2026-09-20T00:30:00Z"),
                MAPPER.readValue("\"2026-09-20T08:30:00+08:00\"", Instant.class));
        assertEquals(Instant.parse("2026-09-20T08:30:00Z"),
                MAPPER.readValue("\"2026-09-20T08:30:00Z\"", Instant.class));
        assertNull(MAPPER.readValue("\"\"", Instant.class));
        assertEquals("\"2026-09-20T08:30:00Z\"",
                MAPPER.writeValueAsString(Instant.parse("2026-09-20T08:30:00Z")));
    }

    @Test
    @DisplayName("decimal amounts never become binary floating point")
    void amountsStayExact() throws Exception {
        TaskRecord record = MAPPER.readValue("""
                {"taskId":"t","model":"m","state":"succeeded","cost":0.1,"settled":true,
                 "createdAt":"2026-09-20T08:30:00Z"}""", TaskRecord.class);

        assertEquals(new BigDecimal("0.1"), record.cost());
    }

    @Test
    @DisplayName("missing collections come back empty, so callers never null-check them")
    void collectionsAreNormalized() throws Exception {
        TaskRecord record = MAPPER.readValue("""
                {"taskId":"t","model":"m","state":"succeeded","cost":"0","settled":true,
                 "createdAt":"2026-09-20T08:30:00Z","output":{"text":"only text"}}""", TaskRecord.class);

        assertEquals(List.of(), record.assets());
        assertEquals("only text", record.outputText().orElseThrow());

        TaskRecord noOutput = MAPPER.readValue("""
                {"taskId":"t","model":"m","state":"failed","cost":"0","settled":true,
                 "createdAt":"2026-09-20T08:30:00Z"}""", TaskRecord.class);
        assertEquals(List.of(), noOutput.assets());
        assertTrue(noOutput.outputText().isEmpty());
    }

    @Test
    @DisplayName("a speech-to-text result keeps the plain text and adds the timed transcript")
    void transcriptMaps() throws Exception {
        TaskRecord record = MAPPER.readValue("""
                {"taskId":"t","model":"m","state":"succeeded","cost":"0.01","settled":true,
                 "createdAt":"2026-10-05T08:30:00Z","requestId":"req_abc",
                 "output":{"text":"hello there","transcript":{"text":"hello there","language":"en",
                   "languageCode":"eng","languageProbability":0.98,"durationSeconds":1.5,
                   "words":[{"text":"hello","start":0.1,"end":0.5,"type":"word","speakerId":"speaker_0"},
                            {"text":"there","start":0.6,"end":1.0}],
                   "channels":[{"index":1,"text":"there","words":[{"text":"there","start":0.6,"end":1.0}]}]}}}""",
                TaskRecord.class);

        assertEquals("hello there", record.outputText().orElseThrow(), "text stays the plain transcript");
        TaskTranscript transcript = record.transcript().orElseThrow();
        assertEquals("eng", transcript.languageCode());
        assertEquals(0.98, transcript.languageProbability());
        assertEquals(2, transcript.words().size());
        assertEquals(0.1, transcript.words().get(0).start());
        assertEquals("speaker_0", transcript.words().get(0).speakerId());
        assertNull(transcript.words().get(1).speakerId());
        assertEquals(1, transcript.channels().get(0).index());
        assertEquals(1, transcript.channels().get(0).words().size());
        assertEquals("req_abc", record.requestId());

        TaskRecord plain = MAPPER.readValue("""
                {"taskId":"t","model":"m","state":"succeeded","cost":"0","settled":true,
                 "createdAt":"2026-10-05T08:30:00Z","output":{"text":"no timing"}}""", TaskRecord.class);
        assertTrue(plain.transcript().isEmpty());
        assertNull(plain.requestId());
        assertEquals(List.of(), MAPPER.readValue("{\"text\":\"x\"}", TaskTranscript.class).words(),
                "absent arrays come back empty");
    }

    @Test
    @DisplayName("layer decomposition assets carry their stacking order and placement")
    void layerMaps() throws Exception {
        TaskRecord record = MAPPER.readValue("""
                {"taskId":"t","model":"m","state":"succeeded","cost":"0.02","settled":true,
                 "createdAt":"2026-10-05T08:30:00Z","output":{"assets":[
                   {"key":"out-0","url":"https://cdn.example.test/0.png","layer":{"zIndex":0}},
                   {"key":"out-1","url":"https://cdn.example.test/1.png",
                    "layer":{"zIndex":1,"name":"cup","boundingBox":[10,20,110,220]}}]}}""", TaskRecord.class);

        TaskAssetLayer base = record.assets().get(0).layer();
        TaskAssetLayer cup = record.assets().get(1).layer();
        assertEquals(0, base.zIndex());
        assertFalse(base.positioned(), "the base image has no box");
        assertEquals(List.of(), base.boundingBox());
        assertEquals(1, cup.zIndex());
        assertEquals("cup", cup.name());
        assertEquals(List.of(10, 20, 110, 220), cup.boundingBox());
        assertTrue(cup.positioned());

        TaskAsset ordinary = MAPPER.readValue("{\"key\":\"out-0\",\"url\":\"https://cdn.example.test/a.png\"}",
                TaskAsset.class);
        assertNull(ordinary.layer(), "only layer decomposition results carry a layer");
    }

    @Test
    @DisplayName("an output with no assets field reads as an empty list")
    void outputWithoutAssets() {
        assertNull(new TaskOutput("t", null, null).transcript());
        assertEquals(List.of(), new TaskOutput("t", null, null).assets());
    }

    @Test
    @DisplayName("a deleted output is unavailable and never readable")
    void deletedAsset() throws Exception {
        TaskAsset asset = MAPPER.readValue("""
                {"deleted":true,"unavailable":true}""", TaskAsset.class);

        assertTrue(asset.deleted());
        assertFalse(asset.readable());
    }

    @Test
    @DisplayName("credits are mapped next to the USD amounts on records, quotes and balances")
    void creditsAreMapped() throws Exception {
        TaskRecord record = MAPPER.readValue("""
                {"taskId":"t","model":"m","state":"succeeded","cost":"0.34","credits":340,"settled":true,
                 "createdAt":"2026-10-19T08:30:00Z"}""", TaskRecord.class);
        assertEquals(340, record.credits());
        assertEquals(new BigDecimal("0.34"), record.cost());

        TaskQuote quote = MAPPER.readValue("""
                {"quoteId":"q","model":"m","estimatedCost":"0.30","maxCharge":"0.34","maxCredits":340,
                 "expectedCredits":300,"currency":"USD","quantity":"5","unit":"per_second",
                 "expiresAt":"2026-10-19T08:35:00Z"}""", TaskQuote.class);
        assertEquals(340, quote.maxCredits());
        assertEquals(300, quote.expectedCredits());

        Balance balance = MAPPER.readValue("""
                {"available":"12.50","held":"0.34","total":"12.84","availableCredits":12500,
                 "heldCredits":340,"totalCredits":12840}""", Balance.class);
        assertEquals(12500, balance.availableCredits());
        assertEquals(340, balance.heldCredits());
        assertEquals(12840, balance.totalCredits());
    }

    @Test
    @DisplayName("a pending asset has no URL and is not readable")
    void pendingAsset() throws Exception {
        TaskAsset asset = MAPPER.readValue("""
                {"key":"out-0","pending":true,"unavailable":false}""", TaskAsset.class);

        assertTrue(asset.url().isEmpty());
        assertFalse(asset.readable());
    }

    @Test
    @DisplayName("the request keeps its own copy of the input map")
    void requestCopiesItsInput() {
        Map<String, Object> input = new HashMap<>(Map.of("prompt", "before"));
        CreateTaskRequest request = CreateTaskRequest.of("publisher/model/text-to-image", input);
        input.put("prompt", "after");

        assertEquals("before", request.input().get("prompt"));
        assertThrows(UnsupportedOperationException.class, () -> request.input().put("prompt", "later"));
        assertThrows(IllegalArgumentException.class, () -> CreateTaskRequest.of("  ", Map.of()));
        assertThrows(NullPointerException.class, () -> CreateTaskRequest.of("m", null));
    }

    @Test
    @DisplayName("withQuote carries both the token and the confirmed amount")
    void withQuote() {
        TaskQuote quote = new TaskQuote("qt_1", "m", new BigDecimal("0.25"), new BigDecimal("0.30"),
                "USD", "5", "per_second", Instant.parse("2036-01-01T00:00:00Z"), 300, 250);
        CreateTaskRequest request = CreateTaskRequest.of("m", Map.of("prompt", "x")).withQuote(quote);

        assertEquals("qt_1", request.quoteId());
        assertEquals(300L, request.expectedCredits(), "the hold is maxCredits, so that is what is confirmed");
        assertNull(request.expectedCost(), "credits are sent alone; they win over expectedCost anyway");
        assertThrows(NullPointerException.class, () -> CreateTaskRequest.of("m", Map.of()).withQuote(null));
    }

    @Test
    @DisplayName("the upload ticket freezes its headers, because they are part of the signature")
    void ticketHeadersAreFrozen() {
        UploadTicket ticket = new UploadTicket("fil_1", "popnow://f/fil_1", "https://storage.example.test/put",
                "PUT", new HashMap<>(Map.of("Content-Type", "image/png")), Instant.now(), 10L);

        assertThrows(UnsupportedOperationException.class, () -> ticket.headers().put("X-Extra", "1"));
        assertEquals(Map.of(), new UploadTicket("f", "k", "u", "PUT", null, Instant.now(), 1L).headers());
    }

    @Test
    @DisplayName("the shipped module is registrable on a caller's own mapper")
    void moduleIsPublicAndUsable() throws Exception {
        ObjectMapper theirs = new ObjectMapper().registerModule(PopNowClient.jacksonModule());

        assertEquals(Instant.parse("2026-09-20T08:30:00Z"),
                theirs.readValue("\"2026-09-20T08:30:00Z\"", Instant.class));
    }

    @Test
    @DisplayName("version() and the User-Agent match the pom's <version>")
    void versionMatchesThePom() throws Exception {
        String pom = java.nio.file.Files.readString(java.nio.file.Path.of("pom.xml"));
        java.util.regex.Matcher version = java.util.regex.Pattern
                .compile("<artifactId>popnow-java</artifactId>\\s*<version>([^<]+)</version>").matcher(pom);
        assertTrue(version.find(), "the pom declares no project version");
        assertEquals(version.group(1), PopNowClient.version());
        assertTrue(PopNowClient.USER_AGENT.startsWith("popnow-java/" + version.group(1) + " "));
    }
}
