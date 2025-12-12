package hcmute.vina.vectorsearchservice.service;

import java.util.List;

import hcmute.vina.vectorsearchservice.dto.CourseDto;
import hcmute.vina.vectorsearchservice.dto.request.CourseSearchRequest;

public interface CourseSearchService {
	List<CourseDto> search(CourseSearchRequest req, int page, int size);
}
