package hcmute.vina.vectorsearchservice.listener;

import java.util.Optional;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import hcmute.vina.vectorsearchservice.constant.KafkaTopic;
import hcmute.vina.vectorsearchservice.entity.CourseEmbedding;
import hcmute.vina.vectorsearchservice.repository.CourseEmbeddingRepository;
import hcmute.vina.vectorsearchservice.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.vinaacademy.common.exception.BadRequestException;
import vn.vinaacademy.kafka.event.CourseEmbeddedEvent;


@Slf4j
@Component
@RequiredArgsConstructor
public class CourseSearchEventListener {
    private final EmbeddingService embeddingService;
    private final CourseEmbeddingRepository courseEmbeddingRepository;

    @KafkaListener(topics = KafkaTopic.VECTOR_TOPIC, groupId = "${spring.kafka.consumer.group-id:vector-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleNotificationEvent(CourseEmbeddedEvent courseEmbeddedEvent, Acknowledgment acknowledgment) {
        try {
            if (courseEmbeddedEvent == null || courseEmbeddedEvent.getId() == null) {
                throw new RuntimeException("CourseEmbeddedEvent is null or missing id");
            }

            String text = String.join(" | ",
                    nullToEmpty(courseEmbeddedEvent.getTitle()),
                    nullToEmpty(courseEmbeddedEvent.getDescription()),
                    nullToEmpty(courseEmbeddedEvent.getInstructorName()),
                    nullToEmpty(courseEmbeddedEvent.getCategoryName()),
                    nullToEmpty(courseEmbeddedEvent.getPrice().toPlainString())
            );

            float[] vector = embeddingService.toFloatArray(embeddingService.createEmbedding(text));

            Optional<CourseEmbedding> existing = courseEmbeddingRepository.findById(courseEmbeddedEvent.getId());

            CourseEmbedding embedding;
            if (existing.isPresent()) {
                embedding = existing.get();
                embedding.setEmbedding(vector); 
            } else {
                embedding = CourseEmbedding.builder()
                        .courseId(courseEmbeddedEvent.getId())
                        .embedding(vector)
                        .build();
            }

            courseEmbeddingRepository.save(embedding);


            log.info("Saved embedding for course {}", courseEmbeddedEvent.getId());

            acknowledgment.acknowledge();
        } catch (BadRequestException e) {
            log.error("Error processing search event: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing search event: {}", e.getMessage(), e);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}