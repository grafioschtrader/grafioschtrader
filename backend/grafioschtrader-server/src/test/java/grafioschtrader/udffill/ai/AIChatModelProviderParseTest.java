package grafioschtrader.udffill.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import grafioschtrader.udffill.ai.AIChatModelProvider.AIProviderChoice;

/**
 * The value of {@code gt.ai.providers} as the administrator enters it: the order is the priority, so a parser that
 * reorders, drops or duplicates an entry sends the values to a provider the administrator did not choose first.
 */
class AIChatModelProviderParseTest {

  @Test
  @DisplayName("Order is kept, a model after the first colon is taken whole, a missing model gets the default")
  void orderAndModels() {
    assertThat(AIChatModelProvider.parse(" Gemini:gemini-2.5-pro , openai, ollama:llama3.1:8b")).containsExactly(
        new AIProviderChoice("gemini", "gemini-2.5-pro"),
        new AIProviderChoice("openai", OpenAiChatOptions.DEFAULT_CHAT_MODEL),
        new AIProviderChoice("ollama", "llama3.1:8b"));
  }

  @Test
  @DisplayName("Unknown providers are skipped, a repeated one counts at its first position, Ollama has no default")
  void unknownAndRepeated() {
    assertThat(AIChatModelProvider.parse("mistral,openai:gpt-5-mini,ollama,openai:gpt-4o"))
        .containsExactly(new AIProviderChoice("openai", "gpt-5-mini"), new AIProviderChoice("ollama", ""));
  }

  @Test
  @DisplayName("A general wrapper message of the client keeps the answer of the provider beside it")
  void failureReasonShowsCause() {
    RuntimeException wrapped = new RuntimeException("Failed to generate content",
        new IllegalStateException("400 Bad Request. API key not valid. Please pass a valid API key."));
    assertThat(AIChatModelProvider.failureReason(wrapped))
        .isEqualTo("Failed to generate content (400 Bad Request. API key not valid. Please pass a valid API key.)");
    assertThat(AIChatModelProvider.failureReason(new IllegalStateException("401: Incorrect API key provided")))
        .isEqualTo("401: Incorrect API key provided");
  }

  @Test
  @DisplayName("Only an answer with text or a tool call is usable")
  void emptyAnswer() {
    assertThat(AIChatModelProvider.emptyAnswerReason(response(new AssistantMessage("{\"rule\": null}")), 12)).isNull();
    assertThat(
        AIChatModelProvider
            .emptyAnswerReason(
                response(AssistantMessage.builder()
                    .toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", "fetch_page", "{}"))).build()),
                30)).isNull();
    assertThat(AIChatModelProvider.emptyAnswerReason(response(new AssistantMessage(" ")), 1000))
        .startsWith("empty answer").contains("1000 output tokens");
  }

  @Test
  @DisplayName("Low reasoning effort only for the OpenAI reasoning models, which alone accept the parameter")
  void openAiReasoningEffort() {
    assertThat(AIChatModelProvider.openAiReasoningEffort("gpt-5-mini")).isEqualTo("low");
    assertThat(AIChatModelProvider.openAiReasoningEffort("o4-mini")).isEqualTo("low");
    assertThat(AIChatModelProvider.openAiReasoningEffort("gpt-4o")).isNull();
    assertThat(AIChatModelProvider.openAiReasoningEffort("gpt-4.1-mini")).isNull();
  }

  @Test
  @DisplayName("The tool of a value proposal reaches the model inside its own options, with its key and model kept")
  void toolCallbacksInModelOptions() {
    OpenAiChatModel chatModel = OpenAiChatModel.builder()
        .options(OpenAiChatOptions.builder().apiKey("test-key").model("gpt-5-mini").build()).build();
    ToolCallback tool = FunctionToolCallback.builder("fetchPage", (String _) -> "")
        .description("Reads a public page").inputType(String.class).build();
    Prompt prompt = new Prompt(List.of(new UserMessage("?")),
        ToolCallingChatOptions.builder().toolCallbacks(tool).build());

    Prompt sent = AIChatModelProvider.withModelOptions(prompt, chatModel);

    assertThat(sent.getOptions()).isInstanceOf(OpenAiChatOptions.class);
    OpenAiChatOptions options = (OpenAiChatOptions) sent.getOptions();
    assertThat(options.getModel()).isEqualTo("gpt-5-mini");
    assertThat(options.getApiKey()).isEqualTo("test-key");
    assertThat(options.getToolCallbacks()).containsExactly(tool);
    assertThat(sent.getInstructions()).isEqualTo(prompt.getInstructions());
    Prompt plain = new Prompt(new UserMessage("?"));
    assertThat(AIChatModelProvider.withModelOptions(plain, chatModel)).isSameAs(plain);
  }

  private static ChatResponse response(AssistantMessage message) {
    return new ChatResponse(List.of(new Generation(message)));
  }

  @Test
  @DisplayName("None, null and blank switch every model feature off")
  void empty() {
    assertThat(AIChatModelProvider.parse("none")).isEmpty();
    assertThat(AIChatModelProvider.parse(null)).isEmpty();
    assertThat(AIChatModelProvider.parse(" ")).isEmpty();
  }
}
