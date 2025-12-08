package hcmute.vina.vectorsearchservice.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    private final OpenAIClient client;

    public EmbeddingService(@Value("${openai.api.key}") String apiKey) {
        // Dùng OkHttpClient (chuẩn theo example OpenAI)
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    /**
     * Tạo embedding từ text.
     */
    public List<Float> createEmbedding(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
                .input(text)
                .build();

        CreateEmbeddingResponse response = client.embeddings().create(params);

        return response
                .data()
                .get(0)
                .embedding();
    }

    /**
     * Convert List<Float> → pgvector format
     * Example: {0.12, 0.98, -0.66}
     */
    public String toPgVector(List<Double> vector) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < vector.size(); i++) {
            sb.append(vector.get(i));
            if (i < vector.size() - 1) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }
}
