package hcmute.vina.vectorsearchservice.service;

import java.util.List;

import hcmute.vina.vectorsearchservice.dto.CourseTransfer;

public interface EmbeddingService {
	
	List<Float> createEmbedding(String text);
	float[] createEmbedding3(String text);
	String toPgVector(List<Double> vector);
	List<CourseTransfer> getCoursesForEmbedding();
	void migrateAllCourse();
	float[] toFloatArray(List<Float> list);

}
