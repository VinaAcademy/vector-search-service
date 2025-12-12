package hcmute.vina.vectorsearchservice.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CourseTransfer {
	private UUID courseId;
	private String instructorName;
	private String description;
	private String courseName;
}
