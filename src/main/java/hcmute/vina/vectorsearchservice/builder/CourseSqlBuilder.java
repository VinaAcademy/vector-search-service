package hcmute.vina.vectorsearchservice.builder;

import hcmute.vina.vectorsearchservice.dto.request.CourseSearchRequest;
import hcmute.vina.vectorsearchservice.enums.CourseStatus;

import java.util.Map;

public class CourseSqlBuilder {
	
    public static String buildWhere(CourseSearchRequest req, Map<String, Object> params) {
    	
        StringBuilder sb = new StringBuilder(" WHERE");
        
        // STATUS = PUBLISHED
        sb.append(" c.status = :status");
        params.put("status", CourseStatus.PUBLISHED.name());
                
        // KEYWORD (search in name + description)
//        if (has(req.getKeyword())) {
//            sb.append(" AND (LOWER(c.name) LIKE LOWER(:kw) OR LOWER(c.description) LIKE LOWER(:kw)) ");
//            params.put("kw", "%" + req.getKeyword().trim() + "%");
//        }

        // SINGLE CATEGORY
        if (has(req.getCategorySlug())) {
            sb.append("""
                AND cate.slug = :categorySlug
            """);
            params.put("categorySlug", req.getCategorySlug());
        }

        // MULTIPLE CATEGORIES (IN)
        if (req.getCategorieSlugs() != null && !req.getCategorieSlugs().isEmpty()) {
            sb.append(" AND cate.slug = ANY(:categorySlugs) ");
            params.put("categorySlugs", req.getCategorieSlugs().toArray(new String[0]));
        }

        // LEVEL
        if (has(req.getLevel())) {
            sb.append(" AND c.level = :level ");
            params.put("level", req.getLevel());
        }

        // LANGUAGE
        if (has(req.getLanguage())) {
            sb.append(" AND c.language = :language ");
            params.put("language", req.getLanguage());
        }

        // MIN PRICE
        if (req.getMinPrice() != null) {
            sb.append(" AND c.price >= :minPrice ");
            params.put("minPrice", req.getMinPrice());
        }

        // MAX PRICE
        if (req.getMaxPrice() != null) {
            sb.append(" AND c.price <= :maxPrice ");
            params.put("maxPrice", req.getMaxPrice());
        }

        // MIN RATING
        if (req.getMinRating() != null) {
            sb.append(" AND c.rating >= :minRating ");
            params.put("minRating", req.getMinRating());
        }

        // INSTRUCTOR ID
        if (req.getInstructorId() != null) {
            sb.append(" AND u.id = :instructorId ");
            params.put("instructorId", req.getInstructorId());
        }
        
        return sb.toString();
    }

    private static boolean has(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof String str) {
            return !str.trim().isEmpty();
        }
        return true;
    }
}
