package hcmute.vina.vectorsearchservice.controller;
import org.springframework.web.bind.annotation.*;

import hcmute.vina.vectorsearchservice.dto.CourseDto;
import hcmute.vina.vectorsearchservice.dto.request.CourseSearchRequest;
import hcmute.vina.vectorsearchservice.service.CourseSearchService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseSearchService courseSearchService;


    /**
     * Tìm kiếm vector courses theo keyword và filter request
     */
    @GetMapping("/search")
    public ResponseEntity<List<CourseDto>> searchCourses(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "2") int size,
            @ModelAttribute CourseSearchRequest req 
    ) {
        // Gán keyword từ param nếu có
        if (keyword != null && !keyword.isEmpty()) {
            req.setKeyword(keyword);
        }

        List<CourseDto> results = courseSearchService.search(req, page, size);
        return ResponseEntity.ok(results);
    }
}
