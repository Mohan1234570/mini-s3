package mini_s3.krish.router;


import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageNode {

    private String nodeId;       // unique id e.g. "node-1"
    private String host;         // e.g. "localhost"
    private int port;            // e.g. 9001
    private boolean healthy;     // is this node alive?

    public String getBaseUrl() {
        return "http://" + host + ":" + port;
    }

    @Override
    public String toString() {
        return nodeId + "(" + getBaseUrl() + ")[" +
                (healthy ? "UP" : "DOWN") + "]";
    }
}
