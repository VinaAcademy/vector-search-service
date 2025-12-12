package hcmute.vina.vectorsearchservice.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import hcmute.vina.vectorsearchservice.builder.CourseSqlBuilder;
import hcmute.vina.vectorsearchservice.dto.CourseDto;
import hcmute.vina.vectorsearchservice.dto.request.CourseSearchRequest;
import hcmute.vina.vectorsearchservice.mapper.CourseRowMapper;
import hcmute.vina.vectorsearchservice.service.rerank.BgeRerankerService;
import hcmute.vina.vectorsearchservice.service.rerank.ScoredDocument;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourseSearchServiceImpl implements CourseSearchService{

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final BgeRerankerService rerankerService; // Optional reranker

    @Value("${search.rerank.enabled:true}")
    private boolean rerankEnabled;

    @Override
    public List<CourseDto> search(CourseSearchRequest req, int page, int size) {
        // 1. Expand query for better semantic understanding
        String expandedQuery = expandQuery(req.getKeyword());
        
        // 2. Retrieve candidates using expanded query
        List<CourseDto> candidates = retrieveCandidates(req, expandedQuery);

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Hybrid search: combine vector + BM25 + rerank
        boolean useRerank = rerankEnabled && rerankerService != null && rerankerService.isAvailable() && page == 0;
        List<CourseDto> rankedCandidates = useRerank
            ? hybridSearchWithRerank(candidates, req.getKeyword(), expandedQuery)
            : hybridSearch(candidates, req.getKeyword(), expandedQuery);

        // 4. Paginate results
        return paginate(rankedCandidates, page, size);
    }

    private List<CourseDto> retrieveCandidates(CourseSearchRequest req, String expandedQuery) {
        Map<String, Object> params = new HashMap<>();
        String whereClause = CourseSqlBuilder.buildWhere(req, params);

        String keyword = expandedQuery;
        if (keyword == null || keyword.trim().isEmpty()) {
            keyword = "";
        }

        float[] embedding = embeddingService.toFloatArray(
                embeddingService.createEmbedding(keyword.trim())
        );
        
        params.put("vector", embedding);
        // Keep candidate pool modest to avoid heavy compute
        int candidateLimit = 100;
        params.put("limit", candidateLimit);
        params.put("offset", 0);

        String sql = """
            SELECT c.*, ce.embedding, cate.name as category_name,
                   (ce.embedding <-> (:vector)::vector) AS distance
            FROM courses c
            INNER JOIN course_embedding ce ON c.id = ce.course_id
            INNER JOIN categories cate ON c.category_id = cate.id
            """ + whereClause + """
            ORDER BY ce.embedding <-> (:vector)::vector
            LIMIT :limit OFFSET :offset
            """;

        return new NamedParameterJdbcTemplate(jdbcTemplate)
                .query(sql, params, new CourseRowMapper());
    }

    private List<CourseDto> hybridSearch(List<CourseDto> candidates, String originalQuery, String expandedQuery) {
        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            for (CourseDto course : candidates) {
                float quality = calculateQuality(course);
                course.setRelevanceScore(quality);
            }
            candidates.sort((c1, c2) -> Float.compare(c2.getRelevanceScore(), c1.getRelevanceScore()));
            return candidates;
        }
        
        String kw = normalizeText(originalQuery);
        String kwExpanded = normalizeText(expandedQuery);
        
        for (CourseDto course : candidates) {
            // (1) Vector Semantic Similarity
            double distance = course.getDistance();
            float vectorScore = (float) Math.max(0.0, Math.min(1.0, 1.0 - distance));
            
            // (2) BM25-like Lexical Score
            float bm25Score = calculateBM25Score(kw, kwExpanded, course);
            
            // (3) Quality Score
            float quality = calculateQuality(course);
            
            // Hybrid Search Blending: Vector (50%) + BM25 (35%) + Quality (15%)
            float finalScore = (vectorScore * 0.50f) + (bm25Score * 0.35f) + (quality * 0.15f);
            course.setRelevanceScore(Math.max(0f, Math.min(1f, finalScore)));
        }

        candidates.sort((c1, c2) -> Float.compare(c2.getRelevanceScore(), c1.getRelevanceScore()));
        return candidates;
    }

    private List<CourseDto> hybridSearchWithRerank(List<CourseDto> candidates, String originalQuery, String expandedQuery) {
        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            return hybridSearch(candidates, originalQuery, expandedQuery);
        }

        // Limit rerank to top-K nearest by vector (reduce to 30 for speed)
        int topRerank = Math.min(candidates.size(), 30);
        List<String> documents = candidates.subList(0, topRerank).stream()
                .map(this::buildCourseText)
                .collect(java.util.stream.Collectors.toList());

        List<ScoredDocument> scored = rerankerService.batchRerank(expandedQuery.trim(), documents);
        if (scored == null || scored.isEmpty()) {
            return hybridSearch(candidates, originalQuery, expandedQuery);
        }
        Map<Integer, Float> scoreMap = scored.stream()
            .collect(java.util.stream.Collectors.toMap(ScoredDocument::getIndex, ScoredDocument::getScore));

        String kw = normalizeText(originalQuery);
        String kwExpanded = normalizeText(expandedQuery);
        
        for (int i = 0; i < candidates.size(); i++) {
            CourseDto course = candidates.get(i);
            double distance = course.getDistance();
            float vectorScore = (float) Math.max(0.0, Math.min(1.0, 1.0 - distance));

            float rerankScore = i < topRerank ? scoreMap.getOrDefault(i, vectorScore) : vectorScore;
            float bm25Score = calculateBM25Score(kw, kwExpanded, course);
            float quality = calculateQuality(course);

            // Exact title match gets priority
            String title = course.getName() == null ? "" : normalizeText(course.getName());
            if (!title.isEmpty() && title.equals(kw)) {
                course.setRelevanceScore(0.99f);
                continue;
            }

            // Hybrid with Rerank: Rerank (55%) + Vector (20%) + BM25 (15%) + Quality (10%)
            float finalScore = (rerankScore * 0.55f) + (vectorScore * 0.20f) + (bm25Score * 0.15f) + (quality * 0.10f);
            course.setRelevanceScore(Math.max(0f, Math.min(1f, finalScore)));
        }

        candidates.sort((c1, c2) -> Float.compare(c2.getRelevanceScore(), c1.getRelevanceScore()));
        return candidates;
    }

    // Query expansion for semantic understanding
    private String expandQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "";
        }
        
        String normalized = normalizeText(query);
        Map<String, String> expansions = new HashMap<>();
        
        // Vietnamese programming terms
        expansions.put("lap trinh", "lap trinh code coding programming phat trien");
        expansions.put("thiet ke", "thiet ke design ui ux");
        expansions.put("du lieu", "du lieu data database sql");
        expansions.put("web", "web website frontend backend fullstack");
        expansions.put("mobile", "mobile app android ios");
        expansions.put("ai", "ai machine learning ml deep learning");
        expansions.put("python", "python programming coding");
        expansions.put("java", "java programming coding spring");
        expansions.put("javascript", "javascript js typescript react vue angular");
        
        // Check for expansion matches
        for (Map.Entry<String, String> entry : expansions.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return query + " " + entry.getValue();
            }
        }
        
        return query;
    }
    
    // BM25-like lexical scoring for hybrid search
    private float calculateBM25Score(String queryNorm, String expandedQueryNorm, CourseDto course) {
        String title = course.getName() == null ? "" : normalizeText(course.getName());
        String category = course.getCategoryName() == null ? "" : normalizeText(course.getCategoryName());
        String desc = course.getDescription() == null ? "" : normalizeText(course.getDescription());
        
        float score = 0.0f;
        
        // Exact match gets highest score
        if (!title.isEmpty() && title.equals(queryNorm)) {
            return 1.0f;
        }
        
        // Title matches (weighted heavily)
        if (!title.isEmpty()) {
            if (title.startsWith(queryNorm) || title.endsWith(queryNorm)) {
                score += 0.50f;
            } else if (title.contains(queryNorm)) {
                score += 0.30f;
            }
            
            // Check for expanded terms
            String[] expandedTerms = expandedQueryNorm.split(" ");
            int matchCount = 0;
            for (String term : expandedTerms) {
                if (!term.isBlank() && title.contains(term)) {
                    matchCount++;
                }
            }
            if (expandedTerms.length > 0) {
                score += (float) matchCount / expandedTerms.length * 0.20f;
            }
        }
        
        // Category matches
        if (!category.isEmpty() && category.contains(queryNorm)) {
            score += 0.15f;
        }
        
        // Description matches (lower weight)
        if (!desc.isEmpty() && desc.contains(queryNorm)) {
            score += 0.10f;
        }
        
        // Token overlap boost
        score += computeTokenOverlapBoost(queryNorm, title) * 0.5f;
        
        return Math.min(1.0f, score);
    }

    // Normalize for multilingual/Vietnamese: lower-case, strip accents, collapse spaces
    private String normalizeText(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase().trim();
        String noAccent = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return noAccent.replaceAll("\\s+", " ");
    }

    // Compute lexical coverage: proportion of query tokens found in title tokens
    private float computeTokenOverlapBoost(String queryNorm, String titleNorm) {
        if (queryNorm.isEmpty() || titleNorm.isEmpty()) return 0f;
        String[] qTokens = queryNorm.split(" ");
        String[] tTokens = titleNorm.split(" ");
        java.util.Set<String> tSet = new java.util.HashSet<>();
        for (String t : tTokens) {
            if (!t.isBlank()) tSet.add(t);
        }
        int hits = 0, total = 0;
        for (String q : qTokens) {
            if (q.isBlank()) continue;
            total++;
            if (tSet.contains(q)) hits++;
        }
        if (total == 0) return 0f;
        float coverage = (float) hits / (float) total; // 0..1
        // Scale to reasonable boost; cap at 0.12
        return Math.min(0.12f, coverage * 0.12f);
    }

    private String buildCourseText(CourseDto course) {
        StringBuilder sb = new StringBuilder();
        
        // Ưu tiên Name và Category
        if (course.getName() != null) sb.append(course.getName()).append(" | ");
        if (course.getCategoryName() != null) sb.append(course.getCategoryName()).append(" | ");
        
        // Description kept concise; shorter helps reranker latency
        if (course.getDescription() != null) {
            String desc = course.getDescription();
            if (desc.length() > 500) {
                desc = desc.substring(0, 500);
            }
            sb.append(desc);
        }
        
        return sb.toString().trim();
    }

    private float calculateQuality(CourseDto course) {
        float score = 0;
        
        if (course.getRating() > 0) {
            score += (course.getRating() / 5.0) * 0.6;
        }
        
        if (course.getTotalStudent() > 0) {
            double normalized = Math.log(course.getTotalStudent() + 1) / Math.log(10000);
            score += Math.min(normalized, 1.0) * 0.4;
        }
        
        return score;
    }

    private List<CourseDto> paginate(List<CourseDto> courses, int page, int size) {
        int start = page * size;
        int end = Math.min(start + size, courses.size());

        if (start >= courses.size()) {
            return Collections.emptyList();
        }

        return courses.subList(start, end);
    }
}

