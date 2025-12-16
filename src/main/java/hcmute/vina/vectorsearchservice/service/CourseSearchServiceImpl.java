package hcmute.vina.vectorsearchservice.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import hcmute.vina.vectorsearchservice.builder.CourseSqlBuilder;
import hcmute.vina.vectorsearchservice.dto.CourseDto;
import hcmute.vina.vectorsearchservice.dto.request.CourseSearchRequest;
import hcmute.vina.vectorsearchservice.mapper.CourseRowMapper;
import hcmute.vina.vectorsearchservice.service.rerank.BgeRerankerService;
import hcmute.vina.vectorsearchservice.service.rerank.JinaRerankerService;
import hcmute.vina.vectorsearchservice.service.rerank.ScoredDocument;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourseSearchServiceImpl implements CourseSearchService{

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final BgeRerankerService bge;
    private final JinaRerankerService rerankerService; // Jina API reranker (multilingual)

    @Value("${search.rerank.enabled:true}")
    private boolean rerankEnabledDefault;

    @Override
    public Page<CourseDto> search(CourseSearchRequest req, int page, int size) {
        long current = System.currentTimeMillis();
        
        // 1. Retrieve candidates using original query (no expansion)
        List<CourseDto> candidates = retrieveCandidates(req, req.getKeyword());
        System.err.println("vector search time " + (System.currentTimeMillis() - current));
        
        if (candidates.isEmpty()) {
            return Page.empty(PageRequest.of(page, size));
        }
 
        // 2. Hybrid search: combine vector + BM25 + rerank
        // Use request parameter if provided, otherwise use config default
        boolean useSemanticRerank = req.getSemantic() != null ? req.getSemantic() : rerankEnabledDefault;
        boolean useRerank = useSemanticRerank && rerankerService != null && rerankerService.isAvailable() && page == 0;
        List<CourseDto> rankedCandidates = useRerank
            ? hybridSearchWithRerank(candidates, req.getKeyword())
            : hybridSearch(candidates, req.getKeyword());

        // 3. Paginate results
        return paginate(rankedCandidates, page, size);
    }

    private List<CourseDto> retrieveCandidates(CourseSearchRequest req, String expandedQuery) {
        Map<String, Object> params = new HashMap<>();
        String whereClause = CourseSqlBuilder.buildWhere(req, params);

        String keyword = expandedQuery.trim().toLowerCase();
        if (keyword == null || keyword.isEmpty()) {
            keyword = "";
        }
        long current = System.currentTimeMillis();
        float[] embedding = embeddingService.toFloatArray(
                embeddingService.createEmbedding(keyword)
        );
//        float[] embedding = 
//                embeddingService.createEmbedding3(keyword.trim());
        System.err.println("embedding time "+(System.currentTimeMillis()-current));
        params.put("vector", embedding);
        // Keep candidate pool modest to avoid heavy compute
        int candidateLimit = 100;
        params.put("limit", candidateLimit);
        params.put("offset", 0);
        System.err.println(whereClause);
        
        String sql = """
            SELECT c.*, ce.embedding, cate.slug as category_slug, u.full_name as instructor_name, cate.name as category_name,
                   (ce.embedding <-> (:vector)::vector) AS distance
            FROM courses c
            INNER JOIN course_embedding ce ON c.id = ce.course_id
            INNER JOIN categories cate ON c.category_id = cate.id
            INNER JOIN course_instructor ci
        			ON c.id = ci.course_id
            INNER JOIN users u
        			ON ci.user_id = u.id
        	 
            """ + whereClause + """
             ORDER BY ce.embedding <-> (:vector)::vector
            LIMIT :limit OFFSET :offset
            """;

        return new NamedParameterJdbcTemplate(jdbcTemplate)
                .query(sql, params, new CourseRowMapper());
    }

    private List<CourseDto> hybridSearch(List<CourseDto> candidates, String originalQuery) {
        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            for (CourseDto course : candidates) {
                float quality = calculateQuality(course);
                course.setRelevanceScore(quality);
            }
            candidates.sort((c1, c2) -> Float.compare(c2.getRelevanceScore(), c1.getRelevanceScore()));
            return candidates;
        }
        
        String kw = normalizeText(originalQuery);
        
        for (CourseDto course : candidates) {
            // (1) Vector Semantic Similarity
            double distance = course.getDistance();
            float vectorScore = (float) Math.max(0.0, Math.min(1.0, 1.0 - distance));
            
            // (2) BM25-like Lexical Score
            float bm25Score = calculateBM25Score(kw, course);
            
            // (3) Quality Score
            float quality = calculateQuality(course);
            
            // Hybrid Search Blending: Vector (50%) + BM25 (35%) + Quality (15%)
            float finalScore = (vectorScore * 0.50f) + (bm25Score * 0.35f) + (quality * 0.15f);
            course.setRelevanceScore(Math.max(0f, Math.min(1f, finalScore)));
        }

        candidates.sort((c1, c2) -> Float.compare(c2.getRelevanceScore(), c1.getRelevanceScore()));
        return candidates;
    }

    private List<CourseDto> hybridSearchWithRerank(List<CourseDto> candidates, String originalQuery) {

        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            return hybridSearch(candidates, originalQuery);
        }

        int topRerank = Math.min(candidates.size(), 20);

       
        List<String> documents = candidates.subList(0, topRerank).stream()
                .map(this::buildCourseText)
                .toList();
        long current = System.currentTimeMillis();
        List<ScoredDocument> scored = rerankerService.batchRerank(originalQuery.trim(), documents);
        System.err.println("rerank time "+(System.currentTimeMillis()-current));
        if (scored == null || scored.isEmpty()) {
            return hybridSearch(candidates, originalQuery);
        }

        Map<Integer, Float> rerankScoreMap = scored.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ScoredDocument::getIndex,
                        ScoredDocument::getScore
                ));

        String kw = normalizeText(originalQuery);

        
        List<CourseDto> top = candidates.subList(0, topRerank);
        for (int i = 0; i < top.size(); i++) {
            CourseDto course = top.get(i);

            float vectorScore = (float) Math.max(0.0, Math.min(1.0, 1.0 - course.getDistance()));
            float bm25Score = calculateBM25Score(kw, course);
            float quality = calculateQuality(course);

            float rerankScore = rerankScoreMap.getOrDefault(i, 0f); 

            float finalScore =
                    (rerankScore * 0.55f) +
                    (vectorScore * 0.25f) +
                    (bm25Score * 0.10f) +
                    (quality * 0.10f);

            course.setRelevanceScore(finalScore);
        }

        top.sort((c1, c2) -> Float.compare(
                c2.getRelevanceScore(),
                c1.getRelevanceScore()
        ));

        List<CourseDto> rest = candidates.subList(topRerank, candidates.size());

        for (CourseDto course : rest) {
            float vectorScore = (float) Math.max(0.0, Math.min(1.0, 1.0 - course.getDistance()));
            float bm25Score = calculateBM25Score(kw, course);
            float quality = calculateQuality(course);

            float finalScore =
                    (vectorScore * 0.70f) +
                    (bm25Score * 0.20f) +
                    (quality * 0.10f);

            course.setRelevanceScore(finalScore);
        }

        // ===== 5. Merge result (tiered ranking) =====
        List<CourseDto> result = new java.util.ArrayList<>(candidates.size());
        result.addAll(top);
        result.addAll(rest);

        return result;
    }


    // BM25-like lexical scoring for hybrid search
    private float calculateBM25Score(String queryNorm, CourseDto course) {
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

    // Normalize text: lower-case and collapse spaces only (preserve Vietnamese diacritics)
    private String normalizeText(String s) {
        if (s == null) return "";
        return s.toLowerCase().trim().replaceAll("\\s+", " ");
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
        // Description kept concise; shorter helps reranker latency
        if (course.getDescription() != null) {
            String desc = course.getDescription();
            if (desc.length() > 500) {
                desc = desc.substring(0, 500);
            }
            
            sb.append(embeddingService.cleanHtml(desc));
        }
        if (course.getInstructorName() != null) sb.append(course.getInstructorName()).append(" | ");
        if (course.getCategoryName() != null) sb.append(course.getCategoryName()).append(" | ");
        if (course.getPrice() != null) sb.append(course.getPrice().toPlainString()).append(" | ");

        
        return sb.toString().trim();
    }

    private float calculateQuality(CourseDto course) {
        float score = 0f;

        // Enhance course if rating good
        if (course.getRating() > 0) {
            score += (course.getRating() / 5.0f) * 0.01f;
        }

        // (log-scaled, max ~0.004)
        if (course.getTotalStudent() > 10) {
            double normalized = Math.log(course.getTotalStudent()) / Math.log(10000);
            score += Math.min((float) normalized, 1.0f) * 0.004f;
        }

        return score;
    }


    private Page<CourseDto> paginate(List<CourseDto> courses, int page, int size) {
        int start = page * size;
        int end = Math.min(start + size, courses.size());

        if (start >= courses.size()) {
            return Page.empty(PageRequest.of(page, size));
        }

        List<CourseDto> pageContent = courses.subList(start, end);
        return new PageImpl<>(pageContent, PageRequest.of(page, size), courses.size());
    }
}

