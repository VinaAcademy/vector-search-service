package hcmute.vina.vectorsearchservice.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import lombok.Value;
import vn.vinaacademy.kafka.event.CourseEmbeddedEvent;

@Configuration
public class KafkaConsumerConfig {

    
//    private String bootstrapServers = "localhost:9092";
//
//    @Bean
//    public ConsumerFactory<String, CourseEmbeddedEvent> consumerFactory() {
//        JsonDeserializer<CourseEmbeddedEvent> deserializer = new JsonDeserializer<>(CourseEmbeddedEvent.class);
//        deserializer.addTrustedPackages("com.vinaacademy.platform.feature.course.dto");
//
//        Map<String, Object> props = new HashMap<>();
//        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//        props.put(ConsumerConfig.GROUP_ID_CONFIG, "vector-group");
//        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
//
//        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
//    }
//
//    @Bean
//    public ConcurrentKafkaListenerContainerFactory<String, CourseEmbeddedEvent> kafkaListenerContainerFactory() {
//        ConcurrentKafkaListenerContainerFactory<String, CourseEmbeddedEvent> factory =
//                new ConcurrentKafkaListenerContainerFactory<>();
//        factory.setConsumerFactory(consumerFactory());
//        return factory;
//    }
}
