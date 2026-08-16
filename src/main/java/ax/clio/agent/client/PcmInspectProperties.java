package ax.clio.agent.client;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clio.pcm-inspect")
public record PcmInspectProperties(
		URI url
) {
}
