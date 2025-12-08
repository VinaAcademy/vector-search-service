package hcmute.vina.vectorsearchservice.service;

import hcmute.vina.vectorsearchservice.builder.CourseSqlBuilder;
import hcmute.vina.vectorsearchservice.dto.CourseDto;
import hcmute.vina.vectorsearchservice.dto.request.CourseSearchRequest;
import hcmute.vina.vectorsearchservice.mapper.CourseRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CourseSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    public List<CourseDto> vectorSearch(CourseSearchRequest req, int page, int size) {

        Map<String, Object> params = new HashMap<>();

        // 1. WHERE clause (không đổi)
        String whereClause = CourseSqlBuilder.buildWhere(req, params);

        // 2. Tạo embedding từ keyword
        List<Float> embedding = embeddingService.createEmbedding(
                req.getKeyword() != null ? req.getKeyword() : ""
        );
        // convert List<Double> → double[]
        double[] vectorArray = embedding.stream()
                .mapToDouble(Float::doubleValue)
                .toArray();

        params.put("vector", vectorArray);
        params.put("size", size);
        params.put("offset", page * size);

        // 3. SQL query pgvector
        String sql = """
            SELECT
                id, name, description, type, category_slug, price, rating
            FROM course
        """ + whereClause + """
            ORDER BY embedding <-> :vector
            LIMIT :size OFFSET :offset
        """;

        return new NamedParameterJdbcTemplate(jdbcTemplate)
                .query(sql, params, new CourseRowMapper());
    }
}
