package grafioschtrader.udffill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.Role;
import grafiosch.entities.UDFDataShared;
import grafiosch.entities.UDFMetadataSubscription;
import grafiosch.entities.User;
import grafiosch.exportdelete.MySqlDeleteMyData;
import grafiosch.repository.UDFMetadataSubscriptionJpaRepository;
import grafiosch.security.JwtTokenHandler;
import grafiosch.security.UserAuthentication;
import grafiosch.types.UDFDataType;
import grafiosch.types.UDFShareMode;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.UDFDataFill;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.exportdelete.MyDataExportDeleteDefinition;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.rest.RequestGTMappings;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFFillState;
import grafioschtrader.udffill.UDFFillOutcome.Value;
import grafioschtrader.udffill.resolver.ResolveOutcome.Found;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Committed fixtures exercise the writer's independent transactions and the real sharing callbacks. */
@GTIntegrationTestContext
@Import(UDFFillResolverRunTest.ResolverConfig.class)
@TestPropertySource(properties = { "g.background.worker.enabled=false", "gt.startup.price.update.task=false" })
class UDFSharedFillTest {
  @PersistenceContext
  private EntityManager em;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Autowired
  private UDFFillWriter writer;
  @Autowired
  private UDFFillService service;
  @Autowired
  private UDFFillScopeService scope;
  @Autowired
  private UDFMetadataSecurityJpaRepository fields;
  @Autowired
  private UDFMetadataSubscriptionJpaRepository subscriptions;
  @Autowired
  private UDFFillTaskQueue queue;
  @Autowired
  private UDFFillResolverRunTest.CountingResolver resolver;
  @Autowired
  private RestTestClient client;
  @Autowired
  private JwtTokenHandler tokens;

  private TransactionTemplate tx;
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
    tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(_ -> {
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
      field = new UDFMetadataSecurity(0, 0, owner.getIdUser(), null, "UDF shared fill test", null,
          UDFDataType.UDF_URLString, null, (byte) 91);
      field.setCategoryTypeEnums(EnumSet.of(assetclass.getCategoryType()));
      field.setSpecialInvestmentInstrumentEnums(EnumSet.of(assetclass.getSpecialInvestmentInstrument()));
      field.setFillSource(UDFFillSource.TEMPLATE);
      field.setFillRule("https://example.com/{ticker}");
      field.setShareMode(UDFShareMode.UDF_SHARE_READ_WRITE);
      em.persist(field);
      subscription = new UDFMetadataSubscription();
      subscription.setIdUDFMetadata(field.getId());
      subscription.setIdUser(reader.getIdUser());
      subscription.setUiOrder((byte) 91);
      em.persist(subscription);
    });
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(owner));
    resolver.next = new Found("https://example.com/lookup", null);
    resolver.calls.set(0);
  }

  private User user(String name) {
    Tenant tenant = new Tenant("UDF shared fill " + name, "CHF", 0, TenantKindType.MAIN, false);
    em.persist(tenant);
    User user = new User("udf-shared-fill-" + name + "@test.local", "unused", "UDF shared fill " + name, "en", 0);
    user.setIdTenant(tenant.getId());
    user.setEnabled(true);
    user.setRoles(new ArrayList<>(em.createQuery("SELECT r FROM Role r WHERE r.rolename IN (?1, ?2)", Role.class)
        .setParameter(1, Role.ROLE_ALL_EDIT).setParameter(2, Role.ROLE_USER).getResultList()));
    em.persist(user);
    return user;
  }

  private Security security(String ticker, Assetclass assetclass, Stockexchange exchange) {
    Security security = new Security("UDF shared fill " + ticker, "CHF", assetclass, exchange, LocalDate.of(2000, 1, 3),
        LocalDate.of(2099, 12, 31), null, ticker, null);
    security.setLeverageFactor(1f);
    em.persist(security);
    return security;
  }

  private void watchlist(User user, Security... securities) {
    Watchlist watchlist = new Watchlist(user.getIdTenant(), "UDF shared fill");
    watchlist.setSecuritycurrencyList(new ArrayList<>(List.of(securities)));
    em.persist(watchlist);
    watchlists.add(watchlist.getIdWatchlist());
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    if (field == null)
      return;
    jdbc.update("DELETE FROM task_data_change WHERE entity = 'UDFMetadataSecurity' AND id_entity = ?", field.getId());
    jdbc.update("DELETE FROM udf_metadata WHERE id_udf_metadata = ?", field.getId());
    for (User user : List.of(owner, reader)) {
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
      jdbc.update("DELETE FROM user_role WHERE id_user = ?", user.getIdUser());
      jdbc.update("DELETE FROM user WHERE id_user = ?", user.getIdUser());
      jdbc.update("DELETE FROM tenant WHERE id_tenant = ?", user.getIdTenant());
    }
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id_udf_metadata = ?", Integer.class,
        field.getId());
  }

  @Test
  void fillsTheUnionOnceAndManualValuesWin() {
    assertThat(scope.getFieldScope(field)).extracting(Security::getId).containsExactly(common.getId(),
        ownerOnly.getId(), readerOnly.getId());
    service.runField(field.getId());
    assertThat(scope.getFieldValues(field)).containsEntry(readerOnly.getId(), "https://example.com/READER").hasSize(3);
    jdbc.update(
        "UPDATE udf_data_shared SET field_value = 'https://example.com/manual' WHERE id_udf_metadata = ? AND id_entity = ?",
        field.getId(), readerOnly.getId());
    assertThat(writer.write(field, readerOnly.getId(), new Value("https://example.com/new", null)))
        .isEqualTo(UDFFillWriteResult.MANUAL);
    assertThat(scope.getFieldValues(field).get(readerOnly.getId())).isEqualTo("https://example.com/manual");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM udf_data WHERE id_user IN (?, ?)", Integer.class,
        owner.getIdUser(), reader.getIdUser())).isZero();
  }

  @Test
  void resolverFillsTheParticipantUnionWithoutDuplicateRequests() {
    jdbc.update("UPDATE udf_metadata_security SET fill_source = ?, fill_rule = ? WHERE id_udf_metadata = ?",
        UDFFillSource.RESOLVER.getValue(), UDFFillResolverRunTest.CountingResolver.ID + " x", field.getId());
    service.runField(field.getId());
    assertThat(resolver.calls).hasValue(3);
    assertThat(scope.getFieldValues(field)).hasSize(3).containsValue("https://example.com/lookup");
    service.runField(field.getId());
    assertThat(resolver.calls).hasValue(3);
  }

  @Test
  void unsubscribingQueuesCleanupAndRemovesOnlyTheDepartingScope() {
    service.runField(field.getId());
    subscriptions.delEntityWithUserId(subscription.getId(), reader.getIdUser());
    assertThat(queue.isWaiting(field.getId())).isTrue();
    service.runField(field.getId());
    assertThat(scope.getFieldValues(field)).hasSize(2).doesNotContainKey(readerOnly.getId());
    assertThat(count(UDFDataFill.TABNAME)).isEqualTo(2);
  }

  private UDFMetadataSecurity changeMode(UDFShareMode mode) {
    return tx.execute(_ -> {
      UDFMetadataSecurity current = fields.findById(field.getId()).orElseThrow();
      UDFMetadataSecurity draft = new UDFMetadataSecurity(current.getCategoryTypes(),
          current.getSpecialInvestmentInstruments(), owner.getIdUser(), null, current.getDescription(), null,
          current.getUdfDataType(), current.getFieldSize(), current.getUiOrder());
      draft.setFillSource(current.getFillSource());
      draft.setFillRule(current.getFillRule());
      draft.setShareMode(mode);
      try {
        return fields.saveOnlyAttributes(draft, current, fields.getUpdatePropertyLevels(current));
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
  }

  @Test
  void storageTransitionsPreserveAutomaticValuesAndFillRows() {
    subscriptions.delEntityWithUserId(subscription.getId(), reader.getIdUser());
    field = changeMode(UDFShareMode.UDF_SHARE_NONE);
    service.runField(field.getId());
    String filled = scope.getFieldValues(field).get(common.getId());
    field = changeMode(UDFShareMode.UDF_SHARE_READ);
    assertThat(count(UDFDataFill.TABNAME)).isEqualTo(2);
    assertThat(count(UDFDataShared.TABNAME)).isEqualTo(2);
    assertThat(writer.write(field, common.getId(), new Value(filled, null))).isEqualTo(UDFFillWriteResult.UNCHANGED);
    field = changeMode(UDFShareMode.UDF_SHARE_NONE);
    assertThat(count(UDFDataShared.TABNAME)).isZero();
    assertThat(scope.getFieldValues(field).get(common.getId())).isEqualTo(filled);
    assertThat(writer.write(field, common.getId(), new Value(filled, null))).isEqualTo(UDFFillWriteResult.UNCHANGED);
  }

  @Test
  void orphanedFieldsAreFrozenInEveryWriterPathAndCanBeClaimed() {
    service.runField(field.getId());
    fields.delEntityWithUserId(field.getId(), owner.getIdUser());
    service.runField(field.getId());
    assertThat(writer.write(field, common.getId(), new Value("https://example.com/changed", null)))
        .isEqualTo(UDFFillWriteResult.STALE);
    writer.lock(field, common.getId());
    writer.deleteFillRow(field, common.getId());
    writer.removeOutOfScope(field, common.getId());
    assertThat(count(UDFDataFill.TABNAME)).isEqualTo(3);
    assertThat(count(UDFDataShared.TABNAME)).isEqualTo(3);
    assertThat(scope.getFieldValues(field).get(common.getId())).isEqualTo("https://example.com/COMMON");
    subscriptions.claimOwnership(subscription.getId(), reader);
    assertThat(queue.isWaiting(field.getId())).isTrue();
    assertThat(fields.findById(field.getId()).orElseThrow().getIdUser()).isEqualTo(reader.getIdUser());
  }

  @Test
  void watchlistLoadFillsASubscribedTemplateAndReturnsItsFillMarks() throws Exception {
    jdbc.update("DELETE FROM udf_metadata_subscription WHERE id_udf_metadata_subscription = ?", subscription.getId());
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(reader));
    var adopted = new UDFMetadataSubscription();
    adopted.setIdUser(reader.getIdUser());
    adopted.setIdUDFMetadata(field.getId());
    adopted.setUiOrder((byte) 91);
    subscriptions.saveOnlyAttributes(adopted, null, subscriptions.getUpdatePropertyLevels(null));
    assertThat(queue.isWaiting(field.getId())).isTrue();
    service.fillWatchlistTemplates(reader, List.of(readerOnly), Map::of);
    assertThat(scope.getFieldValues(field)).containsEntry(readerOnly.getId(), "https://example.com/READER");
    var marks = service.getFillInfo(reader, java.util.Set.of(readerOnly.getId()),
        Map.of(readerOnly.getId(), Map.of("f" + field.getId(), "https://example.com/READER")));
    assertThat(marks.get(readerOnly.getId()).get("f" + field.getId()).fillState()).isEqualTo(UDFFillState.APPLIED);
  }

  @Test
  void exportIncludesSubscriberFillRowsAndOwnerAccountDeletionKeepsThem() {
    service.runField(field.getId());
    var definition = Arrays.stream(MyDataExportDeleteDefinition.exportDefinitions)
        .filter(d -> d.table.equals(UDFDataFill.TABNAME) && d.isExport()).findFirst().orElseThrow();
    var exported = jdbc.queryForList("SELECT " + definition.sqlStatement, reader.getIdUser(), reader.getIdUser());
    assertThat(exported).hasSize(3);
    new MySqlDeleteMyData(jdbc, owner).deleteMyData();
    assertThat(fields.findById(field.getId()).orElseThrow().getIdUser()).isNull();
    assertThat(count(UDFDataFill.TABNAME)).isEqualTo(3);
    assertThat(count(UDFDataShared.TABNAME)).isEqualTo(3);
  }

  private RestTestClient asReader() {
    return client.mutate().defaultHeader("x-auth-token", tokens.createTokenForUser(reader, 5)).build();
  }

  @Test
  void restEnforcesWriteRightsAndReportsOnlyTheCallersScope() {
    service.runField(field.getId());
    field = changeMode(UDFShareMode.UDF_SHARE_READ);
    String path = RequestGTMappings.UDF_METADATA_SECURITY_MAP + "/" + field.getId();
    asReader().get().uri(path + "/fillstatus").exchange().expectStatus().isOk().expectBody().jsonPath("$.inScope")
        .isEqualTo(2).jsonPath("$.countByState.APPLIED").isEqualTo(2);
    asReader().put().uri(path + "/fill/" + readerOnly.getId() + "/lock").exchange().expectStatus().is4xxClientError();
    field = changeMode(UDFShareMode.UDF_SHARE_READ_WRITE);
    asReader().put().uri(path + "/fill/" + ownerOnly.getId() + "/lock").exchange().expectStatus().is4xxClientError();
    asReader().put().uri(path + "/fill/" + readerOnly.getId() + "/lock").exchange().expectStatus().isOk();
    asReader().get().uri(path + "/fillstatus").exchange().expectStatus().isOk().expectBody()
        .jsonPath("$.countByState.LOCKED").isEqualTo(1);
    asReader().delete().uri(path + "/fill/" + readerOnly.getId() + "/lock").exchange().expectStatus().isOk();
  }
}
