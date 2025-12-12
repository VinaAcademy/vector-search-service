package hcmute.vina.vectorsearchservice.dto;


import hcmute.vina.vectorsearchservice.enums.CourseLevel;
import hcmute.vina.vectorsearchservice.enums.CourseStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = false)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDto extends BaseDto {

    private UUID id;

    private String image;

    private String name;

    private String description;

    private String slug;

    private BigDecimal price;

    private CourseLevel level;

    private CourseStatus status;

    private String language;

    private String categoryName;

    private double rating;

    private long totalRating;

    private long totalStudent;

    private long totalSection;

    private long totalLesson;
    
    private float relevanceScore;

    // Distance from query embedding (cosine distance: 1 - cosine_similarity)
    private double distance;

}
