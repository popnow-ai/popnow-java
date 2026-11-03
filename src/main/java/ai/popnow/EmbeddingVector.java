package ai.popnow;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
/** One vector, identified by the corresponding input index. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmbeddingVector(int index, List<Double> embedding) {}
