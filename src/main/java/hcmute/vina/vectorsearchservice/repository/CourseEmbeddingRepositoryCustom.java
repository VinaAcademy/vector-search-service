package hcmute.vina.vectorsearchservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import hcmute.vina.vectorsearchservice.entity.CourseEmbedding;

@Repository
public class CourseEmbeddingRepositoryCustom {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Optional<CourseEmbedding> findById(UUID courseId) {
        String sql = "SELECT course_id, embedding FROM course_embedding WHERE course_id = ?";
        try {
            CourseEmbedding embedding = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                CourseEmbedding ce = new CourseEmbedding();
                ce.setCourseId((UUID) rs.getObject("course_id"));
                
                return ce;
            }, courseId);
            return Optional.of(embedding);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void save(CourseEmbedding embedding) {
        String vectorString = arrayToVectorString(embedding.getEmbedding());
        
        // Check if exists
        String checkSql = "SELECT COUNT(*) FROM course_embedding WHERE course_id = ?";
        int count = jdbcTemplate.queryForObject(checkSql, Integer.class, embedding.getCourseId());
        
        if (count > 0) {
            // Update existing - cast string to vector
            String updateSql = "UPDATE course_embedding SET embedding = ?::vector WHERE course_id = ?";
            jdbcTemplate.update(updateSql, vectorString, embedding.getCourseId());
        } else {
            // Insert new - cast string to vector
            String insertSql = "INSERT INTO course_embedding (course_id, embedding) VALUES (?, ?::vector)";
            jdbcTemplate.update(insertSql, embedding.getCourseId(), vectorString);
        }
    }

    /**
     * Parse vector string format "[0.1, 0.2, 0.3, ...]" to float array
     */
    private float[] parseVectorString(String vectorStr) {
        if (vectorStr == null || vectorStr.trim().isEmpty()) {
            return new float[0];
        }
        
        // Remove brackets and split
        String cleaned = vectorStr.replaceAll("[\\[\\]]", "").trim();
        String[] parts = cleaned.split(",");
        
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    /**
     * Convert float array to vector string format
     */
    private String arrayToVectorString(float[] array) {
        if (array == null || array.length == 0) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
