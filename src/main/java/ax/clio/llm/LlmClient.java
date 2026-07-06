package ax.clio.llm;

public interface LlmClient {

	String completeJson(LlmConfig config, String model, String systemPrompt, String userPrompt);
}
