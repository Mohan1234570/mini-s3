package mini_s3.krish.router;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouterInitializer implements ApplicationRunner {

    private final ConsistentHashRouter router;
    private final NodeConfig nodeConfig;

    @Override
    public void run(ApplicationArguments args) {
        if (nodeConfig.getList() == null) return;

        log.info("Initialising consistent hash ring with {} nodes...",
                nodeConfig.getList().size());

        for (NodeConfig.NodeDefinition def : nodeConfig.getList()) {
            StorageNode node = StorageNode.builder()
                    .nodeId(def.getId())
                    .host(def.getHost())
                    .port(def.getPort())
                    .healthy(true)
                    .build();
            router.addNode(node);
        }

        log.info("Hash ring ready. {} ring positions across {} nodes.",
                router.getRingSize(),
                router.getAllNodes().size());
    }
}
