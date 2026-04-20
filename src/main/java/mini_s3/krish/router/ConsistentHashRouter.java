package mini_s3.krish.router;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Consistent Hash Router with virtual nodes.
 *
 * How it works:
 *  - Each storage node gets VIRTUAL_NODES positions on a hash ring
 *  - An object key is hashed to a position on the ring
 *  - The object is routed to the first node clockwise from that position
 *  - Adding/removing a node only remaps ~1/N of objects (not everything)
 *
 * Why virtual nodes?
 *  - Without them, 3 nodes get 3 positions → uneven load distribution
 *  - With 150 virtual nodes per real node → 450 ring positions → even spread
 */
@Slf4j
@Component
public class ConsistentHashRouter {

    private static final int VIRTUAL_NODES = 150;
    private final Set<String> manuallyDisabled = new HashSet<>();

    // The ring — sorted map of hash → node
    // ConcurrentSkipListMap is thread-safe and keeps keys sorted
    private final ConcurrentSkipListMap<Long, StorageNode> ring =
            new ConcurrentSkipListMap<>();

    // Real nodes by nodeId
    private final Map<String, StorageNode> nodes = new HashMap<>();

    // ── Node management ───────────────────────────────────────────────────────

    public synchronized void addNode(StorageNode node) {
        nodes.put(node.getNodeId(), node);

        // Place VIRTUAL_NODES copies of this node on the ring
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            long hash = hash(node.getNodeId() + "-vnode-" + i);
            ring.put(hash, node);
        }

        log.info("Added node {} to ring. Ring size: {} positions ({} nodes)",
                node, ring.size(), nodes.size());
    }

    public synchronized void removeNode(String nodeId) {
        StorageNode node = nodes.remove(nodeId);
        if (node == null) return;

        // Remove all virtual node positions for this node
        ring.entrySet().removeIf(e ->
                e.getValue().getNodeId().equals(nodeId));

        log.info("Removed node {} from ring. Ring size: {} positions",
                nodeId, ring.size());
    }

    public synchronized void markNodeUnhealthy(String nodeId) {
        StorageNode node = nodes.get(nodeId);
        if (node != null) {
            node.setHealthy(false);
            manuallyDisabled.add(nodeId);   // ← ADD THIS
            log.warn("Marked node {} as UNHEALTHY (manual)", nodeId);
        }
    }

    public synchronized void markNodeHealthy(String nodeId) {
        StorageNode node = nodes.get(nodeId);
        if (node != null) {
            node.setHealthy(true);
            manuallyDisabled.remove(nodeId);  // ← ADD THIS
            log.info("Marked node {} as HEALTHY", nodeId);
        }
    }

    // Add this new method — HeartbeatService will call this
    public synchronized boolean isManuallyDisabled(String nodeId) {
        return manuallyDisabled.contains(nodeId);
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    /**
     * Get the primary node for a given object key.
     * Walks clockwise from the key's hash position to find
     * the first healthy node.
     */
    public Optional<StorageNode> getPrimaryNode(String objectKey) {
        if (ring.isEmpty()) return Optional.empty();

        long hash = hash(objectKey);
        return findHealthyNodeClockwise(hash);
    }

    /**
     * Get N replica nodes for a given object key.
     * Returns the primary + next (N-1) distinct healthy nodes clockwise.
     * Used to determine where to replicate an object.
     */
    public List<StorageNode> getReplicaNodes(String objectKey,
                                             int replicationFactor) {
        if (ring.isEmpty()) return Collections.emptyList();

        long hash = hash(objectKey);
        List<StorageNode> replicas = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Walk clockwise from hash position, collect distinct healthy nodes
        NavigableMap<Long, StorageNode> tail = ring.tailMap(hash);
        Iterable<StorageNode> candidates = () ->
                new RingIterator(tail, ring);

        for (StorageNode node : candidates) {
            if (seen.contains(node.getNodeId())) continue;
            if (!node.isHealthy()) continue;
            replicas.add(node);
            seen.add(node.getNodeId());
            if (replicas.size() == replicationFactor) break;
        }

        return replicas;
    }

    /**
     * Get all currently registered healthy nodes.
     */
    public List<StorageNode> getHealthyNodes() {
        return nodes.values().stream()
                .filter(StorageNode::isHealthy)
                .toList();
    }

    /**
     * Get all nodes (healthy and unhealthy).
     */
    public List<StorageNode> getAllNodes() {
        return new ArrayList<>(nodes.values());
    }

    public int getRingSize() {
        return ring.size();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Optional<StorageNode> findHealthyNodeClockwise(long hash) {
        // Try all nodes clockwise from hash — wrap around if needed
        NavigableMap<Long, StorageNode> tail = ring.tailMap(hash);

        // Tail first (clockwise from hash), then head (wrap around)
        for (StorageNode node : tail.values()) {
            if (node.isHealthy()) return Optional.of(node);
        }
        for (StorageNode node : ring.values()) {
            if (node.isHealthy()) return Optional.of(node);
        }
        return Optional.empty(); // all nodes down
    }

    /**
     * MD5-based hash — gives a 64-bit long position on the ring.
     * Using MD5 here for distribution quality, not security.
     */
    long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            // Take first 8 bytes as a long
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    /**
     * Iterator that walks the ring clockwise, wrapping around.
     */
    private static class RingIterator
            implements Iterator<StorageNode> {

        private final Iterator<StorageNode> tailIter;
        private final Iterator<StorageNode> headIter;
        private boolean onHead = false;

        RingIterator(NavigableMap<Long, StorageNode> tail,
                     NavigableMap<Long, StorageNode> full) {
            this.tailIter = tail.values().iterator();
            this.headIter = full.values().iterator();
        }

        @Override
        public boolean hasNext() {
            return tailIter.hasNext() || headIter.hasNext();
        }

        @Override
        public StorageNode next() {
            if (!onHead && tailIter.hasNext()) return tailIter.next();
            onHead = true;
            return headIter.next();
        }
    }
}
