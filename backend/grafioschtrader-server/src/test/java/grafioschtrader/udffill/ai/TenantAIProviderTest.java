package grafioschtrader.udffill.ai;

import static org.assertj.core.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exportdelete.MySqlDeleteMyData;
import grafiosch.exportdelete.MySqlExportMyData;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafiosch.security.UserAuthentication;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.TenantAIProvider;
import grafioschtrader.repository.TenantAIProviderJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.rest.TenantAIProviderResource;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/** Real persistence, budget counters and resource ownership checks; only the external model transport is replaced. */
@GTIntegrationTestContext
@Import(TenantAIProviderTest.StubConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantAIProviderTest {
  static class StubProvider extends AIChatModelProvider {
    StubProvider(ConnectorApiKeyJpaRepository instance, GlobalparametersService parameters, AIRequestGate gate,
        TenantAIProviderJpaRepository tenants) {
      super(instance, parameters, gate, tenants, "http://localhost:11434");
    }

    @Override
    protected ChatModel getChatModel(AIProviderChoice choice) {
      return _ -> {
        if (choice.model().startsWith("fail")) {
          throw new IllegalStateException("401 invalid API key");
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage("OK"))));
      };
    }

    ChatModel cachedClient(AIProviderChoice choice) {
      return super.getChatModel(choice);
    }
  }

  static class StubConfig {
    @Bean
    @Primary
    StubProvider tenantTestProvider(ConnectorApiKeyJpaRepository instance, GlobalparametersService parameters,
        AIRequestGate gate, TenantAIProviderJpaRepository tenants) {
      return new StubProvider(instance, parameters, gate, tenants);
    }
  }

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private PlatformTransactionManager transactions;
  @Autowired
  private TenantAIProviderResource resource;
  @Autowired
  private TenantAIProviderJpaRepository repository;
  @Autowired
  private StubProvider models;
  @Autowired
  private ObjectMapper mapper;
  @Autowired
  private grafioschtrader.rest.UDFMetadataSecurityResource fields;
  @Autowired
  private ValueSuggestService suggestions;
  private User user;
  private Integer tenantId;
  private final Map<String, Object> oldSettings = new LinkedHashMap<>();

  @BeforeAll
  void saveSettings() {
    for (String key : List.of("gt.ai.providers", "gt.ai.tenant.providers", "gt.ai.instance.keys.propose.rule",
        "gt.ai.daily.requests.instance")) {
      String column = key.endsWith("providers") ? "property_string" : "property_int";
      oldSettings.put(key, jdbc.queryForObject("SELECT " + column + " FROM globalparameters WHERE property_name = ?",
          Object.class, key));
    }
  }

  @BeforeEach
  void createFixture() {
    new TransactionTemplate(transactions).executeWithoutResult(_ -> {
      Tenant tenant = new Tenant("Tenant AI test", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(tenant);
      tenantId = tenant.getId();
      user = new User("tenant-ai@test.local", "unused", "Tenant AI", "en", 0);
      user.setIdTenant(tenantId);
      em.persist(user);
    });
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
    setting("gt.ai.providers", "openai:instance-ok");
    setting("gt.ai.tenant.providers", "anthropic,openai,gemini");
    setting("gt.ai.instance.keys.propose.rule", 1);
    setting("gt.ai.daily.requests.instance", 100000);
  }

  @AfterEach
  void removeFixture() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM tenant_ai_provider WHERE id_tenant = ?", tenantId);
    jdbc.update("DELETE FROM ai_request_log WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM user_entity_change_count WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM udf_metadata WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM user WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM tenant WHERE id_tenant = ?", tenantId);
    AIChatModelProvider.PROVIDERS.forEach(p -> models.resetTenant(tenantId, p));
  }

  @AfterAll
  void restoreSettings() {
    oldSettings.forEach(this::setting);
  }

  private void setting(String key, Object value) {
    String column = key.endsWith("providers") ? "property_string" : "property_int";
    jdbc.update("UPDATE globalparameters SET " + column + " = ? WHERE property_name = ?", value, key);
    em.getEntityManagerFactory().getCache().evict(grafiosch.entities.Globalparameters.class, key);
  }

  private TenantAIProvider create(String provider, String model, int priority) throws Exception {
    TenantAIProvider row = new TenantAIProvider();
    row.setProvider(provider);
    row.setModel(model);
    row.setPriority((byte) priority);
    row.setApiKey("test-secret-not-a-real-key-1234");
    return resource.create(row).getBody();
  }

  private void call(AIRequestPurpose purpose) {
    models.call(new Prompt("OK"), user, purpose);
  }

  @Test
  @DisplayName("Tenant failures precede instance fallback and each attempt records its payer")
  void fallbackAndBudgets() throws Exception {
    create("openai", "fail-second", 2);
    create("anthropic", "fail-first", 1);
    call(AIRequestPurpose.PROPOSE_RULE);
    assertThat(jdbc.queryForList("SELECT key_source FROM ai_request_log WHERE id_user = ? ORDER BY id_ai_request_log",
        Integer.class, user.getIdUser())).containsExactly(1, 1, 2);
    assertThat(jdbc.queryForList("SELECT model FROM ai_request_log WHERE id_user = ? ORDER BY id_ai_request_log",
        String.class, user.getIdUser())).containsExactly("fail-first", "fail-second", "instance-ok");
    assertThat(jdbc.queryForObject(
        "SELECT sum(count_insert + count_update + count_delete) FROM user_entity_change_count WHERE id_user = ? AND entity_name = 'AIRequest'",
        Integer.class, user.getIdUser())).isEqualTo(3);
  }

  @Test
  @DisplayName("Tenant requests survive a zero instance budget; value suggestions never reach instance keys")
  void tenantBudgetAndValueIsolation() throws Exception {
    TenantAIProvider row = create("openai", "ok", 1);
    setting("gt.ai.daily.requests.instance", 0);
    call(AIRequestPurpose.SUGGEST_VALUE);
    row.setModel("fail");
    resource.update(row);
    assertThatThrownBy(() -> call(AIRequestPurpose.SUGGEST_VALUE)).isInstanceOf(DataViolationException.class);
    assertThatThrownBy(() -> call(AIRequestPurpose.PROPOSE_RULE)).isInstanceOfSatisfying(DataViolationException.class,
        e -> assertThat(e.getDataViolation().getFirst().getMessageKey())
            .isEqualTo("gt.ai.daily.requests.instance.exceeded"));
    assertThat(
        jdbc.queryForList("SELECT key_source FROM ai_request_log WHERE id_user = ?", Integer.class, user.getIdUser()))
            .containsOnly(1).hasSize(3);
  }

  @Test
  @DisplayName("Write-only secrets survive empty updates, key tests report failures, export omits and account deletion removes them")
  void secretLifecycle() throws Exception {
    TenantAIProvider row = create("openai", "fail", 1);
    String encrypted = jdbc.queryForObject("SELECT api_key FROM tenant_ai_provider WHERE id_tenant_ai_provider = ?",
        String.class, row.getId());
    assertThat(encrypted).doesNotContain("test-secret");
    assertThat(mapper.writeValueAsString(resource.getAll())).contains("…1234").doesNotContain("test-secret",
        "encryptedApiKey", "\"apiKey\":", "\"api_key\":");
    assertThat(resource.testKey(row.getId()).reason()).contains("401 invalid API key");
    row.setApiKey("");
    row.setModel("ok");
    resource.update(row);
    assertThat(jdbc.queryForObject("SELECT api_key FROM tenant_ai_provider WHERE id_tenant_ai_provider = ?",
        String.class, row.getId())).isEqualTo(encrypted);
    assertThat(resource.testKey(row.getId()).succeeded()).isTrue();
    assertThat(
        jdbc.queryForList("SELECT purpose FROM ai_request_log WHERE id_user = ?", Integer.class, user.getIdUser()))
            .containsOnly(3).hasSize(2);
    assertThat(new MySqlExportMyData(jdbc, user).exportDataMyData().toString()).doesNotContain("tenant_ai_provider",
        encrypted, "test-secret");
    new MySqlDeleteMyData(jdbc, user).deleteMyData();
    assertThat(repository.findById(row.getId())).isEmpty();
  }

  @Test
  @DisplayName("Managed or simulation context still uses home keys but refuses writes and foreign row access")
  void ownershipAndAvailability() throws Exception {
    setting("gt.ai.instance.keys.propose.rule", 0);
    assertThat(models.isRuleProposalAvailable(user)).isFalse();
    assertThat(models.isValueSuggestionAvailable(user)).isFalse();
    TenantAIProvider row = create("gemini", "ok", 1);
    user.setActualIdTenant(tenantId + 1000000);
    assertThat(models.getProviders(user, AIRequestPurpose.SUGGEST_VALUE)).singleElement()
        .satisfies(c -> assertThat(c.idTenant()).isEqualTo(tenantId));
    assertThatThrownBy(() -> resource.update(row)).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> resource.deleteResource(row.getId())).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> create("openai", "ok", 2)).isInstanceOf(SecurityException.class);
    user.setActualIdTenant(null);
    setting("gt.ai.tenant.providers", "none");
    assertThat(models.isValueSuggestionAvailable(user)).isFalse();
    assertThatThrownBy(() -> resource.testKey(row.getId())).isInstanceOf(DataViolationException.class);
    user.setIdTenant(tenantId + 1000000);
    assertThat(resource.getAll()).isEmpty();
    assertThatThrownBy(() -> resource.testKey(row.getId())).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> resource.update(row)).isInstanceOf(SecurityException.class);
    user.setIdTenant(tenantId);
  }

  @Test
  @DisplayName("Changing a saved key invalidates only that tenant's provider clients")
  void cacheInvalidation() throws Exception {
    TenantAIProvider row = create("openai", "ok", 1);
    var choice = models.getProviders(user, AIRequestPurpose.SUGGEST_VALUE).getFirst();
    ChatModel first = models.cachedClient(choice);
    assertThat(models.cachedClient(choice)).isSameAs(first);
    models.reset("openai");
    assertThat(models.cachedClient(choice)).isSameAs(first);
    row.setApiKey("replacement-secret-5678");
    resource.update(row);
    assertThat(models.cachedClient(choice)).isNotSameAs(first);
    assertThat(resource.getAll().getFirst().getApiKeyHint()).isEqualTo("…5678");
  }

  @Test
  @DisplayName("An existing LLM field keeps its source after key removal and the next run reports missing keys")
  void existingFieldAfterKeyRemoval() throws Exception {
    TenantAIProvider row = create("openai", "ok", 1);
    var field = new grafioschtrader.entities.UDFMetadataSecurity(0, 0, user.getIdUser(), null, "Tenant AI field", null,
        grafiosch.types.UDFDataType.UDF_Numeric, "10,4", (byte) 98);
    field.setFillSource(UDFFillSource.LLM);
    field.setFillRule("The annual expense ratio in percent");
    field = fields.create(field).getBody();
    resource.deleteResource(row.getId());
    assertThat(fields.getFillSources().getBody().valueSuggestion()).isFalse();
    assertThat(fields.update(field).getBody().getFillSource()).isEqualTo(UDFFillSource.LLM);
    assertThat(suggestions.runField(field.getIdUDFMetadata())).isEqualTo("gt.ai.tenant.not.configured");
  }

  @Test
  @DisplayName("Priority ties use row IDs and inactive keys can be tested but are not offered")
  void tiesAndInactiveRows() throws Exception {
    TenantAIProvider first = create("gemini", "ok", 3);
    TenantAIProvider second = create("openai", "ok", 3);
    assertThat(models.getProviders(user, AIRequestPurpose.SUGGEST_VALUE))
        .extracting(AIChatModelProvider.AIProviderChoice::idTenantAIProvider)
        .containsExactly(first.getId(), second.getId());
    first.setActive(false);
    resource.update(first);
    assertThat(resource.testKey(first.getId()).succeeded()).isTrue();
    assertThat(models.getProviders(user, AIRequestPurpose.SUGGEST_VALUE))
        .extracting(AIChatModelProvider.AIProviderChoice::idTenantAIProvider).containsExactly(second.getId());
  }
}
