package grafioschtrader.udffill.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.UDFMetadataSubscription;
import grafiosch.entities.User;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafiosch.security.UserAuthentication;
import grafiosch.types.UDFDataType;
import grafiosch.types.UDFShareMode;
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
import grafioschtrader.types.AIRequestPurpose;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.UDFFillPeriod;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFFillState;
import grafioschtrader.udffill.UDFFillScopeService;
import grafioschtrader.udffill.ai.ValueSuggestServiceTest.QueueChatModel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * A shared language model field in {@code grafioschtrader_t}: who pays for a run, which securities a run asks for and
 * which proposals a participant may see and decide. The chat model of the test answers "not found" and counts its
 * calls; the request log of the budget gate names the user every call was charged to. No request leaves the instance.
 */
@GTIntegrationTestContext
@Import(UDFSharedSuggestTest.PayerConfig.class)
@TestPropertySource(properties = { "g.background.worker.enabled=false", "gt.startup.price.update.task=false" })
class UDFSharedSuggestTest {

  /** The stubs of {@link ValueSuggestServiceTest}, with a provider that has no language model for chosen users. */
  static class PayerConfig extends ValueSuggestServiceTest.StubConfig {
    static final Set<Integer> WITHOUT_MODEL = ConcurrentHashMap.newKeySet();

    @Bean
    @Primary
    @Override
    AIChatModelProvider stubAIChatModelProvider(ConnectorApiKeyJpaRepository repository,
        GlobalparametersService globalparametersService, AIRequestGate aiRequestGate, QueueChatModel stub) {
      return new AIChatModelProvider(repository, globalparametersService, aiRequestGate, null, null) {
        @Override
        public List<AIProviderChoice> getProviders(User user, AIRequestPurpose purpose) {
          return WITHOUT_MODEL.contains(user.getIdUser()) ? List.of()
              : List.of(new AIProviderChoice(ANTHROPIC, "stub-model"));
        }

        @Override
        public boolean isValueSuggestionAvailable(User user) {
          return !WITHOUT_MODEL.contains(user.getIdUser());
        }

        @Override
        protected ChatModel getChatModel(AIProviderChoice choice) {
          return stub;
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
  private ValueSuggestService valueSuggestService;
  @Autowired
  private UDFFillScopeService scopeService;
  @Autowired
  private QueueChatModel stub;

  private User owner;
  private User reader;
  private Security common;
  private Security ownerOnly;
  private Security readerOnly;
  private UDFMetadataSecurity field;
  private UDFMetadataSubscription subscription;
  private final List<Integer> watchlists = new ArrayList<>();

  @BeforeEach
  void fixture() {
    new TransactionTemplate(transactionManager).executeWithoutResult(_ -> {
      owner = user("owner");
      reader = user("reader");
      Assetclass assetclass = em.createQuery("SELECT a FROM Assetclass a", Assetclass.class).setMaxResults(1)
          .getSingleResult();
      Stockexchange exchange = em.createQuery("SELECT s FROM Stockexchange s", Stockexchange.class).setMaxResults(1)
          .getSingleResult();
      common = security("COMMON", assetclass, exchange);
      ownerOnly = security("OWN", assetclass, exchange);
      readerOnly = security("READER", assetclass, exchange);
      watchlist(owner, common, ownerOnly);
      watchlist(reader, common, readerOnly);
      field = new UDFMetadataSecurity(0, 0, owner.getIdUser(), null, "UDF shared suggest test", null,
          UDFDataType.UDF_Numeric, "10,4", (byte) 92);
      field.setCategoryTypeEnums(EnumSet.of(assetclass.getCategoryType()));
      field.setSpecialInvestmentInstrumentEnums(EnumSet.of(assetclass.getSpecialInvestmentInstrument()));
      field.setFillSource(UDFFillSource.LLM);
      field.setFillRule("Total expense ratio in percent from the issuer's fund page");
      field.setShareMode(UDFShareMode.UDF_SHARE_READ);
      em.persist(field);
      subscription = new UDFMetadataSubscription();
      subscription.setIdUDFMetadata(field.getId());
      subscription.setIdUser(reader.getIdUser());
      subscription.setUiOrder((byte) 92);
      em.persist(subscription);
    });
    PayerConfig.WITHOUT_MODEL.clear();
    stub.answers.clear();
    stub.calls.set(0);
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(owner));
  }

  private User user(String name) {
    Tenant tenant = new Tenant("UDF shared suggest " + name, "CHF", 0, TenantKindType.MAIN, false);
    em.persist(tenant);
    User user = new User("udf-shared-suggest-" + name + "@test.local", "unused", "UDF shared suggest " + name, "en", 0);
    user.setIdTenant(tenant.getId());
    em.persist(user);
    return user;
  }

  private Security security(String ticker, Assetclass assetclass, Stockexchange exchange) {
    Security security = new Security("UDF shared suggest " + ticker, "CHF", assetclass, exchange,
        LocalDate.of(2000, 1, 3), LocalDate.of(2099, 12, 31), null, ticker, null);
    security.setLeverageFactor(1f);
    em.persist(security);
    return security;
  }

  private void watchlist(User user, Security... securities) {
    Watchlist watchlist = new Watchlist(user.getIdTenant(), "UDF shared suggest");
    watchlist.setSecuritycurrencyList(new ArrayList<>(List.of(securities)));
    em.persist(watchlist);
    watchlists.add(watchlist.getIdWatchlist());
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    PayerConfig.WITHOUT_MODEL.clear();
    if (field == null) {
      return;
    }
    jdbc.update("DELETE FROM task_data_change WHERE id_task = ? AND id_entity IN (?, ?)",
        TaskTypeExtended.UDF_USER_SUGGEST_VALUES.getValue(), field.getId(), subscription.getId());
    jdbc.update("DELETE FROM udf_metadata WHERE id_udf_metadata = ?", field.getId());
    for (User user : List.of(owner, reader)) {
      jdbc.update("DELETE FROM ai_request_log WHERE id_user = ?", user.getIdUser());
      jdbc.update("DELETE FROM udf_data WHERE id_user = ?", user.getIdUser());
      jdbc.update("DELETE FROM user_entity_change_count WHERE id_user = ?", user.getIdUser());
    }
    for (Integer id : watchlists) {
      jdbc.update("DELETE FROM watchlist_sec_cur WHERE id_watchlist = ?", id);
      jdbc.update("DELETE FROM watchlist WHERE id_watchlist = ?", id);
    }
    for (Security security : List.of(common, ownerOnly, readerOnly)) {
      jdbc.update("DELETE FROM security WHERE id_securitycurrency = ?", security.getId());
      jdbc.update("DELETE FROM securitycurrency WHERE id_securitycurrency = ?", security.getId());
    }
    for (User user : List.of(owner, reader)) {
      jdbc.update("DELETE FROM user WHERE id_user = ?", user.getIdUser());
      jdbc.update("DELETE FROM tenant WHERE id_tenant = ?", user.getIdTenant());
    }
  }

  private int requestsOf(User user) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM ai_request_log WHERE id_user = ?", Integer.class,
        user.getIdUser());
  }

  private List<Integer> askedSecurities() {
    return jdbc.queryForList("SELECT id_entity FROM udf_data_fill WHERE id_udf_metadata = ? ORDER BY id_entity",
        Integer.class, field.getId());
  }

  private int waitingRuns(String entity, Integer idEntity) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM task_data_change WHERE id_task = ? AND entity = ? AND id_entity = ?", Integer.class,
        TaskTypeExtended.UDF_USER_SUGGEST_VALUES.getValue(), entity, idEntity);
  }

  private void insertFill(Security security, UDFFillState state) {
    jdbc.update("""
        INSERT INTO udf_data_fill (id_udf_metadata, id_entity, fill_source, fill_state, rule_version, filled_value,
        source_url, checked_time, evidence) VALUES (?, ?, 3, ?, 0, '0.2', 'https://www.fund.example/ter',
        '2026-10-01 00:00:00', 'Total Expense Ratio 0.20%')""", field.getId(), security.getId(), state.getValue());
  }

  @Test
  @DisplayName("Each participant pays for the own securities only, and a cell asked once is not asked again")
  void everyRunAsksItsPayersScopeAtItsPayersCost() {
    assertThat(valueSuggestService.runSubscription(subscription.getId())).isNull();

    assertThat(stub.calls).hasValue(2);
    assertThat(requestsOf(reader)).isEqualTo(2);
    assertThat(requestsOf(owner)).isZero();
    assertThat(askedSecurities()).containsExactlyInAnyOrder(common.getId(), readerOnly.getId());

    assertThat(valueSuggestService.runField(field.getId())).isNull();

    assertThat(stub.calls).hasValue(3);
    assertThat(requestsOf(owner)).isEqualTo(1);
    assertThat(requestsOf(reader)).isEqualTo(2);
    assertThat(askedSecurities()).containsExactlyInAnyOrder(common.getId(), ownerOnly.getId(), readerOnly.getId());
  }

  @Test
  @DisplayName("The periodic run enqueues the owner and only the adopting users that have a language model")
  void periodicRunsSkipAParticipantWithoutModel() {
    jdbc.update("UPDATE udf_metadata_security SET fill_period = ? WHERE id_udf_metadata = ?",
        UDFFillPeriod.FILL_PERIOD_MONTHLY.getValue(), field.getId());
    PayerConfig.WITHOUT_MODEL.add(reader.getIdUser());

    valueSuggestService.enqueuePeriodic(null);

    assertThat(waitingRuns(UDFMetadataSecurity.class.getSimpleName(), field.getId())).isEqualTo(1);
    assertThat(waitingRuns(UDFMetadataSubscription.class.getSimpleName(), subscription.getId())).isZero();

    PayerConfig.WITHOUT_MODEL.clear();
    valueSuggestService.enqueuePeriodic(reader.getIdTenant());

    assertThat(waitingRuns(UDFMetadataSecurity.class.getSimpleName(), field.getId())).isEqualTo(1);
    assertThat(waitingRuns(UDFMetadataSubscription.class.getSimpleName(), subscription.getId())).isEqualTo(1);
  }

  @Test
  @DisplayName("A participant starts only the own run, and nothing asks for a field without owner")
  void startedRunBelongsToTheCallerAndAnOrphanedFieldIsNotAsked() {
    valueSuggestService.enqueue(field.getId(), reader);
    assertThat(waitingRuns(UDFMetadataSubscription.class.getSimpleName(), subscription.getId())).isEqualTo(1);
    assertThat(waitingRuns(UDFMetadataSecurity.class.getSimpleName(), field.getId())).isZero();

    jdbc.update("UPDATE udf_metadata SET id_user = NULL WHERE id_udf_metadata = ?", field.getId());

    assertThat(valueSuggestService.runField(field.getId())).isNull();
    assertThat(valueSuggestService.runSubscription(subscription.getId())).isNull();
    assertThat(stub.calls).hasValue(0);
    assertThatExceptionOfType(SecurityException.class)
        .isThrownBy(() -> valueSuggestService.getProposals(field.getId(), reader));
  }

  @Test
  @DisplayName("An adopting user sees and decides only proposals of the own securities, also with the share mode read")
  void proposalsAreLimitedToTheCallersScope() {
    for (Security security : List.of(common, ownerOnly, readerOnly)) {
      insertFill(security, UDFFillState.PROPOSED);
    }

    assertThat(valueSuggestService.getProposals(field.getId(), reader)).extracting(UDFFillProposal::idSecuritycurrency)
        .containsExactlyInAnyOrder(common.getId(), readerOnly.getId());
    assertThat(valueSuggestService.getProposals(field.getId(), owner)).extracting(UDFFillProposal::idSecuritycurrency)
        .containsExactlyInAnyOrder(common.getId(), ownerOnly.getId());

    assertThatExceptionOfType(SecurityException.class).isThrownBy(() -> valueSuggestService.decide(field.getId(),
        List.of(new UDFFillProposalDecision(readerOnly.getId(), 0, "0.2"),
            new UDFFillProposalDecision(ownerOnly.getId(), 0, "0.2")),
        true, reader));
    assertThat(scopeService.getFieldValues(field)).isEmpty();

    assertThat(valueSuggestService
        .decide(field.getId(), List.of(new UDFFillProposalDecision(readerOnly.getId(), 0, "0.2")), true, reader)
        .getFirst().outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(scopeService.getFieldValues(field)).containsEntry(readerOnly.getId(), "0.2").hasSize(1);

    jdbc.update("UPDATE udf_data_fill SET fill_state = ? WHERE id_udf_metadata = ? AND id_entity IN (?, ?)",
        UDFFillState.REJECTED.getValue(), field.getId(), common.getId(), ownerOnly.getId());
    assertThat(valueSuggestService.retry(field.getId(), reader)).isEqualTo(1);
    assertThat(askedSecurities()).containsExactlyInAnyOrder(ownerOnly.getId(), readerOnly.getId());
  }
}
