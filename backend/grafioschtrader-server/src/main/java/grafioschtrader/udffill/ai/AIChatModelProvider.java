package grafioschtrader.udffill.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import grafiosch.BaseConstants;
import grafiosch.entities.ConnectorApiKey;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafioschtrader.dto.AIKeyTestResult;
import grafioschtrader.entities.TenantAIProvider;
import grafioschtrader.repository.TenantAIProviderJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.AIKeySource;
import grafioschtrader.types.AIRequestPurpose;
import grafioschtrader.types.AIRequestStatus;

/**
 * Language models for user-defined fields. Each request tries the active providers of the caller's home tenant in
 * priority order. Only rule proposals may fall back to the instance providers, and only with administrator consent.
 * Value suggestions and key tests are always tenant-funded. Every attempt passes the budget gate and records its payer;
 * technical failures move to the next provider, whereas a refused budget stops the chain.
 *
 * Clients are cached separately by key source, tenant, provider and model, and invalidated after credential changes.
 * Endpoints come exclusively from the provider SDK or the deployment's Ollama configuration. Calls retain the 60 s
 * timeout, no retries, and bounded output and reasoning tokens. Empty answers count as technical failures.
 */
@Component
public class AIChatModelProvider {

  public static final String ANTHROPIC = "anthropic";
  public static final String OPENAI = "openai";
  public static final String OLLAMA = "ollama";
  /** Google Gemini through the Gemini Developer API with an API key, not Vertex AI. */
  public static final String GEMINI = "gemini";
  /** All providers, in the order the edit form offers them. */
  public static final List<String> PROVIDERS = List.of(ANTHROPIC, OPENAI, GEMINI, OLLAMA);
  /** Providers whose key is stored as connector API key. */
  public static final Set<String> KEY_PROVIDERS = Set.of(ANTHROPIC, OPENAI, GEMINI);

  /** Shared display names for connector keys, tenant settings and request consent. */
  public static final Map<String, String> PROVIDER_NAMES = Map.of(ANTHROPIC, "Anthropic", OPENAI, "OpenAI", GEMINI,
      "Google Gemini", OLLAMA, "Ollama");

  /**
   * Google's alias of its newest Flash model. Google withdraws Gemini models from new users within months, faster than
   * the model constants of Spring AI follow, so a fixed default answers 404 for a newly created key.
   */
  public static final String GEMINI_DEFAULT_MODEL = "gemini-flash-latest";

  /** Output tokens of the answer itself. */
  public static final int MAX_OUTPUT_TOKENS = 1000;
  /**
   * Output tokens a reasoning model may spend before it answers. They count against the output limit and are billed as
   * output; with no room for them gpt-5-mini used all 1000 tokens on reasoning and answered nothing.
   */
  public static final int MAX_REASONING_TOKENS = 3000;
  /** Output limit of a provider whose reasoning counts against it. */
  public static final int MAX_OUTPUT_WITH_REASONING_TOKENS = MAX_OUTPUT_TOKENS + MAX_REASONING_TOKENS;
  public static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);

  private static final Logger log = LoggerFactory.getLogger(AIChatModelProvider.class);

  /**
   * One entry of {@code gt.ai.providers}.
   *
   * @param provider the provider
   * @param model    the model, the default of the provider when the entry names none; empty for Ollama without model
   */
  public record AIProviderChoice(String provider, String model, AIKeySource keySource, Integer idTenant,
      Integer idTenantAIProvider, Integer dailyRequestLimit) {

    public AIProviderChoice(String provider, String model) {
      this(provider, model, AIKeySource.INSTANCE, 0, null, null);
    }

    /** Provider and model as shown to the user, for example {@code Google Gemini (gemini-flash-latest)}. */
    public String label() {
      return PROVIDER_NAMES.getOrDefault(provider, provider) + " (" + model + ")";
    }
  }

  /**
   * The answer of one round trip and the provider that gave it.
   */
  public record AIChatResult(ChatResponse response, AIProviderChoice choice) {
  }

  private final ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;
  private final GlobalparametersService globalparametersService;
  private final AIRequestGate aiRequestGate;
  private final TenantAIProviderJpaRepository tenantProviders;
  private final String ollamaBaseUrl;

  private record ClientKey(AIKeySource source, Integer idTenant, String provider, String model) {
  }

  private final Map<ClientKey, ChatModel> chatModels = new ConcurrentHashMap<>();

  public AIChatModelProvider(ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository,
      GlobalparametersService globalparametersService, AIRequestGate aiRequestGate,
      TenantAIProviderJpaRepository tenantProviders,
      @Value("${gt.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
    this.connectorApiKeyJpaRepository = connectorApiKeyJpaRepository;
    this.globalparametersService = globalparametersService;
    this.aiRequestGate = aiRequestGate;
    this.tenantProviders = tenantProviders;
    this.ollamaBaseUrl = ollamaBaseUrl;
  }

  /**
   * Returns the configured providers in order of priority, read on every call so that a change of the global parameter
   * takes effect without a restart.
   */
  public List<AIProviderChoice> getProviders() {
    return parse(globalparametersService.getAIProviders());
  }

  /** Builds the caller's home-tenant chain, followed only for rule proposals by permitted instance providers. */
  public List<AIProviderChoice> getProviders(User user, AIRequestPurpose purpose) {
    List<AIProviderChoice> choices = new ArrayList<>(getTenantProviders(user));
    if (purpose == AIRequestPurpose.PROPOSE_RULE && globalparametersService.isAIInstanceKeysProposeRule()) {
      choices.addAll(getProviders());
    }
    return List.copyOf(choices);
  }

  private List<AIProviderChoice> getTenantProviders(User user) {
    if (user.getIdTenant() == null) {
      return List.of();
    }
    List<String> allowed = globalparametersService.getAITenantProviders();
    return tenantProviders.findByIdTenantOrderByPriorityAscIdTenantAIProviderAsc(user.getIdTenant()).stream()
        .filter(TenantAIProvider::isActive).filter(row -> allowed.contains(row.getProvider()))
        .map(AIChatModelProvider::choice).toList();
  }

  private static AIProviderChoice choice(TenantAIProvider row) {
    return new AIProviderChoice(row.getProvider(),
        row.getModel() == null ? defaultModel(row.getProvider()) : row.getModel(), AIKeySource.TENANT,
        row.getIdTenant(), row.getId(), row.getDailyRequestLimit());
  }

  /**
   * Parses the value of {@code gt.ai.providers}: comma-separated entries {@code provider} or {@code provider:model}.
   * Unknown providers, among them {@code none}, are skipped, a provider listed twice counts once at its first position.
   *
   * @param value the value, may be null
   * @return the providers in order of priority, empty when none is configured
   */
  public static List<AIProviderChoice> parse(String value) {
    Map<String, AIProviderChoice> choices = new LinkedHashMap<>();
    if (value != null) {
      for (String entry : value.split(",")) {
        String e = entry.strip();
        int colon = e.indexOf(':');
        String provider = (colon < 0 ? e : e.substring(0, colon)).strip().toLowerCase(Locale.ROOT);
        String model = colon < 0 ? "" : e.substring(colon + 1).strip();
        if (PROVIDERS.contains(provider) && !choices.containsKey(provider)) {
          choices.put(provider, new AIProviderChoice(provider, model.isEmpty() ? defaultModel(provider) : model));
        }
      }
    }
    return List.copyOf(choices.values());
  }

  public static String defaultModel(String provider) {
    return switch (provider) {
    case ANTHROPIC -> AnthropicChatOptions.DEFAULT_MODEL;
    case OPENAI -> OpenAiChatOptions.DEFAULT_CHAT_MODEL;
    case GEMINI -> GEMINI_DEFAULT_MODEL;
    default -> "";
    };
  }

  /**
   * Whether this user has an active tenant provider or a permitted instance fallback for rule proposals.
   */
  public boolean isRuleProposalAvailable(User user) {
    return !getProviders(user, AIRequestPurpose.PROPOSE_RULE).isEmpty();
  }

  public boolean isValueSuggestionAvailable(User user) {
    return !getTenantProviders(user).isEmpty();
  }

  /**
   * Sends one prompt, trying the providers in order of priority. Every attempt reserves a unit of the budgets before
   * the call and records its outcome; a technical failure moves on to the next provider, and so does a tenant key
   * that reached the daily limit its tenant set. When every key is at its tenant limit, the message says so.
   *
   * @param prompt  the prompt
   * @param user    the user on whose behalf the model is called
   * @param purpose what the call is for
   * @return the answer and the provider that gave it
   * @throws DataViolationException                               with a translated message when a budget refuses the
   *                                                              request, no provider is configured, or every provider
   *                                                              failed
   * @throws grafiosch.exceptions.LimitEntityTransactionException when the daily budget of the user is used up
   */
  public AIChatResult call(Prompt prompt, User user, AIRequestPurpose purpose) {
    List<AIProviderChoice> choices = getProviders(user, purpose);
    if (choices.isEmpty()) {
      throw new DataViolationException("ai.provider", "gt.ai.tenant.not.configured", null);
    }
    List<String> failures = new ArrayList<>();
    int atTenantLimit = 0;
    for (AIProviderChoice choice : choices) {
      Integer reservation;
      try {
        reservation = aiRequestGate.reserve(user, purpose, choice);
      } catch (AITenantLimitException e) {
        // The tenant limits the cost of this key; its next provider may still answer
        atTenantLimit++;
        failures.add(choice.provider() + ": " + e.getMessage());
        continue;
      }
      try {
        ChatModel chatModel = getChatModel(choice);
        ChatResponse response = chatModel.call(withModelOptions(prompt, chatModel));
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        Integer inputTokens = usage == null ? null : usage.getPromptTokens();
        Integer outputTokens = usage == null ? null : usage.getCompletionTokens();
        String empty = emptyAnswerReason(response, outputTokens);
        if (empty != null) {
          aiRequestGate.complete(reservation, AIRequestStatus.FAILED, inputTokens, outputTokens);
          log.warn("Language model {} gave no answer, trying the next provider: {}", choice.label(), empty);
          failures.add(choice.provider() + ": " + empty);
          continue;
        }
        aiRequestGate.complete(reservation, AIRequestStatus.SUCCEEDED, inputTokens, outputTokens);
        return new AIChatResult(response, choice);
      } catch (RuntimeException e) {
        aiRequestGate.complete(reservation, AIRequestStatus.FAILED, null, null);
        // Provider exceptions can contain request headers. Never log the exception or a decrypted secret.
        log.warn("Language model {} failed, trying the next provider", choice.label());
        failures.add(choice.provider() + ": " + safeFailureReason(e, choice));
      }
    }
    if (atTenantLimit == choices.size()) {
      throw new DataViolationException("ai.provider", "gt.ai.tenant.limit.exhausted", null);
    }
    throw new DataViolationException("ai.provider", "gt.ai.provider.error",
        new Object[] { String.join("; ", failures) });
  }

  /**
   * Carries the tool callbacks of a prompt over into a copy of the model's own options. Since Spring AI 2 a chat model
   * no longer merges the options of a prompt with its defaults: it takes them as they are and casts them to its own
   * options class. Generic options, such as those a value proposal sets for its tool, would fail that cast and would
   * also lose the key, the model name and the token limit set when the model was built. The provider is only known
   * here, after the fallback chain picked it, so the caller cannot build the provider's options itself.
   *
   * @param prompt    the prompt of the caller; without options the model applies its own
   * @param chatModel the model that answers
   * @return the prompt as it is, or with the model's options plus the tool callbacks of the caller
   */
  static Prompt withModelOptions(Prompt prompt, ChatModel chatModel) {
    ChatOptions requested = prompt.getOptions();
    ChatOptions own = chatModel.getOptions();
    if (requested == null || own.getClass().isInstance(requested)
        || !(own instanceof ToolCallingChatOptions ownToolOptions)) {
      return prompt;
    }
    ToolCallingChatOptions.Builder<?> builder = ownToolOptions.mutate();
    if (requested instanceof ToolCallingChatOptions requestedToolOptions
        && requestedToolOptions.getToolCallbacks() != null) {
      builder.toolCallbacks(requestedToolOptions.getToolCallbacks());
    }
    return prompt.mutate().chatOptions(builder.build()).build();
  }

  /** Tests exactly one owned row, including an inactive one, without instance fallback. */
  public AIKeyTestResult testKey(Integer id, User user) {
    TenantAIProvider row = tenantProviders.findByIdTenantAIProviderAndIdTenant(id, user.getIdTenant())
        .orElseThrow(() -> new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH));
    if (!globalparametersService.getAITenantProviders().contains(row.getProvider())) {
      throw new DataViolationException("provider", "gt.ai.tenant.provider.invalid", null);
    }
    AIProviderChoice choice = choice(row);
    Integer reservation;
    try {
      reservation = aiRequestGate.reserve(user, AIRequestPurpose.TEST_KEY, choice);
    } catch (AITenantLimitException e) {
      return new AIKeyTestResult(false, e.getMessage());
    }
    try {
      ChatResponse response = getChatModel(choice)
          .call(new Prompt(new SystemMessage("Reply with the single word OK.")));
      Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
      Integer input = usage == null ? null : usage.getPromptTokens();
      Integer output = usage == null ? null : usage.getCompletionTokens();
      String reason = emptyAnswerReason(response, output);
      aiRequestGate.complete(reservation, reason == null ? AIRequestStatus.SUCCEEDED : AIRequestStatus.FAILED, input,
          output);
      return new AIKeyTestResult(reason == null, reason);
    } catch (RuntimeException e) {
      aiRequestGate.complete(reservation, AIRequestStatus.FAILED, null, null);
      return new AIKeyTestResult(false, safeFailureReason(e, choice));
    }
  }

  private String safeFailureReason(Throwable failure, AIProviderChoice choice) {
    String reason = failureReason(failure);
    if (KEY_PROVIDERS.contains(choice.provider())) {
      try {
        String key = apiKey(choice);
        reason = reason.replace(key, "[redacted]");
      } catch (RuntimeException _) {
        // A missing key or deleted row has no credential to redact.
      }
    }
    return reason;
  }

  /**
   * Tells why an answer carries neither text nor a tool call, which is of no use to any caller.
   *
   * @param response     the answer
   * @param outputTokens the output tokens the provider reported, may be null
   * @return the reason, or null when the answer is usable
   */
  static String emptyAnswerReason(ChatResponse response, Integer outputTokens) {
    Generation generation = response.getResult();
    AssistantMessage output = generation == null ? null : generation.getOutput();
    if (output != null && (output.hasToolCalls() || (output.getText() != null && !output.getText().isBlank()))) {
      return null;
    }
    String finishReason = generation == null ? null : generation.getMetadata().getFinishReason();
    return "empty answer (finish reason " + finishReason + ", " + outputTokens + " output tokens)";
  }

  /**
   * The reason of a failed call as the user needs it. Some clients wrap the answer of the provider in a general
   * message, Spring AI's Gemini model for example in "Failed to generate content", so the innermost cause is added when
   * it says something else, such as an invalid key, an exhausted quota or an unknown model.
   */
  static String failureReason(Throwable e) {
    Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
    String rootMessage = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    return root == e || rootMessage.equals(e.getMessage()) ? rootMessage : e.getMessage() + " (" + rootMessage + ")";
  }

  /**
   * Returns the chat model of a provider, building it on first use.
   *
   * @param choice provider and model
   * @return the chat model
   * @throws DataViolationException when the key is missing or Ollama has no model name
   */
  protected synchronized ChatModel getChatModel(AIProviderChoice choice) {
    ClientKey key = new ClientKey(choice.keySource(), choice.idTenant(), choice.provider(), choice.model());
    ChatModel chatModel = chatModels.get(key);
    if (chatModel == null) {
      // One model per tenant/provider, even if an in-flight request still carries a previous model name.
      chatModels.keySet().removeIf(k -> k.source() == key.source() && k.idTenant().equals(key.idTenant())
          && k.provider().equals(key.provider()));
      chatModel = build(choice);
      chatModels.put(key, chatModel);
    }
    return chatModel;
  }

  private ChatModel build(AIProviderChoice choice) {
    String model = choice.model();
    return switch (choice.provider()) {
    case ANTHROPIC -> AnthropicChatModel.builder().options(AnthropicChatOptions.builder().apiKey(apiKey(choice))
        .model(model).maxTokens(MAX_OUTPUT_TOKENS).timeout(CALL_TIMEOUT).maxRetries(0).build()).build();
    case OPENAI -> OpenAiChatModel.builder()
        .options(OpenAiChatOptions.builder().apiKey(apiKey(choice)).model(model)
            .maxCompletionTokens(MAX_OUTPUT_WITH_REASONING_TOKENS).reasoningEffort(openAiReasoningEffort(model))
            .timeout(CALL_TIMEOUT).maxRetries(0).build())
        .build();
    case GEMINI -> buildGemini(model, apiKey(choice));
    default -> buildOllama(model);
    };
  }

  /**
   * The reasoning effort for an OpenAI model: low for the reasoning models, gpt-5 and the o series, none for the
   * others, which refuse the parameter.
   */
  static String openAiReasoningEffort(String model) {
    return model.matches("(gpt-5|o\\d).*") ? "low" : null;
  }

  /**
   * Builds the Gemini client of the Gemini Developer API with the stored key; the SDK retries nothing and each request
   * is bounded by the call timeout. Gemini 3 thinks at a level, Gemini 2 within a token budget; Spring AI refuses a
   * level for a Gemini 2 model.
   */
  private ChatModel buildGemini(String model, String key) {
    Client client = Client.builder().apiKey(key).vertexAI(false).httpOptions(HttpOptions.builder()
        .timeout((int) CALL_TIMEOUT.toMillis()).retryOptions(HttpRetryOptions.builder().attempts(1)).build()).build();
    return GoogleGenAiChatModel.builder().genAiClient(client)
        .options(geminiThinking(
            GoogleGenAiChatOptions.builder().model(model).maxOutputTokens(MAX_OUTPUT_WITH_REASONING_TOKENS), model)
                .build())
        .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0))).build();
  }

  private static GoogleGenAiChatOptions.Builder geminiThinking(GoogleGenAiChatOptions.Builder builder, String model) {
    return model.startsWith("gemini-2") ? builder.thinkingBudget(MAX_REASONING_TOKENS)
        : builder.thinkingLevel(GoogleGenAiThinkingLevel.LOW);
  }

  private ChatModel buildOllama(String model) {
    if (model.isEmpty()) {
      throw new DataViolationException("ai.provider", "gt.ai.model.missing", new Object[] { OLLAMA });
    }
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CALL_TIMEOUT);
    requestFactory.setReadTimeout(CALL_TIMEOUT);
    OllamaApi ollamaApi = OllamaApi.builder().baseUrl(ollamaBaseUrl)
        .restClientBuilder(RestClient.builder().requestFactory(requestFactory)).build();
    return OllamaChatModel.builder().ollamaApi(ollamaApi)
        .options(OllamaChatOptions.builder().model(model).numPredict(MAX_OUTPUT_WITH_REASONING_TOKENS).build())
        .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0))).build();
  }

  private String apiKey(AIProviderChoice choice) {
    return (choice.keySource() == AIKeySource.TENANT
        ? tenantProviders.findByIdTenantAIProviderAndIdTenant(choice.idTenantAIProvider(), choice.idTenant())
            .filter(row -> row.getProvider().equals(choice.provider())).map(TenantAIProvider::decryptApiKey)
        : connectorApiKeyJpaRepository.findById(choice.provider()).map(ConnectorApiKey::getApiKey))
            .filter(key -> !key.isBlank()).orElseThrow(() -> new DataViolationException("ai.provider",
                "gt.ai.key.missing", new Object[] { choice.provider() }));
  }

  /**
   * Drops the cached clients of one provider; the next call builds them with the current key.
   *
   * @param provider the provider whose key changed
   */
  public synchronized void reset(String provider) {
    chatModels.keySet().removeIf(key -> key.source() == AIKeySource.INSTANCE && key.provider().equals(provider));
  }

  /** Drops only this tenant's clients; other tenants and instance keys are independent. */
  public synchronized void resetTenant(Integer idTenant, String provider) {
    chatModels.keySet().removeIf(key -> key.source() == AIKeySource.TENANT && key.idTenant().equals(idTenant)
        && key.provider().equals(provider));
  }
}
