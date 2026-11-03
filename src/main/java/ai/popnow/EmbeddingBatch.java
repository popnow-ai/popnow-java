package ai.popnow;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
/** Vectors and measured input usage from a completed embedding task. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmbeddingBatch(List<EmbeddingVector> data, long inputTokens) {}
