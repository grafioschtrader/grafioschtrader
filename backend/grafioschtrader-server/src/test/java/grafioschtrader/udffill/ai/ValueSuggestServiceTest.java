package grafioschtrader.udffill.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

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
import grafioschtrader.dto.UDFFillProposal;
import grafioschtrader.dto.UDFFillProposalDecision;
import grafioschtrader.dto.UDFFillProposalDecisionResult.Outcome;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFFillState;
import grafioschtrader.udffill.http.FakeHttpTransport;
import grafioschtrader.udffill.http.FakeHttpTransport.Answer;
import grafioschtrader.udffill.http.UDFFillHttp;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Value proposals of a language model in {@code grafioschtrader_t}: runs with a chat model of the test that answers
 * from a queue, the real fetcher on a fake transport serving the recorded pages, and the decisions of the user. No
 * request leaves the instance.
 */
@GTIntegrationTestContext
@Import(ValueSuggestServiceTest.StubConfig.class)
@TestInstance(Lifecycle.PER_CLASS)
class ValueSuggestServiceTest {

  private static final String TER_URL = "https://www.fund.example/ter";
  private static final String FINAL_ANSWER = """
      {"value": "0.20", "sourceUrl": "https://www.fund.example/ter", "excerpt": "Total Expense Ratio 0.20%",
       "asOfDate": "2026-09-30"}""";

  /** Chat model of the test: answers the queued messages in order and counts its calls. */
  static class QueueChatModel implements ChatModel {
    final Deque<AssistantMessage> answers = new ConcurrentLinkedDeque<>();
    final AtomicInteger calls = new AtomicInteger();

    @Override
    public ChatResponse call(Prompt prompt) {
      calls.incrementAndGet();
      AssistantMessage next = answers.poll();
      return new ChatResponse(List.of(new Generation(next == null ? new AssistantMessage("{\"value\": null}") : next)));
    }
  }

  /** Imported, not annotated: GTforTest scans the test packages, so an annotated class would reach every context. */
  static class StubConfig {
    @Bean
    QueueChatModel queueChatModel() {
      return new QueueChatModel();
    }

    @Bean
    FakeHttpTransport fakeHttpTransport() {
      return new FakeHttpTransport(uri -> uri.getPath().equals("/ter") ? page() : null);
    }

    @Bean
    @Primary
    UDFFillHttp fakeUdfFillHttp(FakeHttpTransport transport) {
      return new UDFFillHttp(_ -> new InetAddress[] { InetAddress.ofLiteral("93.184.215.14") }, transport, "test");
    }

    @Bean
    @Primary
    AIChatModelProvider stubAIChatModelProvider(ConnectorApiKeyJpaRepository repository,
        GlobalparametersService globalparametersService, AIRequestGate aiRequestGate, QueueChatModel stub) {
      return new AIChatModelProvider(repository, globalparametersService, aiRequestGate, null, null) {
        @Override
        public List<AIProviderChoice> getProviders(User user, grafioschtrader.types.AIRequestPurpose purpose) {
          return List.of(new AIProviderChoice(ANTHROPIC, "stub-model"));
        }

        @Override
        protected ChatModel getChatModel(AIProviderChoice choice) {
          return stub;
        }
      };
    }

    private static Answer page() {
      try (InputStream in = ValueSuggestServiceTest.class.getResourceAsStream("/udffill/pages/ter-fund-page.html")) {
        return new Answer(200, "text/html; charset=utf-8", null, null, in.readAllBytes());
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Autowired
  private ValueSuggestService valueSuggestService;
  @Autowired
  private QueueChatModel stub;
  @Autowired
  private FakeHttpTransport transport;

  private Integer idTenant;
  private User user;
  private Security fund;
  private Security example;
  private Integer idWatchlist;
  private UDFMetadataSecurity field;

  @BeforeAll
  void createFixture() {
    new TransactionTemplate(transactionManager).executeWithoutResult(_ -> {
      Tenant tenant = new Tenant("Value suggest", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(tenant);
      idTenant = tenant.getId();
      user = new User("value-suggest@test.local", "unused", "Value suggest", "en", 0);
      user.setIdTenant(idTenant);
      em.persist(user);
      Assetclass assetclass = em.createQuery("SELECT a FROM Assetclass a", Assetclass.class).setMaxResults(1)
          .getSingleResult();
      fund = security("Value suggest MSCI World (Acc)", "IE00B4L5Y983", assetclass);
      example = security("Value suggest S&P 500", "IE00BK5BQT80", assetclass);
      Watchlist watchlist = new Watchlist(idTenant, "Value suggest");
      watchlist.setSecuritycurrencyList(new ArrayList<>(List.of(fund, example)));
      em.persist(watchlist);
      idWatchlist = watchlist.getIdWatchlist();
      field = new UDFMetadataSecurity(0, 0, user.getIdUser(), null, "Value suggest TER", null, UDFDataType.UDF_Numeric,
          "10,4", (byte) 98);
      field.setCategoryTypeEnums(EnumSet.of(assetclass.getCategoryType()));
      field.setSpecialInvestmentInstrumentEnums(EnumSet.of(assetclass.getSpecialInvestmentInstrument()));
      field.setFillSource(UDFFillSource.LLM);
      field.setFillRule("Total expense ratio in percent from the issuer's fund page or KID");
      em.persist(field);
    });
  }

  private Security security(String name, String isin, Assetclass assetclass) {
    Stockexchange exchange = em.createQuery("SELECT s FROM Stockexchange s", Stockexchange.class).setMaxResults(1)
        .getSingleResult();
    Security security = new Security(name, "CHF", assetclass, exchange, LocalDate.of(2000, 1, 3),
        LocalDate.of(2099, 12, 31), null, null, isin);
    security.setLeverageFactor(1f);
    em.persist(security);
    return security;
  }

  @AfterAll
  void removeFixture() {
    jdbc.update("DELETE FROM task_data_change WHERE id_task = ? AND id_entity = ?",
        TaskTypeExtended.UDF_USER_SUGGEST_VALUES.getValue(), field.getIdUDFMetadata());
    jdbc.update("DELETE FROM ai_request_log WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM user_entity_change_count WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM udf_data WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM udf_metadata WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM watchlist_sec_cur WHERE id_watchlist = ?", idWatchlist);
    jdbc.update("DELETE FROM watchlist WHERE id_watchlist = ?", idWatchlist);
    for (Security s : List.of(fund, example)) {
      jdbc.update("DELETE FROM security WHERE id_securitycurrency = ?", s.getIdSecuritycurrency());
      jdbc.update("DELETE FROM securitycurrency WHERE id_securitycurrency = ?", s.getIdSecuritycurrency());
    }
    jdbc.update("DELETE FROM user WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM tenant WHERE id_tenant = ?", idTenant);
  }

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM ai_request_log WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM udf_data_fill WHERE id_udf_metadata = ?", field.getIdUDFMetadata());
    jdbc.update("DELETE FROM udf_data WHERE id_user = ?", user.getIdUser());
    jdbc.update("UPDATE udf_metadata_security SET fill_source = 3, fill_rule_version = 0 WHERE id_udf_metadata = ?",
        field.getIdUDFMetadata());
    setCell(example, "0.07");
    stub.answers.clear();
    stub.calls.set(0);
    transport.getRequests().clear();
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("A run proposes a verified value; a second run does not ask again for a proposal of the version")
  void runProposesOnceAndDoesNotAskAgain() {
    stub.answers.add(new AssistantMessage(FINAL_ANSWER));

    valueSuggestService.runField(field.getIdUDFMetadata());

    assertThat(stub.calls).hasValue(1);
    assertThat(cell(fund)).isNull();
    assertThat(jdbc.queryForMap("SELECT fill_state, filled_value, evidence, as_of_date FROM udf_data_fill "
        + "WHERE id_udf_metadata = ? AND id_entity = ?", field.getIdUDFMetadata(), fund.getIdSecuritycurrency()))
            .containsEntry("filled_value", "0.2").containsEntry("evidence", "Total Expense Ratio 0.20%")
            .containsEntry("as_of_date", java.sql.Date.valueOf("2026-09-30"))
            .satisfies(row -> assertThat(((Number) row.get("fill_state")).byteValue())
                .isEqualTo(UDFFillState.PROPOSED.getValue()));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_request_log WHERE id_user = ? AND purpose = 2",
        Integer.class, user.getIdUser())).isEqualTo(1);
    assertThat(valueSuggestService.getProposals(field.getIdUDFMetadata(), user)).extracting(UDFFillProposal::value)
        .containsExactly("0.2");

    valueSuggestService.runField(field.getIdUDFMetadata());
    assertThat(stub.calls).hasValue(1);
  }

  @Test
  @DisplayName("A fetchPage call of the model is executed by the application through the public source fetcher")
  void toolCallIsExecutedHere() {
    stub.answers.add(AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall("call-1",
        "function", ValueSuggestService.FETCH_PAGE, "{\"url\": \"" + TER_URL + "\"}"))).build());
    stub.answers.add(new AssistantMessage(FINAL_ANSWER));

    valueSuggestService.runField(field.getIdUDFMetadata());

    assertThat(stub.calls).hasValue(2);
    assertThat(transport.getRequests()).extracting(r -> r.uri().toString()).contains(TER_URL);
    assertThat(fillState(fund)).isEqualTo(UDFFillState.PROPOSED);
    assertThat(
        jdbc.queryForObject("SELECT COUNT(*) FROM ai_request_log WHERE id_user = ?", Integer.class, user.getIdUser()))
            .isEqualTo(2);
  }

  @Test
  @DisplayName("Accept writes the value once; accept after a manual value conflicts; after a context change it is stale")
  void acceptOutcomes() {
    UDFFillProposalDecision decision = new UDFFillProposalDecision(fund.getIdSecuritycurrency(), 0, "0.2");

    insertProposal(0);
    assertThat(decide(decision, true)).isEqualTo(Outcome.APPLIED);
    assertThat(decide(decision, true)).isEqualTo(Outcome.APPLIED);
    assertThat(cell(fund)).isEqualTo("0.2");
    assertThat(fillState(fund)).isEqualTo(UDFFillState.APPLIED);

    reset();
    insertProposal(0);
    setCell(fund, "0.25");
    assertThat(decide(decision, true)).isEqualTo(Outcome.CONFLICT);
    assertThat(cell(fund)).isEqualTo("0.25");
    assertThat(fillState(fund)).isNull();

    reset();
    insertProposal(0);
    jdbc.update("UPDATE udf_metadata_security SET fill_rule_version = 1 WHERE id_udf_metadata = ?",
        field.getIdUDFMetadata());
    assertThat(decide(decision, true)).isEqualTo(Outcome.STALE);
    assertThat(cell(fund)).isNull();
  }

  @Test
  @DisplayName("Reject is idempotent, and retry lets the next run ask again")
  void rejectAndRetry() {
    insertProposal(0);
    UDFFillProposalDecision decision = new UDFFillProposalDecision(fund.getIdSecuritycurrency(), 0, "0.2");
    assertThat(decide(decision, false)).isEqualTo(Outcome.REJECTED);
    assertThat(decide(decision, false)).isEqualTo(Outcome.REJECTED);
    assertThat(fillState(fund)).isEqualTo(UDFFillState.REJECTED);

    valueSuggestService.runField(field.getIdUDFMetadata());
    assertThat(stub.calls).hasValue(0);

    assertThat(valueSuggestService.retry(field.getIdUDFMetadata(), user)).isEqualTo(1);
    stub.answers.add(new AssistantMessage(FINAL_ANSWER));
    valueSuggestService.runField(field.getIdUDFMetadata());
    assertThat(stub.calls).hasValue(1);
    assertThat(fillState(fund)).isEqualTo(UDFFillState.PROPOSED);
  }

  @Test
  @DisplayName("Starting a run enqueues one task for the field")
  void enqueue() {
    valueSuggestService.enqueue(field.getIdUDFMetadata(), user);
    valueSuggestService.enqueue(field.getIdUDFMetadata(), user);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_data_change WHERE id_task = ? AND id_entity = ?",
        Integer.class, TaskTypeExtended.UDF_USER_SUGGEST_VALUES.getValue(), field.getIdUDFMetadata())).isEqualTo(1);
  }

  private Outcome decide(UDFFillProposalDecision decision, boolean accept) {
    return valueSuggestService.decide(field.getIdUDFMetadata(), List.of(decision), accept, user).getFirst().outcome();
  }

  private void insertProposal(int ruleVersion) {
    jdbc.update("""
        INSERT INTO udf_data_fill (id_udf_metadata, id_entity, fill_source, fill_state, rule_version, filled_value,
        source_url, checked_time, evidence) VALUES (?, ?, 3, 3, ?, '0.2', ?, '2026-10-01 00:00:00',
        'Total Expense Ratio 0.20%')""", field.getIdUDFMetadata(), fund.getIdSecuritycurrency(), ruleVersion, TER_URL);
  }

  private void setCell(Security security, String value) {
    String key = "f" + field.getIdUDFMetadata();
    jdbc.update("""
        INSERT INTO udf_data (id_user, entity, id_entity, json_values) VALUES (?, 'Security', ?, JSON_OBJECT(?, ?))
        ON DUPLICATE KEY UPDATE json_values = JSON_SET(json_values, CONCAT('$.', ?), ?)""", user.getIdUser(),
        security.getIdSecuritycurrency(), key, value, key, value);
  }

  private String cell(Security security) {
    List<String> values = jdbc.queryForList(
        "SELECT JSON_VALUE(json_values, CONCAT('$.f', ?)) FROM udf_data WHERE id_user = ? AND entity = 'Security' AND id_entity = ?",
        String.class, field.getIdUDFMetadata(), user.getIdUser(), security.getIdSecuritycurrency());
    return values.isEmpty() ? null : values.getFirst();
  }

  private UDFFillState fillState(Security security) {
    List<Byte> states = jdbc.queryForList(
        "SELECT fill_state FROM udf_data_fill WHERE id_udf_metadata = ? AND id_entity = ?", Byte.class,
        field.getIdUDFMetadata(), security.getIdSecuritycurrency());
    return states.isEmpty() ? null : UDFFillState.getByValue(states.getFirst());
  }
}
