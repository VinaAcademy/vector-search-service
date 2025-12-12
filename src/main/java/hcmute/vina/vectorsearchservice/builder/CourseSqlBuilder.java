package hcmute.vina.vectorsearchservice.builder;

import hcmute.vina.vectorsearchservice.dto.request.CourseSearchRequest;
import hcmute.vina.vectorsearchservice.enums.CourseStatus;

import java.util.Map;

public class CourseSqlBuilder {
	
    public static String buildWhere(CourseSearchRequest req, Map<String, Object> params) {
    	
        StringBuilder sb = new StringBuilder(" WHERE");
        
        // STATUS = PUBLISHED
        sb.append(" status = :status ");
        params.put("status", CourseStatus.PUBLISHED.name());
                
        // KEYWORD (search in name + description)
//        if (has(req.getKeyword())) {
//            sb.append(" AND (LOWER(name) LIKE LOWER(:kw) OR LOWER(description) LIKE LOWER(:kw)) ");
//            params.put("kw", "%" + req.getKeyword().trim() + "%");
//        }

        // SINGLE CATEGORY
        if (has(req.getCategorySlug())) {
            sb.append("""
                AND category_slug = :categorySlug
            """);
            params.put("categorySlug", req.getCategorySlug());
        }

        // MULTIPLE CATEGORIES (IN)
        if (req.getCategorieSlugs() != null && !req.getCategorieSlugs().isEmpty()) {
            sb.append(" AND category_slug = ANY(:categorySlugs) ");
            params.put("categorySlugs", req.getCategorieSlugs().toArray(new String[0]));
        }

        // LEVEL
        if (has(req.getLevel())) {
            sb.append(" AND level = :level ");
            params.put("level", req.getLevel());
        }

        // LANGUAGE
        if (has(req.getLanguage())) {
            sb.append(" AND language = :language ");
            params.put("language", req.getLanguage());
        }

        // MIN PRICE
        if (req.getMinPrice() != null) {
            sb.append(" AND price >= :minPrice ");
            params.put("minPrice", req.getMinPrice());
        }

        // MAX PRICE
        if (req.getMaxPrice() != null) {
            sb.append(" AND price <= :maxPrice ");
            params.put("maxPrice", req.getMaxPrice());
        }

        // MIN RATING
        if (req.getMinRating() != null) {
            sb.append(" AND rating >= :minRating ");
            params.put("minRating", req.getMinRating());
        }

        // INSTRUCTOR ID
        if (req.getInstructorId() != null) {
            sb.append(" AND instructor_id = :instructorId ");
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
