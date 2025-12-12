package hcmute.vina.vectorsearchservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import hcmute.vina.vectorsearchservice.constant.KafkaTopic;

@Configuration
public class KafkaTopicConfig {
	
    @Bean
    public NewTopic createVectorTopic() {
        return TopicBuilder.name(KafkaTopic.VECTOR_TOPIC)
                .build();
    }
}