package hcmute.vina.vectorsearchservice.service;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.management.RuntimeErrorException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import hcmute.vina.vectorsearchservice.dto.CourseTransfer;
import hcmute.vina.vectorsearchservice.entity.CourseEmbedding;
import hcmute.vina.vectorsearchservice.repository.CourseEmbeddingRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService{

    private final OpenAIClient client;
    
    private ZooModel<String, float[]> model;
    
    private Predictor<String, float[]> predictor;

    // Simple LRU cache to avoid repeated OpenAI calls for the same query
    private final Map<String, List<Float>> embeddingCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Float>> eldest) {
                    return size() > 200; // cap cache size
                }
            }
    );
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private CourseEmbeddingRepository courseEmbeddingRepository;

    //Tạm thời sử dụng OpenAI thay vì model mpnet-base-v2 nên sẽ không init
    @PostConstruct
    public void init() throws ModelNotFoundException, MalformedModelException, IOException {
        Criteria<String, float[]> criteria = Criteria.builder()
                .optApplication(ai.djl.Application.NLP.TEXT_EMBEDDING)
                .setTypes(String.class, float[].class)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-multilingual-mpnet-base-v2")
                .optEngine("PyTorch")
                .optProgress(new ProgressBar())
                .build();

        model = criteria.loadModel();
        predictor = model.newPredictor();
        log.info("Embedding model loaded successfully!");
    }

    public EmbeddingServiceImpl(@Value("${openai.api.key}") String apiKey) {
        // Dùng OkHttpClient (chuẩn theo example OpenAI)
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    /**
     * Tạo embedding từ text.
     * Model OpenAi 1536 dimensional
     */
    @Override
    public List<Float> createEmbedding(String text) {
        if (text == null) {
            text = "";
        }
        String normalized = text.trim().toLowerCase();
        List<Float> cached = embeddingCache.get(normalized);
        if (cached != null) {
            return cached;
        }

        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
                .input(normalized)
                .build();

        CreateEmbeddingResponse response = client.embeddings().create(params);
        List<Float> vector = response
                .data()
                .get(0)
                .embedding();

        embeddingCache.put(normalized, vector);
        return vector;
    }
    

    /**
     * Tạo embedding từ text.
     * Model paraphrase-multilingual-mpnet-base-v2 768 dimensional 
     */
    @Override
    public float[] createEmbedding3(String text) {
        try {
			return predictor.predict(text);
		} catch (Exception e) {
			log.error("Error happen predict embeded", e);
			e.printStackTrace();
		}
		return null;
    }
    
    
    private String cleanHtml(String htmlContent) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return "";
        }
        
        // 1. Phân tích chuỗi HTML
        Document document = Jsoup.parse(htmlContent);
        
        // 2. Trích xuất văn bản thuần túy
        String plainText = document.text();
        
        return plainText;
    }

    /**
     * Convert List<Float> → pgvector format
     * Example: {0.12, 0.98, -0.66}
     */
    @Override
    public String toPgVector(List<Double> vector) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < vector.size(); i++) {
            sb.append(vector.get(i));
            if (i < vector.size() - 1) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }
    
    @Override
    public List<CourseTransfer> getCoursesForEmbedding() {
        String sql = """
            SELECT c.id, c.name, c.description, u.full_name
            FROM courses c
            INNER JOIN course_instructor ci 
        			ON c.id = ci.course_id
        			AND ci.is_owner = TRUE
        	INNER JOIN users u
        			ON ci.user_id = u.id
            WHERE status = 'PUBLISHED'
        """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> CourseTransfer.builder()
                .courseId(UUID.fromString(rs.getString("id")))
                .courseName(rs.getString("name"))
                .description(rs.getString("description"))
                .instructorName(rs.getString("full_name"))
                .build());
    }
    
    @Override
    public float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i).floatValue();
        }
        return arr;
    }
    
    public void updateCourseEmbedding(CourseTransfer course) {

        String text = course.getCourseName() + ". " + cleanHtml(course.getDescription()) + ". " +course.getInstructorName();

        //float[] vector = toFloatArray(createEmbedding(text));
        float[] vector = toFloatArray(createEmbedding(text));
        CourseEmbedding embedding = CourseEmbedding.builder()
                .courseId(course.getCourseId())
                .embedding(vector)
                .build();

        courseEmbeddingRepository.save(embedding);
    }
    
    @Override
    public void migrateAllCourse() {
    	List<CourseTransfer> courseTransfers = getCoursesForEmbedding();
    	
    	log.debug("Size course transfer {}", courseTransfers.size());
    	courseTransfers.forEach(ct->{
        	Optional<CourseEmbedding> existing = courseEmbeddingRepository.findById(ct.getCourseId());
        	if (existing.isPresent()) {
        		updateCourseEmbedding(ct);
        		log.debug("setup vector for course {}",ct.getCourseId());
        	}

    	});

    }
    
    @PreDestroy
    public void cleanup() {
        if (predictor != null) predictor.close();
        if (model != null) model.close();
    }

}
