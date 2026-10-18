package grafioschtrader.udffill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.security.UserAuthentication;
import grafiosch.types.UDFDataType;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFFillState;
import grafioschtrader.udffill.resolver.IUDFValueResolver;
import grafioschtrader.udffill.resolver.ResolveOutcome;
import grafioschtrader.udffill.resolver.ResolveOutcome.Found;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFound;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFoundReason;
import grafioschtrader.udffill.resolver.ResolveOutcome.Retryable;
import grafioschtrader.udffill.resolver.UDFValueResolverRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Fill runs of the fill source RESOLVER in {@code grafioschtrader_t}, with a resolver bean of the test that answers
 * what the test prepares and counts its calls; nothing goes out to a public source. The in-memory cache of the registry
 * is emptied before each run, so that the counted calls show which cells were due.
 */
@GTIntegrationTestContext
@Import(UDFFillResolverRunTest.ResolverConfig.class)
@TestInstance(Lifecycle.PER_CLASS)
class UDFFillResolverRunTest {

  private static final String URL1 = "https://de.wikipedia.org/wiki/Erster_Artikel";
  private static final String URL2 = "https://de.wikipedia.org/wiki/Zweiter_Artikel";
  private static final String URL3 = "https://de.wikipedia.org/wiki/Dritter_Artikel";

  /** Resolver of the test: answers {@link #next} and counts its calls. */
  static class CountingResolver implements IUDFValueResolver {
    static final String ID = "TESTLOOKUP";
    volatile ResolveOutcome next;
    final AtomicInteger calls = new AtomicInteger();

    @Override
    public String getResolverId() {
      return ID;
    }

    @Override
    public Set<UDFDataType> getSupportedDataTypes() {
      return Set.of(UDFDataType.UDF_URLString);
    }

    @Override
    public String getDefaultArgument() {
      return "x";
    }

    @Override
    public String normalizeArgument(String argument) {
      return argument == null || argument.isBlank() ? "x" : argument.strip();
    }

    @Override
    public ResolveOutcome resolve(Security security, String argument) {
      calls.incrementAndGet();
      return next;
    }
  }

  /** Imported, not annotated: GTforTest scans the test packages, so an annotated class would reach every context. */
  static class ResolverConfig {
    @Bean
    CountingResolver countingResolver() {
      return new CountingResolver();
    }
  }

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Autowired
  private UDFFillService udfFillService;
  @Autowired
  private UDFValueResolverRegistry resolverRegistry;
  @Autowired
  private CountingResolver resolver;
  @Autowired
  private UDFMetadataSecurityJpaRepository uMetaSecurityRepository;

  private Integer idTenant;
  private User user;
  private Security security;
  private Integer idWatchlist;
  private UDFMetadataSecurity field;

  @BeforeAll
  void createFixture() {
    new TransactionTemplate(transactionManager).executeWithoutResult(_ -> {
      Tenant tenant = new Tenant("UDF fill resolver", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(tenant);
      idTenant = tenant.getId();
      user = new User("udf-fill-resolver@test.local", "unused", "UDF fill resolver", "en", 0);
      user.setIdTenant(idTenant);
      em.persist(user);
      Assetclass assetclass = em.createQuery("SELECT a FROM Assetclass a", Assetclass.class).setMaxResults(1)
          .getSingleResult();
      Stockexchange exchange = em.createQuery("SELECT s FROM Stockexchange s", Stockexchange.class).setMaxResults(1)
          .getSingleResult();
      security = new Security("UDF fill resolver security", "CHF", assetclass, exchange, LocalDate.of(2000, 1, 3),
          LocalDate.of(2099, 12, 31), null, "UFR", "CH9999999996");
      security.setLeverageFactor(1f);
      em.persist(security);
      Watchlist watchlist = new Watchlist(idTenant, "UDF fill resolver");
      watchlist.setSecuritycurrencyList(new ArrayList<>(List.of(security)));
      em.persist(watchlist);
      idWatchlist = watchlist.getIdWatchlist();
      field = new UDFMetadataSecurity(0, 0, user.getIdUser(), null, "UDF fill resolver link", null,
          UDFDataType.UDF_URLString, null, (byte) 95);
      field.setCategoryTypeEnums(EnumSet.of(assetclass.getCategoryType()));
      field.setSpecialInvestmentInstrumentEnums(EnumSet.of(assetclass.getSpecialInvestmentInstrument()));
      field.setFillSource(UDFFillSource.RESOLVER);
      field.setFillRule(CountingResolver.ID + " x");
      em.persist(field);
    });
  }

  @AfterAll
  void removeFixture() {
    jdbc.update("DELETE FROM task_data_change WHERE id_task = ? AND id_entity = ?",
        TaskTypeExtended.UDF_FILL_VALUES.getValue(), field.getIdUDFMetadata());
    jdbc.update("DELETE FROM udf_data WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM udf_metadata WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM watchlist_sec_cur WHERE id_watchlist = ?", idWatchlist);
    jdbc.update("DELETE FROM watchlist WHERE id_watchlist = ?", idWatchlist);
    jdbc.update("DELETE FROM security WHERE id_securitycurrency = ?", security.getIdSecuritycurrency());
    jdbc.update("DELETE FROM securitycurrency WHERE id_securitycurrency = ?", security.getIdSecuritycurrency());
    jdbc.update("DELETE FROM user WHERE id_user = ?", user.getIdUser());
    jdbc.update("DELETE FROM tenant WHERE id_tenant = ?", idTenant);
  }

  @BeforeEach
  void resetCell() {
    jdbc.update("DELETE FROM udf_data_fill WHERE id_udf_metadata = ?", field.getIdUDFMetadata());
    jdbc.update("DELETE FROM udf_data WHERE id_user = ?", user.getIdUser());
    jdbc.update("UPDATE udf_metadata_security SET fill_source = 2, fill_rule = ?, fill_rule_version = 0 "
        + "WHERE id_udf_metadata = ?", CountingResolver.ID + " x", field.getIdUDFMetadata());
    resolver.calls.set(0);
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("A resolved value is looked up again only after the refresh days or after a rule change")
  void refreshOnlyWhenDue() {
    run(new Found(URL1, "https://www.wikidata.org/wiki/Q1"));
    assertThat(cell()).isEqualTo(URL1);
    assertThat(fillState()).isEqualTo(UDFFillState.APPLIED);
    assertThat(resolver.calls).hasValue(1);

    run(new Found(URL2, null));
    assertThat(resolver.calls).hasValue(1);
    assertThat(cell()).isEqualTo(URL1);

    ageCheckedTime(31);
    run(new Found(URL2, null));
    assertThat(resolver.calls).hasValue(2);
    assertThat(cell()).isEqualTo(URL2);

    jdbc.update("UPDATE udf_metadata_security SET fill_rule = ?, fill_rule_version = 1 WHERE id_udf_metadata = ?",
        CountingResolver.ID + " y", field.getIdUDFMetadata());
    run(new Found(URL3, null));
    assertThat(resolver.calls).hasValue(3);
    assertThat(cell()).isEqualTo(URL3);
  }

  @Test
  @DisplayName("A definitive none removes the unedited value and is itself asked again only after the refresh days")
  void notFoundIsRemembered() {
    run(new Found(URL1, null));
    ageCheckedTime(31);
    run(new NotFound(NotFoundReason.ZERO_HITS));
    assertThat(cell()).isNull();
    assertThat(fillState()).isEqualTo(UDFFillState.NOT_FOUND);

    run(new Found(URL2, null));
    assertThat(resolver.calls).hasValue(2);
    assertThat(cell()).isNull();
  }

  @Test
  @DisplayName("A technical failure leaves value, row and checked time unchanged")
  void retryableChangesNothing() {
    run(new Found(URL1, null));
    ageCheckedTime(31);
    LocalDateTime before = checkedTime();

    run(new Retryable("Wikidata answered status 503"));

    assertThat(resolver.calls).hasValue(2);
    assertThat(cell()).isEqualTo(URL1);
    assertThat(fillState()).isEqualTo(UDFFillState.APPLIED);
    assertThat(checkedTime()).isEqualTo(before);
  }

  @Test
  @DisplayName("Saving normalises the argument and refuses an unknown resolver or a data type it cannot fill")
  void saveValidation() throws Exception {
    UDFMetadataSecurity existing = uMetaSecurityRepository.findById(field.getIdUDFMetadata()).get();
    UDFMetadataSecurity changed = copyOf(existing);
    changed.setFillRule("WIKIPEDIA  DE, en,de");
    UDFMetadataSecurity saved = uMetaSecurityRepository.saveOnlyAttributes(changed, existing,
        Set.of(PropertyAlwaysUpdatable.class));
    assertThat(saved.getFillRule()).isEqualTo("WIKIPEDIA de,en");
    assertThat(saved.getFillRuleVersion()).isEqualTo(1);

    UDFMetadataSecurity unknown = copyOf(saved);
    unknown.setFillRule("NOSUCHLOOKUP");
    assertRefused(unknown, saved, "gt.udf.fill.resolver.unknown");

    UDFMetadataSecurity invalidLanguages = copyOf(saved);
    invalidLanguages.setFillRule("WIKIPEDIA deutsch");
    assertRefused(invalidLanguages, saved, "gt.udf.fill.resolver.wikipedia.languages");

    UDFMetadataSecurity text = new UDFMetadataSecurity(0, 0, user.getIdUser(), null, "UDF fill resolver text", null,
        UDFDataType.UDF_String, "1,50", (byte) 96);
    text.setFillSource(UDFFillSource.RESOLVER);
    text.setFillRule("WIKIPEDIA de");
    assertRefused(text, null, "gt.udf.fill.source.data.type");
  }

  private void assertRefused(UDFMetadataSecurity entity, UDFMetadataSecurity existing, String messageKey) {
    assertThatThrownBy(
        () -> uMetaSecurityRepository.saveOnlyAttributes(entity, existing, Set.of(PropertyAlwaysUpdatable.class)))
            .isInstanceOfSatisfying(DataViolationException.class,
                e -> assertThat(e.getDataViolation().getFirst().getMessageKey()).isEqualTo(messageKey));
  }

  private static UDFMetadataSecurity copyOf(UDFMetadataSecurity source) {
    UDFMetadataSecurity copy = new UDFMetadataSecurity(source.getCategoryTypes(),
        source.getSpecialInvestmentInstruments(), source.getIdUser(), source.getUdfSpecialTypeAsByte(),
        source.getDescription(), source.getDescriptionHelp(), source.getUdfDataType(), source.getFieldSize(),
        source.getUiOrder());
    copy.setFillSource(source.getFillSource());
    copy.setFillRule(source.getFillRule());
    return copy;
  }

  private void run(ResolveOutcome next) {
    resolver.next = next;
    resolverRegistry.clearCache();
    udfFillService.runField(field.getIdUDFMetadata());
  }

  private void ageCheckedTime(int days) {
    jdbc.update("UPDATE udf_data_fill SET checked_time = checked_time - INTERVAL ? DAY WHERE id_udf_metadata = ?", days,
        field.getIdUDFMetadata());
  }

  private String cell() {
    List<String> values = jdbc.queryForList(
        "SELECT JSON_VALUE(json_values, CONCAT('$.f', ?)) FROM udf_data WHERE id_user = ? AND entity = 'Security' AND id_entity = ?",
        String.class, field.getIdUDFMetadata(), user.getIdUser(), security.getIdSecuritycurrency());
    return values.isEmpty() ? null : values.getFirst();
  }

  private UDFFillState fillState() {
    List<Byte> states = jdbc.queryForList(
        "SELECT fill_state FROM udf_data_fill WHERE id_udf_metadata = ? AND id_entity = ?", Byte.class,
        field.getIdUDFMetadata(), security.getIdSecuritycurrency());
    return states.isEmpty() ? null : UDFFillState.getByValue(states.getFirst());
  }

  private LocalDateTime checkedTime() {
    return jdbc.queryForObject("SELECT checked_time FROM udf_data_fill WHERE id_udf_metadata = ? AND id_entity = ?",
        LocalDateTime.class, field.getIdUDFMetadata(), security.getIdSecuritycurrency());
  }
}
