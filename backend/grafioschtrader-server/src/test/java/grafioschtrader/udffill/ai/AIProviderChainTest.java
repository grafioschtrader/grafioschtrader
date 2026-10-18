package grafioschtrader.udffill.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exceptions.LimitEntityTransactionException;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafioschtrader.entities.TenantAIProvider;
import grafioschtrader.repository.TenantAIProviderJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.AIKeySource;
import grafioschtrader.types.AIRequestPurpose;

class AIProviderChainTest {
  @Test
  @DisplayName("The chain filters inactive and forbidden tenant rows, keeps tenant order, then appends only permitted rule fallback")
  void chainSelection() {
    var tenants = mock(TenantAIProviderJpaRepository.class);
    var parameters = mock(GlobalparametersService.class);
    var models = new AIChatModelProvider(mock(ConnectorApiKeyJpaRepository.class), parameters,
        mock(AIRequestGate.class), tenants, "http://localhost:11434");
    User user = new User();
    user.setIdTenant(42);
    user.setActualIdTenant(99);
    when(parameters.getAITenantProviders()).thenReturn(List.of("openai", "gemini"));
    when(parameters.getAIProviders()).thenReturn("openai:instance,anthropic:other");
    when(parameters.isAIInstanceKeysProposeRule()).thenReturn(true);
    when(tenants.findByIdTenantOrderByPriorityAscIdTenantAIProviderAsc(42))
        .thenReturn(List.of(row(7, "anthropic", true), row(8, "gemini", false), row(9, "openai", true)));
    var rule = models.getProviders(user, AIRequestPurpose.PROPOSE_RULE);
    assertThat(rule).extracting(AIChatModelProvider.AIProviderChoice::provider).containsExactly("openai", "openai",
        "anthropic");
    assertThat(rule).extracting(AIChatModelProvider.AIProviderChoice::keySource).containsExactly(AIKeySource.TENANT,
        AIKeySource.INSTANCE, AIKeySource.INSTANCE);
    assertThat(rule.getFirst().idTenantAIProvider()).isEqualTo(9);
    assertThat(models.getProviders(user, AIRequestPurpose.SUGGEST_VALUE)).containsExactly(rule.getFirst());
    when(parameters.isAIInstanceKeysProposeRule()).thenReturn(false);
    assertThat(models.getProviders(user, AIRequestPurpose.PROPOSE_RULE)).containsExactly(rule.getFirst());
  }

  @Test
  @DisplayName("A tenant key at its daily limit is skipped for the next one; when all are, the message says so")
  void tenantLimitSkipsToNextKey() {
    var tenants = mock(TenantAIProviderJpaRepository.class);
    var parameters = mock(GlobalparametersService.class);
    var gate = mock(AIRequestGate.class);
    ChatModel answering = new ChatModel() {
      @Override
      public ChatResponse call(Prompt prompt) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("18.4"))));
      }
    };
    var models = new AIChatModelProvider(mock(ConnectorApiKeyJpaRepository.class), parameters, gate, tenants,
        "http://localhost:11434") {
      @Override
      protected synchronized ChatModel getChatModel(AIProviderChoice choice) {
        return answering;
      }
    };
    User user = new User();
    user.setIdTenant(42);
    when(parameters.getAITenantProviders()).thenReturn(List.of("openai", "gemini"));
    when(tenants.findByIdTenantOrderByPriorityAscIdTenantAIProviderAsc(42))
        .thenReturn(List.of(row(9, "openai", true), row(8, "gemini", true)));
    when(gate.reserve(eq(user), eq(AIRequestPurpose.SUGGEST_VALUE), argThat(c -> c.provider().equals("openai"))))
        .thenThrow(new AITenantLimitException(30));
    when(gate.reserve(eq(user), eq(AIRequestPurpose.SUGGEST_VALUE), argThat(c -> c.provider().equals("gemini"))))
        .thenReturn(1);

    assertThat(models.call(new Prompt("?"), user, AIRequestPurpose.SUGGEST_VALUE).choice().provider())
        .isEqualTo("gemini");

    when(gate.reserve(eq(user), eq(AIRequestPurpose.SUGGEST_VALUE), argThat(c -> c.provider().equals("gemini"))))
        .thenThrow(new AITenantLimitException(10));
    assertThatThrownBy(() -> models.call(new Prompt("?"), user, AIRequestPurpose.SUGGEST_VALUE))
        .isInstanceOfSatisfying(DataViolationException.class, e -> assertThat(
            e.getDataViolation().getFirst().getMessageKey()).isEqualTo("gt.ai.tenant.limit.exhausted"));
  }

  @Test
  @DisplayName("A stopped run names the limit that stopped it")
  void stopReason() {
    assertThat(ValueSuggestService.stopReason(new LimitEntityTransactionException(null)))
        .isEqualTo("gt.ai.run.stopped.user.limit");
    assertThat(ValueSuggestService
        .stopReason(new DataViolationException("ai.provider", "gt.ai.tenant.limit.exhausted", null)))
            .isEqualTo("gt.ai.tenant.limit.exhausted");
    assertThat(ValueSuggestService
        .stopReason(new DataViolationException("ai.provider", "gt.ai.provider.error", new Object[] { "x" })))
            .isEqualTo("gt.ai.run.stopped");
  }

  private static TenantAIProvider row(int id, String provider, boolean active) {
    var row = new TenantAIProvider();
    row.setIdTenantAIProvider(id);
    row.setIdTenant(42);
    row.setProvider(provider);
    row.setActive(active);
    return row;
  }
}
