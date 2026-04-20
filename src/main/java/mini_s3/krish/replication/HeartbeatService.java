package mini_s3.krish.replication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mini_s3.krish.object.config.StorageProperties;
import mini_s3.krish.router.ConsistentHashRouter;
import mini_s3.krish.router.StorageNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatService {

    private final ConsistentHashRouter router;
    private final RestTemplate restTemplate;
    private final StorageProperties storageProperties;

    @Value("${replication.heartbeat-miss-threshold:3}")
    private int missingThreshold;

    private final Map<String, Integer> missedHeartbeats =
            new ConcurrentHashMap<>();

    private final Map<String, LocalDateTime> lastSeen =
            new ConcurrentHashMap<>();

    @Scheduled(fixedRateString =
            "${replication.heartbeat-interval-ms:5000}")
    public void checkNodeHealth() {
        for (StorageNode node : router.getAllNodes()) {
            boolean alive = pingNode(node);
            if (alive) {
                handleNodeAlive(node);
            } else {
                handleNodeMissedHeartbeat(node);
            }
        }
    }

    private boolean pingNode(StorageNode node) {
        try {
            Path nodeDir = Paths.get(
                    storageProperties.getBasePath(),
                    "replicas",
                    node.getNodeId());
            Files.createDirectories(nodeDir);
            return true;
        } catch (Exception e) {
            log.error("Node {} disk check failed: {}",
                    node.getNodeId(), e.getMessage());
            return false;
        }
    }

    private void handleNodeAlive(StorageNode node) {
        // If manually disabled via API — never auto-restore
        if (router.isManuallyDisabled(node.getNodeId())) {
            log.debug("Node {} is manually disabled — skipping auto-restore",
                    node.getNodeId());
            return;   // ← THIS IS THE KEY LINE
        }

        boolean wasUnhealthy = !node.isHealthy();
        missedHeartbeats.put(node.getNodeId(), 0);
        lastSeen.put(node.getNodeId(), LocalDateTime.now());

        if (wasUnhealthy) {
            router.markNodeHealthy(node.getNodeId());
            log.info("Node {} is back ONLINE — restoring to ring",
                    node.getNodeId());
        }
    }

    private void handleNodeMissedHeartbeat(StorageNode node) {
        int missed = missedHeartbeats.merge(
                node.getNodeId(), 1, Integer::sum);

        log.warn("Node {} missed heartbeat ({}/{})",
                node.getNodeId(), missed, missingThreshold);

        if (missed >= missingThreshold && node.isHealthy()) {
            router.markNodeUnhealthy(node.getNodeId());
            log.error("Node {} marked UNHEALTHY after {} missed heartbeats",
                    node.getNodeId(), missed);
        }
    }

    public Map<String, Integer> getMissedHeartbeats() {
        return Map.copyOf(missedHeartbeats);
    }

    public Map<String, LocalDateTime> getLastSeen() {
        return Map.copyOf(lastSeen);
    }
}