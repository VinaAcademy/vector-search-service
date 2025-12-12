package hcmute.vina.vectorsearchservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import vn.vinaacademy.kafka.KafkaConsumerConfig;

@Configuration
@Import(KafkaConsumerConfig.class)
public class AppConfig {
}