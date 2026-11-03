package ai.popnow;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class EmbeddingTest {
 @Test void readsVectorsAndUsageWithoutFiles() throws Exception {
  var out=PopNowClient.newObjectMapper().readValue("{\"embeddings\":{\"data\":[{\"index\":0,\"embedding\":[0.25,-0.5]}],\"inputTokens\":7}}", TaskOutput.class);
  assertEquals(7, out.embeddings().inputTokens());
  assertEquals(-0.5, out.embeddings().data().get(0).embedding().get(1));
  assertTrue(out.assets().isEmpty());
 }
}
