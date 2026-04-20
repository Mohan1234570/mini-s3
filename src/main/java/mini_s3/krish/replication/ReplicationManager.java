package mini_s3.krish.replication;



import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mini_s3.krish.router.ConsistentHashRouter;
import mini_s3.krish.router.StorageNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplicationManager {

    private final ConsistentHashRouter router;
    private final ReplicationProducer producer;

    @Value("${replication.factor:3}")
    private int replicationFactor;

    /**
     * Called after a successful object write to primary node.
     * Publishes replication events to Kafka for all replica nodes.
     */
    public void replicateObject(String bucketName,
                                String objectKey,
                                String sourcePath,
                                String primaryNodeId,
                                long fileSize,
                                String etag) {

        // Get all replica nodes (excludes primary)
        List<StorageNode> allNodes = router.getReplicaNodes(
                bucketName + "/" + objectKey, replicationFactor);

        List<StorageNode> replicaNodes = allNodes.stream()
                .filter(n -> !n.getNodeId().equals(primaryNodeId))
                .toList();

        if (replicaNodes.isEmpty()) {
            log.warn("No replica nodes available for {}/{}",
                    bucketName, objectKey);
            return;
        }

        // Publish one replication event per replica node
        for (StorageNode replica : replicaNodes) {
            producer.publishReplicationEvent(
                    bucketName, objectKey, sourcePath,
                    primaryNodeId, replica.getNodeId(),
                    fileSize, etag,
                    ReplicationEvent.EventType.REPLICATE);
        }

        log.info("Queued replication of {}/{} to {} replica nodes",
                bucketName, objectKey, replicaNodes.size());
    }

    /**
     * Called when a node comes back online.
     * Re-replicates any objects that were on the failed node.
     */
    public void reReplicateForNode(String recoveredNodeId,
                                   List<ReplicationState> missedReplications) {
        for (ReplicationState state : missedReplications) {
            // Find a healthy node that has the object to use as source
            List<StorageNode> healthyNodes = router.getHealthyNodes()
                    .stream()
                    .filter(n -> !n.getNodeId().equals(recoveredNodeId))
                    .toList();

            if (healthyNodes.isEmpty()) continue;

            StorageNode sourceNode = healthyNodes.get(0);

            producer.publishReplicationEvent(
                    state.getBucketName(),
                    state.getObjectKey(),
                    buildReplicaPath(sourceNode.getNodeId(),
                            state.getBucketName(),
                            state.getObjectKey()),
                    sourceNode.getNodeId(),
                    recoveredNodeId,
                    0L, "",
                    ReplicationEvent.EventType.RE_REPLICATE);
        }
        log.info("Queued re-replication of {} objects for recovered node {}",
                missedReplications.size(), recoveredNodeId);
    }

    private String buildReplicaPath(String nodeId,
                                    String bucket,
                                    String key) {
        return "./storage-data/replicas/" + nodeId + "/" + bucket + "/" + key;
    }
}
