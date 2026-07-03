package mini_s3.krish.cache;


import lombok.*;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PresignedToken implements Serializable {
    private String tokenId;
    private String bucketName;
    private String objectKey;
    private String operation;   // GET or PUT
    private long expiresAt;     // epoch seconds
    private boolean used;
}
