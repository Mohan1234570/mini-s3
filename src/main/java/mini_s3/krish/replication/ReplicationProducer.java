package mini_s3.krish.replication;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReplicationProducer {

    private final KafkaTemplate<String, ReplicationEvent> kafkaTemplate;

    public void publishReplicationEvent(String bucketName,
                                        String objectKey,
                                        String sourcePath,
                                        String primaryNodeId,
                                        String targetNodeId,
                                        long fileSize,
                                        String etag,
                                        ReplicationEvent.EventType eventType) {

        ReplicationEvent event = ReplicationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bucketName(bucketName)
                .objectKey(objectKey)
                .sourcePath(sourcePath)
                .primaryNodeId(primaryNodeId)
                .targetNodeId(targetNodeId)
                .fileSize(fileSize)
                .etag(etag)
                .eventType(eventType)
                .createdAt(LocalDateTime.now())
                .build();

        // Use objectKey as partition key — same object always goes
        // to same partition — preserves ordering per object
        kafkaTemplate.send(KafkaTopicConfig.REPLICATION_TOPIC,
                bucketName + "/" + objectKey, event);

        log.info("Published {} event: {}/{} → node {}",
                eventType, bucketName, objectKey, targetNodeId);
    }
}
