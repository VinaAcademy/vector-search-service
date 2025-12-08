package hcmute.vina.vectorsearchservice.mapper;

import hcmute.vina.vectorsearchservice.dto.CourseDto;
import hcmute.vina.vectorsearchservice.enums.CourseLevel;
import hcmute.vina.vectorsearchservice.enums.CourseStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class CourseRowMapper implements RowMapper<CourseDto> {

    @Override
    public CourseDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        CourseDto courseDto = new CourseDto();

        courseDto.setId(UUID.fromString(rs.getString("id")));
        courseDto.setImage(rs.getString("image"));
        courseDto.setName(rs.getString("name"));
        courseDto.setDescription(rs.getString("description"));
        courseDto.setSlug(rs.getString("slug"));
        courseDto.setPrice(rs.getBigDecimal("price"));

        courseDto.setLevel(CourseLevel.valueOf(rs.getString("level")));
        courseDto.setStatus(CourseStatus.valueOf(rs.getString("status")));

        courseDto.setLanguage(rs.getString("language"));
        courseDto.setCategoryName(rs.getString("category_name"));

        courseDto.setRating(rs.getDouble("rating"));
        courseDto.setTotalRating(rs.getLong("total_rating"));
        courseDto.setTotalStudent(rs.getLong("total_student"));
        courseDto.setTotalSection(rs.getLong("total_section"));
        courseDto.setTotalLesson(rs.getLong("total_lesson"));

        return courseDto;
    }
}
