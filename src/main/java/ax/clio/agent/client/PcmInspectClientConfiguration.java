package ax.clio.agent.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PcmInspectProperties.class)
public class PcmInspectClientConfiguration {
}
