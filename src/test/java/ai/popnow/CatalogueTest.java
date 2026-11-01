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
             "usd":{"unit":"per_second","perUnit":"0.009","currency":"USD","blocks":{"seconds":5,"min":1,"cap":4},
             "rules":[{"when":{"resolution":"1080p"},"perUnit":"0.0135"}]},
             "inputSchema":{"type":"object","required":["prompt"]},"version":"v7","availability":"available",
             "badges":["commercial_use","brand_new_badge_from_the_future"],"relatedModels":[],
             "updatedAt":"2026-09-20T08:30:00Z","someFieldAddedNextYear":{"nested":true}}]}""";

    private static final String ONE = """
            {"model":"publisher/model-2.0/text-to-image","family":"publisher/model-2.0","displayName":"Model 2.0",
             "provider":"publisher","modality":"image","tasks":["text-to-image"],"async":true,
             "taskTimeoutSeconds":600,"enabled":true,"available":false,
             "quantityField":"","credits":{"unit":"per_image","perUnit":4},
             "usd":{"unit":"per_image","perUnit":"0.004","currency":"USD"},
             "inputSchema":{},"version":"v7","availability":"maintenance",
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
            ModelUsd usd = model.usdPrice().orElseThrow();
            assertEquals("per_second", usd.unit());
            assertEquals("USD", usd.currency());
            assertEquals(new BigDecimal("0.009"), usd.perUnit());
            assertEquals(new BigDecimal("0.0135"), usd.rules().get(0).perUnit(),
                    "a USD price string survives mapping digit for digit");
            assertEquals("1080p", usd.rules().get(0).when().path("resolution").asText());
            assertTrue(usd.rules().get(0).ownUnit().isEmpty());
            assertEquals(5, usd.billingBlocks().orElseThrow().seconds());
            assertEquals(4, usd.billingBlocks().orElseThrow().cap());
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
            assertEquals("popnow-java/0.2.0 (+https://github.com/popnow-ai/popnow-java)",
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
            assertTrue(model.usd().rules().isEmpty(), "a missing rules array reads as empty");
            assertTrue(model.usd().billingBlocks().isEmpty());
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("the deprecated credit price still maps next to the USD one")
    void legacyCreditPrice() throws Exception {
        try (StubServer server = catalogue()) {
            ApiModel model = server.client().listModels().items().get(0);

            assertEquals("per_second", model.credits().unit());
            assertEquals(0, new BigDecimal("9").compareTo(model.credits().perUnit()));
            assertEquals(0, new BigDecimal("13.5").compareTo(model.credits().rules().get(0).perUnit()),
                    "a fractional credit price survives mapping");
            assertTrue(model.credits().rules().get(0).ownUnit().isEmpty());
        }
    }

    @Test
    @DisplayName("a chat model's token prices and long-context tier map as exact USD")
    void chatTokenPrices() throws Exception {
        ModelUsd usd = PopNowClient.newObjectMapper().readValue("""
                {"unit":"per_million_tokens","currency":"USD","input":"0.3","output":"2.5","cachedInput":"0.03",
                 "cacheWrite":"0.375","longContext":{"thresholdTokens":200000,"inclusive":false,"input":"0.6",
                 "output":"5","cachedInput":"0.06","cacheWrite":"0.75"}}""", ModelUsd.class);

        assertEquals(new BigDecimal("0.3"), usd.input());
        assertEquals(new BigDecimal("2.5"), usd.output());
        assertEquals(new BigDecimal("0.03"), usd.cachedInput());
        assertEquals(new BigDecimal("0.375"), usd.cacheWrite());
        assertEquals(200_000L, usd.longContext().thresholdTokens());
        assertEquals(new BigDecimal("0.6"), usd.longContext().input());
        assertTrue(usd.rules().isEmpty());
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
