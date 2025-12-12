package hcmute.vina.vectorsearchservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import hcmute.vina.vectorsearchservice.entity.CourseEmbedding;

public interface CourseEmbeddingRepository extends JpaRepository<CourseEmbedding, UUID> {
}
