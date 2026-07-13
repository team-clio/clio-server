package ax.clio.llm.client;

import ax.clio.llm.entity.LlmConfig;

public interface LlmClient {

	String completeJson(LlmConfig config, String model, String systemPrompt, String userPrompt);
}
