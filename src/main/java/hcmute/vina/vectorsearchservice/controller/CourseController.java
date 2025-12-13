package hcmute.vina.vectorsearchservice.controller;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import hcmute.vina.vectorsearchservice.dto.CourseDto;
import hcmute.vina.vectorsearchservice.dto.request.CourseSearchRequest;
import hcmute.vina.vectorsearchservice.service.CourseSearchService;
import lombok.RequiredArgsConstructor;
import vn.vinaacademy.common.response.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseSearchService courseSearchService;


    /**
     * Tìm kiếm vector courses theo keyword và filter request
     */
    @GetMapping("/search")
    public ApiResponse<Page<CourseDto>> searchCourses(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
              @RequestParam(defaultValue = "9") int size,
          @RequestParam(required = false) Boolean semantic,
            @ModelAttribute CourseSearchRequest req 
    ) {
        // Gán keyword từ param nếu có
        if (keyword != null && !keyword.isEmpty()) {
            req.setKeyword(keyword);
        }
        // Gán semantic từ param nếu có
        if (semantic != null) {
            req.setSemantic(semantic);
        }

        return ApiResponse.success(courseSearchService.search(req, page, size));
    }
}
