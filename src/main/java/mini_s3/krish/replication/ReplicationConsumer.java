package mini_s3.krish.replication;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mini_s3.krish.object.config.StorageProperties;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReplicationConsumer {

    private final StorageProperties storageProperties;
    private final ReplicationStateRepository replicationStateRepository;

    @KafkaListener(
            topics = KafkaTopicConfig.REPLICATION_TOPIC,
            groupId = "mini-s3-replication",
            containerFactory = "replicationKafkaListenerContainerFactory"
    )
    public void handleReplicationEvent(ReplicationEvent event) {
        log.info("Received {} event for {}/{}",
                event.getEventType(),
                event.getBucketName(),
                event.getObjectKey());

        try {
            switch (event.getEventType()) {
                case REPLICATE, RE_REPLICATE -> replicateObject(event);
                case DELETE                  -> deleteReplica(event);
            }
            saveReplicationState(event, ReplicationState.Status.COMPLETED);
        } catch (Exception e) {
            log.error("Replication failed for {}/{}: {}",
                    event.getBucketName(), event.getObjectKey(), e.getMessage());
            saveReplicationState(event, ReplicationState.Status.FAILED);
        }
    }

    private void replicateObject(ReplicationEvent event) throws IOException {
        Path source = Paths.get(event.getSourcePath());
        if (!Files.exists(source)) {
            throw new IllegalStateException(
                    "Source file not found: " + source);
        }

        // objectKey may contain slashes e.g. "images/profile.png"
        // so we must resolve the full path and create ALL parent dirs
        Path replicaBase = Paths.get(
                storageProperties.getBasePath(),
                "replicas",
                event.getTargetNodeId(),
                event.getBucketName());

        // This handles nested keys like "images/profile.png" correctly
        Path destination = replicaBase.resolve(
                Paths.get(event.getObjectKey()));

        // Create ALL parent directories including "images/" subfolder
        Files.createDirectories(destination.getParent());

        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);

        log.info("Replicated {}/{} to node {} ({}B)",
                event.getBucketName(), event.getObjectKey(),
                event.getTargetNodeId(), event.getFileSize());
    }

    private void deleteReplica(ReplicationEvent event) throws IOException {
        Path replicaPath = Paths.get(
                storageProperties.getBasePath(),
                "replicas",
                event.getTargetNodeId(),
                event.getBucketName(),
                event.getObjectKey());
        Files.deleteIfExists(replicaPath);
        log.info("Deleted replica {}/{} from node {}",
                event.getBucketName(), event.getObjectKey(),
                event.getTargetNodeId());
    }

    private void saveReplicationState(ReplicationEvent event,
                                      ReplicationState.Status status) {
        ReplicationState state = ReplicationState.builder()
                .eventId(event.getEventId())
                .bucketName(event.getBucketName())
                .objectKey(event.getObjectKey())
                .primaryNodeId(event.getPrimaryNodeId())
                .targetNodeId(event.getTargetNodeId())
                .status(status)
                .build();
        replicationStateRepository.save(state);
    }
}
