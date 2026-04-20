package mini_s3.krish.replication;


import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReplicationEvent {

    private String eventId;          // unique event ID
    private String bucketName;
    private String objectKey;
    private String sourcePath;       // where file lives on primary node
    private String primaryNodeId;    // node that wrote the file
    private String targetNodeId;     // node that should replicate it
    private long fileSize;
    private String etag;
    private EventType eventType;
    private LocalDateTime createdAt;

    public enum EventType {
        REPLICATE,    // copy object to target node
        DELETE,       // delete object from target node
        RE_REPLICATE  // re-replicate because a node came back up
    }
}
