package ai.popnow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Catalogue queries: encoding, headers, and "an older client must not break when the server grows
 * one more field". */
class CatalogueTest {

    private static final String LIST = """
            {"total":1,"items":[{"model":"publisher/model-2.0/text-to-image","family":"publisher/model-2.0",
             "displayName":"Model 2.0","provider":"publisher","modality":"image","tasks":["text-to-image"],
             "async":true,"taskTimeoutSeconds":600,
             "enabled":true,"available":true,"quantityField":"duration_seconds","credits":{"unit":"per_second",
             "perUnit":9,"rules":[{"when":{"resolution":"1080p"},"perUnit":13.5}]},
             "inputSchema":{"type":"object","required":["prompt"]},"version":"v7","availability":"available",
             "badges":["commercial_use","brand_new_badge_from_the_future"],"relatedModels":[],
             "updatedAt":"2026-09-20T08:30:00Z","someFieldAddedNextYear":{"nested":true}}]}""";

    private static final String ONE = """
            {"model":"publisher/model-2.0/text-to-image","family":"publisher/model-2.0","displayName":"Model 2.0",
             "provider":"publisher","modality":"image","tasks":["text-to-image"],"async":true,
             "taskTimeoutSeconds":600,"enabled":true,"available":false,
             "quantityField":"","credits":{"unit":"per_image","perUnit":4},"inputSchema":{},"version":"v7","availability":"maintenance",
             "badges":[],"relatedModels":[],"updatedAt":"2026-09-20T08:30:00Z"}""";

    private static StubServer catalogue() throws IOException {
        StubServer server = new StubServer();
        return server.on("/api/v1/models", (request, exchange) -> StubServer.respond(exchange, 200,
                StubServer.envelope("/api/v1/models".equals(exchange.getRequestURI().getRawPath()) ? LIST : ONE)));
    }

    @Test
    @DisplayName("listModels maps the catalogue and tolerates fields this release has never seen")
    void listModels() throws Exception {
        try (StubServer server = catalogue()) {
            ModelPage page = server.client().listModels(
                    ModelFilter.all().withModality("image").withIncludeSchema(true).withSearch("a b"));

            assertEquals(1, page.total());
            assertEquals("publisher/model-2.0/text-to-image", page.modelIds().get(0));
            ApiModel model = page.items().get(0);
            assertTrue(model.callable(), "enabled and available means callable");
            assertEquals("text-to-image", model.task().orElseThrow());
            assertEquals("per_second", model.credits().unit());
            assertEquals(0, new BigDecimal("9").compareTo(model.credits().perUnit()));
            assertEquals(0, new BigDecimal("13.5").compareTo(model.credits().rules().get(0).perUnit()),
                    "a fractional credit price survives mapping");
            assertEquals("1080p", model.credits().rules().get(0).when().path("resolution").asText());
            assertTrue(model.credits().rules().get(0).ownUnit().isEmpty());
            assertEquals(Instant.parse("2026-09-20T08:30:00Z"), model.updatedAt());
            assertTrue(model.inputSchema().has("required"), "the schema stays a tree, not fixed fields");
            assertTrue(model.badges().contains("brand_new_badge_from_the_future"),
                    "an unknown badge is data, not an error");
        }
    }

    @Test
    @DisplayName("filters go on the query string, URL-encoded, with unset fields omitted")
    void queryEncoding() throws Exception {
        try (StubServer server = catalogue()) {
            server.client().listModels(
                    ModelFilter.all().withModality("image").withIncludeSchema(true).withSearch("a b"));

            assertEquals("/api/v1/models?modality=image&search=a+b&includeSchema=1",
                    server.first("/models").path());
        }
    }

    @Test
    @DisplayName("every call carries the bearer key and the selected error language")
    void headers() throws Exception {
        try (StubServer server = catalogue()) {
            server.clientBuilder().errorLanguage("de").build().listModels();

            StubServer.Recorded request = server.first("/models");
            assertEquals("Bearer sk-popnow-0123456789abcdef", request.header("Authorization"));
            assertEquals("de", request.header("Accept-Language"));
            assertEquals("application/json", request.header("Accept"));
            assertEquals("popnow-java/0.1.0 (+https://github.com/popnow-ai/popnow-java)",
                    request.header("User-Agent"),
                    "an explicit User-Agent: the edge refuses some default client agents outright");
        }
    }

    @Test
    @DisplayName("a model id with slashes becomes one path segment, not three")
    void modelIdIsOneSegment() throws Exception {
        try (StubServer server = catalogue()) {
            ApiModel model = server.client().getModel("publisher/model-2.0/text-to-image");

            assertEquals("/api/v1/models/publisher%2Fmodel-2.0%2Ftext-to-image", server.last("/models/").path());
            assertFalse(model.callable(), "available=false is not callable however enabled it is");
            assertTrue(model.credits().rules().isEmpty(), "a missing rules array reads as empty");
        }
    }

    @Test
    @DisplayName("an unset filter adds no query string at all")
    void emptyFilterHasNoQuery() throws Exception {
        try (StubServer server = catalogue()) {
            server.client().listModels();
            assertEquals("/api/v1/models", server.first("/models").path());
        }
    }
}
