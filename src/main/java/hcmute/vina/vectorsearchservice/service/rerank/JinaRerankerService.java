package hcmute.vina.vectorsearchservice.service.rerank;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;

@Service
public class JinaRerankerService {

    @Value("${jina.api.key:}")
    private String jinaApiKey;

    @Value("${search.rerank.enabled:false}")
    private boolean rerankEnabled;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public JinaRerankerService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        if (!rerankEnabled) {
            System.out.println("Reranker disabled in config (search.rerank.enabled=false)");
            return;
        }

        if (jinaApiKey == null || jinaApiKey.isBlank()) {
            System.err.println("JINA_API_KEY not configured. Reranker will be disabled.");
            return;
        }

        System.out.println("Jina AI Reranker configured (supports 100+ languages including Vietnamese)");
    }

    public List<ScoredDocument> batchRerank(String query, List<String> docs) {
        List<ScoredDocument> result = new ArrayList<>();

        if (!isAvailable()) {
            return result;
        }

        if (docs == null || docs.isEmpty()) {
            return result;
        }

        try {
            // Build request payload
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "jina-reranker-v2-base-multilingual");
            requestBody.put("query", query);
            
            ArrayNode documentsArray = objectMapper.createArrayNode();
            for (String doc : docs) {
                documentsArray.add(doc);
            }
            requestBody.set("documents", documentsArray);
            requestBody.put("top_n", docs.size()); // Return all documents with scores

            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.jina.ai/v1/rerank"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + jinaApiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Jina API error: " + response.statusCode() + " - " + response.body());
                return result;
            }

            // Parse response
            JsonNode responseJson = objectMapper.readTree(response.body());
            JsonNode results = responseJson.get("results");

            if (results != null && results.isArray()) {
                for (JsonNode item : results) {
                    int index = item.get("index").asInt();
                    double score = item.get("relevance_score").asDouble();
                    
                    if (index >= 0 && index < docs.size()) {
                        result.add(new ScoredDocument(index, docs.get(index), (float) score));
                    }
                }
            }

            // Sort by score descending
            result.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

        } catch (Exception e) {
            System.err.println("Failed to call Jina Reranker API: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    public boolean isAvailable() {
        return rerankEnabled && jinaApiKey != null && !jinaApiKey.isBlank();
    }
}
