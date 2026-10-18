package grafioschtrader.udffill.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
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
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafiosch.security.UserAuthentication;
import grafiosch.types.UDFDataType;
import grafioschtrader.dto.RuleProposalExample;
import grafioschtrader.dto.RuleProposalPreview;
import grafioschtrader.dto.RuleProposalResult;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.UDFFillSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * The rule proposal from preview to evaluation in {@code grafioschtrader_t}, with a provider of the test whose chat
 * model answers what the test prepares. No request leaves the instance.
 */
@GTIntegrationTestContext
@Import(RuleProposalServiceTest.StubProviderConfig.class)
@TestInstance(Lifecycle.PER_CLASS)
class RuleProposalServiceTest {

  /**
   * Chat model of the test: records the prompt and answers {@link #answer}. With {@link #firstFails} the first provider
   * fails as a provider with a wrong key or an exhausted quota does.
   */
  static class StubChatModel implements ChatModel {
    volatile String answer;
    volatile Prompt lastPrompt;
    volatile boolean firstFails;
    volatile boolean firstEmpty;

    @Override
    public ChatResponse call(Prompt prompt) {
      lastPrompt = prompt;
      return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
    }
  }

  /** Imported, not annotated: GTforTest scans the test packages, so an annotated class would reach every context. */
  static class StubProviderConfig {
    @Bean
    StubChatModel stubChatModel() {
      return new StubChatModel();
    }

    @Bean
    @Primary
    AIChatModelProvider stubAIChatModelProvider(ConnectorApiKeyJpaRepository repository,
        GlobalparametersService globalparametersService, AIRequestGate aiRequestGate, StubChatModel stub) {
      return new AIChatModelProvider(repository, globalparametersService, aiRequestGate, null, null) {
        @Override
        public List<AIProviderChoice> getProviders(User user, grafioschtrader.types.AIRequestPurpose purpose) {
          return List.of(new AIProviderChoice(ANTHROPIC, "stub-first"), new AIProviderChoice(OPENAI, "stub-second"));
        }

        @Override
        protected ChatModel getChatModel(AIProviderChoice choice) {
          return prompt -> {
            if (stub.firstFails && choice.provider().equals(ANTHROPIC)) {
              throw new IllegalStateException("401 invalid x-api-key");
            }
            if (stub.firstEmpty && choice.provider().equals(ANTHROPIC)) {
              // As a reasoning model answers that spent the whole output limit on reasoning
              return new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
            }
            return stub.call(prompt);
          };
        }
      };
    }
  }

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Autowired
  private RuleProposalService ruleProposalService;
  @Autowired
  private StubChatModel stub;

  private Integer idTenant;
  private User user;
  private Security aapl;
  private Security ko;
  private Security nesn;
  private Integer idWatchlist;
  private UDFMetadataSecurity field;

  @BeforeAll
  void createFixture() {
    new TransactionTemplate(transactionManager).executeWithoutResult(_ -> {
      Tenant tenant = new Tenant("Rule proposal", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(tenant);
      idTenant = tenant.getId();
      user = new User("rule-proposal@test.local", "unused", "Rule proposal", "en", 0);
      user.setIdTenant(idTenant);
      em.persist(user);
      Assetclass assetclass = em.createQuery("SELECT a FROM Assetclass a", Assetclass.class).setMaxResults(1)
          .getSingleResult();
      aapl = security("Rule proposal Apple", "AAPL", "XNAS", assetclass);
      ko = security("Rule proposal Coca-Cola", "KO", "XNYS", assetclass);
      nesn = security("Rule proposal Nestle", "NESN", "XSWX", assetclass);
      Watchlist watchlist = new Watchlist(idTenant, "Rule proposal");
      watchlist.setSecuritycurrencyList(new ArrayList<>(List.of(aapl, ko, nesn)));
      em.persist(watchlist);
      idWatchlist = watchlist.getIdWatchlist();
      field = new UDFMetadataSecurity(0, 0, user.getIdUser(), null, "Rule proposal link", null,
          UDFDataType.UDF_URLString, null, (byte) 97);
      field.setCategoryTypeEnums(EnumSet.of(assetclass.getCategoryType()));
      field.setSpecialInvestmentInstrumentEnums(EnumSet.of(assetclass.getSpecialInvestmentInstrument()));
      field.setFillSource(UDFFillSource.MANUAL);
      em.persist(field);
    });
  }

  private Security security(String name, String ticker, String mic, Assetclass assetclass) {
    Stockexchange exchange = em.createQuery("SELECT s FROM Stockexchange s WHERE s.mic = ?1", Stockexchange.class)
        .setParameter(1, mic).getSingleResult();
    Security security = new Security(name, "USD", assetclass, exchange, LocalDate.of(2000, 1, 3),
        LocalDate.of(2099, 12, 31), null, ticker, null);
    security.setLeverageFactor(1f);
    em.persist(security);
    return security;
  }

  @AfterAll
  void removeFixture() {
    jdbc.update("DELETE FROM ai_request_log WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM user_entity_change_count WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM udf_data WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM udf_metadata WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM watchlist_sec_cur WHERE id_watchlist = ?", idWatchlist);
    jdbc.update("DELETE FROM watchlist WHERE id_watchlist = ?", idWatchlist);
    for (Security s : List.of(aapl, ko, nesn)) {
      jdbc.update("DELETE FROM security WHERE id_securitycurrency = ?", s.getIdSecuritycurrency());
      jdbc.update("DELETE FROM securitycurrency WHERE id_securitycurrency = ?", s.getIdSecuritycurrency());
    }
    jdbc.update("DELETE FROM user WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM tenant WHERE id_tenant = ?", idTenant);
  }

  @BeforeEach
  void prepareValues() {
    jdbc.update("DELETE FROM ai_request_log WHERE id_user = ?", user.getIdUser());
    stub.firstFails = false;
    stub.firstEmpty = false;
    jdbc.update("DELETE FROM udf_data_fill WHERE id_udf_metadata = ?", field.getIdUDFMetadata());
    jdbc.update("DELETE FROM udf_data WHERE id_user = ?", user.getIdUser());
    setCell(aapl, "https://example.com/aapl");
    setCell(ko, "https://example.com/ko");
    // The value of KO is automatic: it teaches the model nothing and is no example
    jdbc.update("""
        INSERT INTO udf_data_fill (id_udf_metadata, id_entity, fill_source, fill_state, rule_version, filled_value,
        checked_time) VALUES (?, ?, 1, 0, 0, 'https://example.com/ko', '2026-01-01 00:00:00')""",
        field.getIdUDFMetadata(), ko.getIdSecuritycurrency());
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("The preview lists only manual values; the proposed rule is evaluated against all values")
  void proposeRule() {
    RuleProposalPreview preview = ruleProposalService.preview(field.getIdUDFMetadata(), user);
    assertThat(preview.providers()).extracting(RuleProposalPreview.Provider::label)
        .containsExactly("Anthropic (stub-first)", "OpenAI (stub-second)");
    assertThat(preview.examples()).extracting(RuleProposalExample::idSecuritycurrency)
        .containsExactly(aapl.getIdSecuritycurrency());

    stub.answer = "```json\n{\"rule\": \"https://example.com/{ticker|lower}\"}\n```";
    RuleProposalResult result = ruleProposalService.propose(field.getIdUDFMetadata(),
        List.of(aapl.getIdSecuritycurrency()), user);

    assertThat(result.rule()).isEqualTo("https://example.com/{ticker|lower}");
    assertThat(result.parseError()).isNull();
    assertThat(result.testResult().inScope).isEqualTo(3);
    assertThat(result.testResult().equal).isEqualTo(2);
    assertThat(result.testResult().samples).singleElement()
        .satisfies(s -> assertThat(s.computed()).isEqualTo("https://example.com/nesn"));
    assertThat(stub.lastPrompt.getUserMessage().getText()).contains("https://example.com/aapl")
        .doesNotContain("https://example.com/ko");
    assertThat(
        jdbc.queryForObject("SELECT status FROM ai_request_log WHERE id_user = ?", Integer.class, user.getIdUser()))
            .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM udf_data_fill WHERE id_udf_metadata = ?", Integer.class,
        field.getIdUDFMetadata())).isEqualTo(1);
  }

  @Test
  @DisplayName("A failing first provider hands the request to the second; both attempts are logged")
  void fallbackToNextProvider() {
    stub.firstFails = true;
    stub.answer = "{\"rule\": \"https://example.com/{ticker|lower}\"}";
    RuleProposalResult result = ruleProposalService.propose(field.getIdUDFMetadata(),
        List.of(aapl.getIdSecuritycurrency()), user);

    assertThat(result.rule()).isEqualTo("https://example.com/{ticker|lower}");
    assertThat(jdbc.queryForList(
        "SELECT CONCAT(provider, ':', status) FROM ai_request_log WHERE id_user = ? ORDER BY id_ai_request_log",
        String.class, user.getIdUser())).containsExactly("anthropic:2", "openai:1");
  }

  @Test
  @DisplayName("An empty answer of the first provider counts as failure; the second provider answers")
  void emptyAnswerFallsBack() {
    stub.firstEmpty = true;
    stub.answer = "{\"rule\": \"https://example.com/{ticker|lower}\"}";
    RuleProposalResult result = ruleProposalService.propose(field.getIdUDFMetadata(),
        List.of(aapl.getIdSecuritycurrency()), user);

    assertThat(result.rule()).isEqualTo("https://example.com/{ticker|lower}");
    assertThat(jdbc.queryForList(
        "SELECT CONCAT(provider, ':', status) FROM ai_request_log WHERE id_user = ? ORDER BY id_ai_request_log",
        String.class, user.getIdUser())).containsExactly("anthropic:2", "openai:1");
  }

  @Test
  @DisplayName("An unparsable rule and an answer without rule are reported, a security outside the selection refused")
  void unusableAnswers() {
    stub.answer = "{\"rule\": \"https://example.com/{tickr}\"}";
    RuleProposalResult unparsable = ruleProposalService.propose(field.getIdUDFMetadata(),
        List.of(aapl.getIdSecuritycurrency()), user);
    assertThat(unparsable.parseError()).contains("tickr");
    assertThat(unparsable.testResult()).isNull();

    stub.answer = "I cannot help with that.";
    RuleProposalResult noRule = ruleProposalService.propose(field.getIdUDFMetadata(),
        List.of(aapl.getIdSecuritycurrency()), user);
    assertThat(noRule.rule()).isNull();
    assertThat(noRule.parseError()).isNotBlank();

    assertThatThrownBy(
        () -> ruleProposalService.propose(field.getIdUDFMetadata(), List.of(ko.getIdSecuritycurrency()), user))
            .isInstanceOf(SecurityException.class);
  }

  private void setCell(Security security, String value) {
    String key = "f" + field.getIdUDFMetadata();
    jdbc.update("""
        INSERT INTO udf_data (id_user, entity, id_entity, json_values) VALUES (?, 'Security', ?, JSON_OBJECT(?, ?))
        ON DUPLICATE KEY UPDATE json_values = JSON_SET(json_values, CONCAT('$.', ?), ?)""", user.getIdUser(),
        security.getIdSecuritycurrency(), key, value, key, value);
  }
}
