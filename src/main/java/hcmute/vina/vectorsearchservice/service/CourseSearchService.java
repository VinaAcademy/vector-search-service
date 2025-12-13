package hcmute.vina.vectorsearchservice.service;

import org.springframework.data.domain.Page;

import hcmute.vina.vectorsearchservice.dto.CourseDto;
import hcmute.vina.vectorsearchservice.dto.request.CourseSearchRequest;

public interface CourseSearchService {
	Page<CourseDto> search(CourseSearchRequest req, int page, int size);
}
