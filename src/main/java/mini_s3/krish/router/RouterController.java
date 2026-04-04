package mini_s3.krish.router;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin/router")
@RequiredArgsConstructor
public class RouterController {

    private final ConsistentHashRouter router;

    // GET /admin/router/nodes — list all nodes and health
    @GetMapping("/nodes")
    public ResponseEntity<List<StorageNode>> listNodes() {
        return ResponseEntity.ok(router.getAllNodes());
    }

    // GET /admin/router/locate/{bucket}/{key} — which node owns this object?
    @GetMapping("/locate/{bucket}/{key}")
    public ResponseEntity<Map<String, Object>> locateObject(
            @PathVariable String bucket,
            @PathVariable String key) {

        String objectKey = bucket + "/" + key;
        Optional<StorageNode> primary = router.getPrimaryNode(objectKey);
        List<StorageNode> replicas = router.getReplicaNodes(objectKey, 3);

        return ResponseEntity.ok(Map.of(
                "objectKey", objectKey,
                "primaryNode", primary.map(StorageNode::getNodeId)
                        .orElse("none"),
                "replicaNodes", replicas.stream()
                        .map(StorageNode::getNodeId).toList(),
                "ringSize", router.getRingSize()
        ));
    }

    // POST /admin/router/nodes/{nodeId}/healthy — mark node healthy
    @PostMapping("/nodes/{nodeId}/healthy")
    public ResponseEntity<String> markHealthy(
            @PathVariable String nodeId) {
        router.markNodeHealthy(nodeId);
        return ResponseEntity.ok("Node " + nodeId + " marked healthy");
    }

    // POST /admin/router/nodes/{nodeId}/unhealthy — mark node unhealthy
    @PostMapping("/nodes/{nodeId}/unhealthy")
    public ResponseEntity<String> markUnhealthy(
            @PathVariable String nodeId) {
        router.markNodeUnhealthy(nodeId);
        return ResponseEntity.ok("Node " + nodeId + " marked unhealthy");
    }
}
