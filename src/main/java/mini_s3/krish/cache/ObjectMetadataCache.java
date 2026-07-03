package mini_s3.krish.cache;


import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ObjectMetadataCache implements Serializable {

    private String id;
    private String bucketName;
    private String objectKey;
    private Long size;
    private String contentType;
    private String etag;
    private String storagePath;
    private String primaryNodeId;
    private Integer currentVersion;
    private LocalDateTime createdAt;
}
