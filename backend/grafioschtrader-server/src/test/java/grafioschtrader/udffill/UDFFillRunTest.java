package grafioschtrader.udffill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.BaseConstants;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.security.UserAuthentication;
import grafiosch.types.UDFDataType;
import grafioschtrader.dto.UDFFillTestRequest;
import grafioschtrader.dto.UDFFillTestResult;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFFillState;
import grafioschtrader.types.UDFSpecialGTType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Runs the fill of a template field over the tenant scope of a watchlist in {@code grafioschtrader_t}: the marketbeat
 * rule of the specification on a NASDAQ, a NYSE and a SIX listing. The fill writes in its own transactions, so the
 * fixture is committed and removed explicitly.
 */
@GTIntegrationTestContext
@TestInstance(Lifecycle.PER_CLASS)
class UDFFillRunTest {

  private static final String MARKETBEAT = "[mic=XNAS,XNYS] https://www.marketbeat.com/stocks/"
      + "{mic|map(XNAS=NASDAQ,XNYS=NYSE)}/{ticker|urlencode}/short-interest/";
  private static final String AAPL_URL = "https://www.marketbeat.com/stocks/NASDAQ/AAPL/short-interest/";
  private static final String KO_URL = "https://www.marketbeat.com/stocks/NYSE/KO/short-interest/";
  private static final String TYPED = "https://www.example.com/typed-by-the-user";

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Autowired
  private UDFFillService udfFillService;
  @Autowired
  private UDFFillWriter writer;
  @Autowired
  private UDFMetadataSecurityJpaRepository uMetaSecurityRepository;

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
      Tenant tenant = new Tenant("UDF fill run", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(tenant);
      idTenant = tenant.getId();
      user = new User("udf-fill-run@test.local", "unused", "UDF fill run", "en", 0);
      user.setIdTenant(idTenant);
      em.persist(user);
      Assetclass assetclass = em.createQuery("SELECT a FROM Assetclass a", Assetclass.class).setMaxResults(1)
          .getSingleResult();
      aapl = security("UDF fill run Apple", "AAPL", "XNAS", assetclass);
      ko = security("UDF fill run Coca-Cola", "KO", "XNYS", assetclass);
      nesn = security("UDF fill run Nestle", "NESN", "XSWX", assetclass);
      Watchlist watchlist = new Watchlist(idTenant, "UDF fill run");
      watchlist.setSecuritycurrencyList(new ArrayList<>(List.of(aapl, ko, nesn)));
      em.persist(watchlist);
      idWatchlist = watchlist.getIdWatchlist();
      field = new UDFMetadataSecurity(0, 0, user.getIdUser(), null, "UDF fill run marketbeat", null,
          UDFDataType.UDF_URLString, null, (byte) 93);
      field.setCategoryTypeEnums(EnumSet.of(assetclass.getCategoryType()));
      field.setSpecialInvestmentInstrumentEnums(EnumSet.of(assetclass.getSpecialInvestmentInstrument()));
      field.setFillSource(UDFFillSource.TEMPLATE);
      field.setFillRule(MARKETBEAT);
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
  void resetCells() {
    jdbc.update("DELETE FROM udf_data_fill WHERE id_udf_metadata = ?", field.getIdUDFMetadata());
    jdbc.update("DELETE FROM udf_data WHERE id_user = ?", user.getIdUser());
    setRule(MARKETBEAT, 0);
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("The marketbeat rule fills NASDAQ and NYSE, adopts an equal value, and removes NYSE when narrowed")
  void marketbeatRule() {
    setCell(ko, KO_URL);
    udfFillService.runField(field.getIdUDFMetadata());

    assertThat(cell(aapl)).isEqualTo(AAPL_URL);
    assertThat(fillState(aapl)).isEqualTo(UDFFillState.APPLIED);
    assertThat(cell(ko)).isEqualTo(KO_URL);
    assertThat(fillState(ko)).isEqualTo(UDFFillState.APPLIED);
    assertThat(cell(nesn)).isNull();
    assertThat(fillState(nesn)).isNull();

    setRule(MARKETBEAT.replace("[mic=XNAS,XNYS]", "[mic=XNAS]"), 1);
    udfFillService.runField(field.getIdUDFMetadata());

    assertThat(cell(aapl)).isEqualTo(AAPL_URL);
    assertThat(cell(ko)).isNull();
    assertThat(fillState(ko)).isNull();
  }

  @Test
  @DisplayName("An edited value survives a run and loses its mark, a cleared one is filled again, a lock holds")
  void manualValuesWin() {
    udfFillService.runField(field.getIdUDFMetadata());
    setCell(aapl, TYPED);
    jdbc.update("DELETE FROM udf_data WHERE id_user = ? AND id_entity = ?", user.getIdUser(),
        ko.getIdSecuritycurrency());
    writer.lock(field, nesn.getIdSecuritycurrency());
    setCell(nesn, TYPED);

    udfFillService.runField(field.getIdUDFMetadata());

    assertThat(cell(aapl)).isEqualTo(TYPED);
    assertThat(fillState(aapl)).isNull();
    assertThat(cell(ko)).isEqualTo(KO_URL);
    assertThat(fillState(ko)).isEqualTo(UDFFillState.APPLIED);
    assertThat(cell(nesn)).isEqualTo(TYPED);
    assertThat(fillState(nesn)).isEqualTo(UDFFillState.LOCKED);
  }

  @Test
  @DisplayName("The test of a rule counts coverage and agreement and writes nothing")
  void testButtonWritesNothing() {
    setCell(ko, KO_URL);
    setCell(nesn, TYPED);
    UDFFillTestRequest request = new UDFFillTestRequest();
    request.idUDFMetadata = field.getIdUDFMetadata();
    request.udfDataType = UDFDataType.UDF_URLString;
    request.categoryTypeEnums = field.getCategoryTypeEnums();
    request.specialInvestmentInstrumentEnums = field.getSpecialInvestmentInstrumentEnums();
    request.fillSource = UDFFillSource.TEMPLATE;
    request.fillRule = MARKETBEAT;

    UDFFillTestResult result = udfFillService.testFillRule(request, user);

    // The tenant scope may also hold other securities of the test data of this tenant; it has none here.
    assertThat(result.inScope).isEqualTo(3);
    assertThat(result.withValue).isEqualTo(2);
    assertThat(result.produced).isEqualTo(2);
    assertThat(result.equal).isEqualTo(1);
    assertThat(result.differing).isEqualTo(1);
    assertThat(result.noLine).isEqualTo(1);
    assertThat(result.invalid).isZero();
    assertThat(result.mismatches).singleElement().satisfies(m -> {
      assertThat(m.existing()).isEqualTo(TYPED);
      assertThat(m.computed()).isNull();
    });
    assertThat(result.samples).singleElement().satisfies(s -> assertThat(s.computed()).isEqualTo(AAPL_URL));
    assertThat(cell(aapl)).isNull();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM udf_data_fill WHERE id_udf_metadata = ?", Integer.class,
        field.getIdUDFMetadata())).isZero();
  }

  @Test
  @DisplayName("An unparsable rule, a template on a numeric field and a fill source on a user-0 field are refused")
  void saveValidation() {
    UDFMetadataSecurity unparsable = copyOf(field);
    unparsable.setFillRule("https://a.example/{tickr}");
    assertRefused(unparsable, field, "gt.udf.fill.rule.unknown.placeholder");

    UDFMetadataSecurity numeric = new UDFMetadataSecurity(0, 0, user.getIdUser(), null, "UDF fill run numeric", null,
        UDFDataType.UDF_Numeric, "10,2", (byte) 94);
    numeric.setFillSource(UDFFillSource.TEMPLATE);
    numeric.setFillRule("{ticker}");
    assertRefused(numeric, null, "gt.udf.fill.source.data.type");

    UDFMetadataSecurity user0Field = uMetaSecurityRepository.getByUdfSpecialTypeAndIdUser(
        UDFSpecialGTType.UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE.getValue(), BaseConstants.UDF_ID_USER);
    UDFMetadataSecurity user0Change = copyOf(user0Field);
    user0Change.setFillSource(UDFFillSource.TEMPLATE);
    user0Change.setFillRule("{ticker}");
    assertRefused(user0Change, user0Field, "gt.udf.fill.source.not.allowed");
  }

  private void assertRefused(UDFMetadataSecurity entity, UDFMetadataSecurity existing, String messageKey) {
    assertThatThrownBy(
        () -> uMetaSecurityRepository.saveOnlyAttributes(entity, existing, Set.of(PropertyAlwaysUpdatable.class)))
            .isInstanceOfSatisfying(DataViolationException.class, e -> {
              String key = e.getDataViolation().getFirst().getMessageKey();
              assertThat(key).isEqualTo(messageKey);
              // The key resolves to a translated text in both languages
              for (Locale locale : List.of(Locale.ENGLISH, Locale.GERMAN)) {
                assertThat(ResourceBundle.getBundle("message/messages", locale).getString(key)).isNotBlank();
              }
            });
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

  private void setRule(String rule, int version) {
    jdbc.update("UPDATE udf_metadata_security SET fill_source = 1, fill_rule = ?, fill_rule_version = ? "
        + "WHERE id_udf_metadata = ?", rule, version, field.getIdUDFMetadata());
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
