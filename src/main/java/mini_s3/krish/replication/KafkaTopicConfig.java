package mini_s3.krish.replication;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String REPLICATION_TOPIC = "mini-s3-replication";
    public static final String HEARTBEAT_TOPIC   = "mini-s3-heartbeat";

    @Bean
    public NewTopic replicationTopic() {
        return TopicBuilder.name(REPLICATION_TOPIC)
                .partitions(3)      // one partition per node
                .replicas(1)        // single broker locally
                .build();
    }

    @Bean
    public NewTopic heartbeatTopic() {
        return TopicBuilder.name(HEARTBEAT_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
