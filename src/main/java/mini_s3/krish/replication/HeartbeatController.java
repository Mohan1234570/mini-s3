package mini_s3.krish.replication;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HeartbeatController {

    private final HeartbeatService heartbeatService;

    // GET /health — storage node responds to heartbeat pings
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    // GET /admin/heartbeat/status — see missed heartbeats per node
    @GetMapping("/admin/heartbeat/status")
    public ResponseEntity<Map<String, Object>> heartbeatStatus() {
        return ResponseEntity.ok(Map.of(
                "missedHeartbeats", heartbeatService.getMissedHeartbeats(),
                "lastSeen", heartbeatService.getLastSeen()
        ));
    }
}
