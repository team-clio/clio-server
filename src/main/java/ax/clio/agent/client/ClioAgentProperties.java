package ax.clio.agent.client;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clio.agent")
public record ClioAgentProperties(
		boolean enabled,
		URI url
) {
}
