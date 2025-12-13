package hcmute.vina.vectorsearchservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

import hcmute.vina.vectorsearchservice.enums.CourseLevel;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CourseTransfer {
	private UUID courseId;
	private String instructorName;
	private String description;
	private String courseName;
	private String categoryName;
	private BigDecimal price;
}
