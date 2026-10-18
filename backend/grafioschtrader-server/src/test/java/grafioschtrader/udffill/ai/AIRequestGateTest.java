package grafioschtrader.udffill.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.dto.InputRule;
import grafiosch.entities.Globalparameters;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.security.UserAuthentication;
import grafioschtrader.entities.Tenant;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.rest.UDFMetadataSecurityResource;
import grafioschtrader.types.AIKeySource;
import grafioschtrader.types.AIRequestPurpose;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.udffill.ai.AIChatModelProvider.AIProviderChoice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Validator;

/**
 * The budget gate of the language model requests against {@code grafioschtrader_t}. The application runs with the
 * default {@code gt.ai.provider=none}, which also shows that it starts without any key. No test calls a model.
 */
@GTIntegrationTestContext
@TestInstance(Lifecycle.PER_CLASS)
class AIRequestGateTest {

  private static final String INSTANCE_LIMIT = "gt.ai.daily.requests.instance";

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Autowired
  private AIRequestGate gate;
  @Autowired
  private AIChatModelProvider aiChatModelProvider;
  @Autowired
  private UDFMetadataSecurityResource udfMetadataSecurityResource;
  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;
  @Autowired
  private Validator validator;

  private Integer idTenant;
  private User user;
  private Integer savedInstanceLimit;

  @BeforeAll
  void createFixture() {
    new TransactionTemplate(transactionManager).executeWithoutResult(_ -> {
      Tenant tenant = new Tenant("AI request gate", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(tenant);
      idTenant = tenant.getId();
      user = new User("ai-request-gate@test.local", "unused", "AI request gate", "en", 0);
      user.setIdTenant(idTenant);
      em.persist(user);
    });
    savedInstanceLimit = jdbc.queryForObject("SELECT property_int FROM globalparameters WHERE property_name = ?",
        Integer.class, INSTANCE_LIMIT);
  }

  @AfterAll
  void removeFixture() {
    jdbc.update("UPDATE globalparameters SET property_int = ? WHERE property_name = ?", savedInstanceLimit,
        INSTANCE_LIMIT);
    jdbc.update("DELETE FROM ai_request_log WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM user_entity_change_count WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM user WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM tenant WHERE id_tenant = ?", idTenant);
  }

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM ai_request_log WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM user_entity_change_count WHERE id_user = ?", user.getIdUser());
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  /** Leaves exactly {@code free} units of the instance budget of today. */
  private void leaveFreeUnits(int free) {
    long today = jdbc.queryForObject("SELECT COUNT(*) FROM ai_request_log WHERE key_source = 2 AND request_time >= ?",
        Long.class, LocalDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay());
    jdbc.update("UPDATE globalparameters SET property_int = ? WHERE property_name = ?", today + free, INSTANCE_LIMIT);
  }

  private Integer reserve() {
    return gate.reserve(user, AIRequestPurpose.PROPOSE_RULE, new AIProviderChoice("anthropic", "test-model"));
  }

  @Test
  @DisplayName("A tenant key at its daily limit is refused before anything counts; the limit counts only this key")
  void tenantLimitOfOneKey() {
    int idKey = 987_654;
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    for (int i = 0; i < 2; i++) {
      jdbc.update("""
          INSERT INTO ai_request_log (id_user, request_time, purpose, key_source, provider, id_tenant_ai_provider,
          model, status) VALUES (?, ?, 2, 1, 'openai', ?, 'm', 1)""", user.getIdUser(), now, idKey);
    }
    // A request of yesterday and one over another key of the tenant do not count against today's limit of this key
    jdbc.update("""
        INSERT INTO ai_request_log (id_user, request_time, purpose, key_source, provider, id_tenant_ai_provider, model,
        status) VALUES (?, ?, 2, 1, 'openai', ?, 'm', 1)""", user.getIdUser(), now.minusDays(1), idKey);
    jdbc.update("""
        INSERT INTO ai_request_log (id_user, request_time, purpose, key_source, provider, id_tenant_ai_provider, model,
        status) VALUES (?, ?, 2, 1, 'gemini', ?, 'm', 1)""", user.getIdUser(), now, idKey + 1);

    AIProviderChoice atLimit = new AIProviderChoice("openai", "m", AIKeySource.TENANT, idTenant, idKey, 2);
    assertThatThrownBy(() -> gate.reserve(user, AIRequestPurpose.SUGGEST_VALUE, atLimit))
        .isInstanceOf(AITenantLimitException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_entity_change_count WHERE id_user = ?", Integer.class,
        user.getIdUser())).isZero();

    AIProviderChoice belowLimit = new AIProviderChoice("openai", "m", AIKeySource.TENANT, idTenant, idKey, 3);
    Integer reservation = gate.reserve(user, AIRequestPurpose.SUGGEST_VALUE, belowLimit);
    assertThat(jdbc.queryForObject("SELECT id_tenant_ai_provider FROM ai_request_log WHERE id_ai_request_log = ?",
        Integer.class, reservation)).isEqualTo(idKey);
  }

  @Test
  @DisplayName("Concurrent reservations cannot use the last free unit twice")
  void lastUnitOnlyOnce() throws Exception {
    leaveFreeUnits(1);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(4);
    List<Future<Integer>> futures = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      Callable<Integer> task = () -> {
        start.await();
        try {
          return reserve();
        } catch (DataViolationException e) {
          return null;
        }
      };
      futures.add(executor.submit(task));
    }
    start.countDown();
    int granted = 0;
    for (Future<Integer> future : futures) {
      if (future.get() != null) {
        granted++;
      }
    }
    executor.shutdown();

    assertThat(granted).isEqualTo(1);
    assertThat(
        jdbc.queryForObject("SELECT COUNT(*) FROM ai_request_log WHERE id_user = ?", Integer.class, user.getIdUser()))
            .isEqualTo(1);
  }

  @Test
  @DisplayName("A reservation without completion still counts")
  void unfinishedReservationCounts() {
    leaveFreeUnits(1);
    reserve();

    assertThatThrownBy(this::reserve).isInstanceOfSatisfying(DataViolationException.class,
        e -> assertThat(e.getDataViolation().getFirst().getMessageKey())
            .isEqualTo("gt.ai.daily.requests.instance.exceeded"));
    assertThat(
        jdbc.queryForObject("SELECT status FROM ai_request_log WHERE id_user = ?", Integer.class, user.getIdUser()))
            .isZero();
  }

  @Test
  @DisplayName("Rows older than the retention are deleted before the budgets are counted")
  void retentionDeletesOldRows() {
    jdbc.update("""
        INSERT INTO ai_request_log (id_user, request_time, purpose, provider, model, status)
        VALUES (?, ?, 1, 'anthropic', 'old-model', 1)""", user.getIdUser(),
        LocalDateTime.now(ZoneOffset.UTC).minusDays(91));
    leaveFreeUnits(5);

    reserve();

    assertThat(jdbc.queryForList("SELECT model FROM ai_request_log WHERE id_user = ?", String.class, user.getIdUser()))
        .containsExactly("test-model");
  }

  @Test
  @DisplayName("The stored input rule of the provider list is complete and accepts exactly the list the provider reads")
  void providerListInputRule() {
    // input_rule holds 100 characters; a longer rule was once cut off and broke the edit dialog of the global settings
    InputRule rule = InputRule.parse(jdbc.queryForObject(
        "SELECT input_rule FROM globalparameters WHERE property_name = 'gt.ai.providers'", String.class));
    assertThat(rule.validate("gemini:gemini-2.5-flash,openai,ollama:llama3.1:8b")).isNull();
    assertThat(rule.validate("none")).isNull();
    assertThat(List.of("mistral", "gemini,", "gemini, openai", "openai:"))
        .allSatisfy(value -> assertThat(rule.validate(value)).isEqualTo("g.input.rule.pattern.violation"));
  }

  @Test
  @DisplayName("The administrator can replace the seeded provider list by a long list with models")
  void providerListEditable() {
    String providers = "gemini:gemini-2.5-flash,openai:gpt-5-mini,anthropic,ollama:llama3.1:8b";
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(status -> {
      // A row seeded with NULL has no type, and replacing its value threw IllegalArgumentException
      Globalparameters stored = globalparametersJpaRepository.findById("gt.ai.providers").orElseThrow();
      stored.replaceExistingPropertyValue(new Globalparameters().setPropertyString(providers));
      assertThat(validator.validate(stored)).isEmpty();
      globalparametersJpaRepository.saveAndFlush(stored);
      assertThat(jdbc.queryForObject(
          "SELECT property_string FROM globalparameters WHERE property_name = 'gt.ai.providers'", String.class))
              .isEqualTo(providers);
      status.setRollbackOnly();
    });
  }

  @Test
  @DisplayName("Without a configured provider nothing of the proposal is offered and the endpoints answer 403")
  void disabledWithoutProvider() {
    assertThat(aiChatModelProvider.isRuleProposalAvailable(user)).isFalse();
    assertThat(udfMetadataSecurityResource.getFillSources().getBody().ruleProposal()).isFalse();
    assertThat(udfMetadataSecurityResource.previewRuleProposal(1).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(udfMetadataSecurityResource.proposeRule(1, List.of(1)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    // The fill source stays selectable without a key; such a field keeps its values but is not processed
    assertThat(udfMetadataSecurityResource.getFillSources().getBody().fillSources())
        .anyMatch(o -> o.fillSource() == UDFFillSource.LLM);
    assertThat(udfMetadataSecurityResource.getFillSources().getBody().valueSuggestion()).isFalse();
    assertThat(udfMetadataSecurityResource.suggestValues(1).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(udfMetadataSecurityResource.getProposals(1).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(udfMetadataSecurityResource.acceptProposals(1, List.of()).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(udfMetadataSecurityResource.rejectProposals(1, List.of()).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(udfMetadataSecurityResource.retryProposals(1).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
