package grafioschtrader.search;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.security.UserAuthentication;
import grafiosch.types.UDFDataType;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.WatchlistIndicatorColumn;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.service.AdvancedSearchFieldService;
import grafioschtrader.types.IndicatorMarkColors;
import grafioschtrader.types.IndicatorResultType;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.UDFSpecialGTType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * The advanced security search against {@code grafioschtrader_t}: criteria on stored user-defined fields and on
 * indicators, through the same entry point the watchlist, correlation and algorithm dialogs use. Seven securities with
 * a common name prefix keep the results independent of the other test data; the stored values of one field at a time
 * are written by the test itself, because the search reads them from {@code udf_data}.
 */
@GTIntegrationTestContext
@TestInstance(Lifecycle.PER_CLASS)
class AdvancedSecuritySearchTest {

  private static final String PREFIX = "AdvSrchFixture";
  private static final String ISIN_OF_FIRST = "XSADVSRCH010";
  private static final byte UI_ORDER_FIRST = 85;
  private static final UDFSpecialGTType YIELD_TO_MATURITY = UDFSpecialGTType.UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY;

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Autowired
  private SecurityJpaRepository securityJpaRepository;
  @Autowired
  private AdvancedSearchFieldService advancedSearchFieldService;

  private Integer idTenant;
  private User user;
  private User otherUser;
  private Security[] securities;
  private UDFMetadataSecurity numberField;
  private UDFMetadataSecurity dateField;
  private UDFMetadataSecurity flagField;
  private UDFMetadataSecurity textField;
  private UDFMetadataSecurity linkField;
  private UDFMetadataSecurity systemField;
  private UDFMetadataSecurity otherUsersField;
  private WatchlistIndicatorColumn priceColumn;
  private WatchlistIndicatorColumn aboveFifteenColumn;
  private boolean lastSecurityOutOfScope;

  @BeforeAll
  void createFixture() {
    new TransactionTemplate(transactionManager).executeWithoutResult(_ -> {
      Tenant tenant = new Tenant("Advanced search", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(tenant);
      idTenant = tenant.getId();
      user = new User("adv-search@test.local", "unused", "Advanced search", "en", 0);
      user.setIdTenant(idTenant);
      em.persist(user);
      otherUser = new User("adv-search-other@test.local", "unused", "Advanced search other", "en", 0);
      otherUser.setIdTenant(idTenant);
      em.persist(otherUser);
      createSecurities();
      numberField = field(user, "Adv number", UDFDataType.UDF_Numeric, "6,2", UI_ORDER_FIRST);
      dateField = field(user, "Adv date", UDFDataType.UDF_DateString, null, (byte) (UI_ORDER_FIRST + 1));
      flagField = field(user, "Adv flag", UDFDataType.UDF_Boolean, null, (byte) (UI_ORDER_FIRST + 2));
      textField = field(user, "Adv text", UDFDataType.UDF_String, "1,100", (byte) (UI_ORDER_FIRST + 3));
      linkField = field(user, "Adv link", UDFDataType.UDF_URLString, null, (byte) (UI_ORDER_FIRST + 6));
      systemField = field(null, "Adv system", UDFDataType.UDF_NumericInteger, "5,0", (byte) (UI_ORDER_FIRST + 4));
      otherUsersField = field(otherUser, "Adv other", UDFDataType.UDF_Numeric, "6,2", (byte) (UI_ORDER_FIRST + 5));
      priceColumn = column("Adv price", "PRICE", IndicatorResultType.NUMBER, (short) 1);
      aboveFifteenColumn = column("Adv above 15", "PRICE > 15", IndicatorResultType.BOOLEAN, (short) 2);
    });
  }

  private void createSecurities() {
    List<Assetclass> assetclasses = em.createQuery("SELECT a FROM Assetclass a", Assetclass.class).getResultList();
    Assetclass inScope = assetclasses.getFirst();
    Assetclass outOfScope = assetclasses.stream()
        .filter(a -> a.getCategoryType() != inScope.getCategoryType()
            || a.getSpecialInvestmentInstrument() != inScope.getSpecialInvestmentInstrument())
        .findFirst().orElse(inScope);
    lastSecurityOutOfScope = outOfScope != inScope;
    Stockexchange exchange = em.createQuery("SELECT s FROM Stockexchange s", Stockexchange.class).setMaxResults(1)
        .getSingleResult();
    securities = new Security[7];
    Double[] prices = { 10.0, 20.0, 30.0, null, null, null, null };
    for (int i = 0; i < securities.length; i++) {
      Security security = new Security(PREFIX + " " + (i + 1), "USD", i == 6 ? outOfScope : inScope, exchange,
          LocalDate.of(2000, 1, 3), LocalDate.of(2099, 12, 31), null, "ADV" + (i + 1),
          i == 0 ? ISIN_OF_FIRST : null);
      security.setLeverageFactor(1f);
      security.setSLast(prices[i]);
      em.persist(security);
      securities[i] = security;
    }
  }

  private UDFMetadataSecurity field(User owner, String description, UDFDataType type, String fieldSize,
      byte uiOrder) {
    Assetclass assetclass = securities[0].getAssetClass();
    int idOwner = owner == null ? BaseConstants.UDF_ID_USER : owner.getIdUser();
    UDFMetadataSecurity field = new UDFMetadataSecurity(0, 0, idOwner, null, description, null, type, fieldSize,
        uiOrder);
    field.setCategoryTypeEnums(EnumSet.of(assetclass.getCategoryType()));
    field.setSpecialInvestmentInstrumentEnums(EnumSet.of(assetclass.getSpecialInvestmentInstrument()));
    em.persist(field);
    return field;
  }

  private WatchlistIndicatorColumn column(String label, String expression, IndicatorResultType type, short uiOrder) {
    WatchlistIndicatorColumn column = new WatchlistIndicatorColumn();
    column.setIdTenant(idTenant);
    column.setLabel(label);
    column.setExpression(expression);
    column.setResultType(type);
    column.setMarkColors(IndicatorMarkColors.NEUTRAL);
    column.setUiOrder(uiOrder);
    em.persist(column);
    return column;
  }

  @AfterAll
  void removeFixture() {
    clearValues();
    jdbc.update("DELETE FROM watchlist_indicator_column_watchlist WHERE id_watchlist_indicator_column IN "
        + "(SELECT id_watchlist_indicator_column FROM watchlist_indicator_column WHERE id_tenant = ?)", idTenant);
    jdbc.update("DELETE FROM watchlist_indicator_column WHERE id_tenant = ?", idTenant);
    for (UDFMetadataSecurity field : List.of(numberField, dateField, flagField, textField, linkField,
        systemField, otherUsersField)) {
      jdbc.update("DELETE FROM udf_metadata WHERE id_udf_metadata = ?", field.getIdUDFMetadata());
    }
    for (Security security : securities) {
      jdbc.update("DELETE FROM security WHERE id_securitycurrency = ?", security.getIdSecuritycurrency());
      jdbc.update("DELETE FROM securitycurrency WHERE id_securitycurrency = ?", security.getIdSecuritycurrency());
    }
    jdbc.update("DELETE FROM user WHERE id_user IN (?, ?)", user.getIdUser(), otherUser.getIdUser());
    jdbc.update("DELETE FROM tenant WHERE id_tenant = ?", idTenant);
  }

  @BeforeEach
  void authenticate() {
    clearValues();
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  private void clearValues() {
    jdbc.update("DELETE FROM udf_data WHERE id_user IN (?, ?, ?) AND id_entity IN "
        + "(SELECT id_securitycurrency FROM security WHERE name LIKE ?)", user.getIdUser(), otherUser.getIdUser(),
        BaseConstants.UDF_ID_USER, PREFIX + "%");
  }

  /** Stores the raw JSON of one field of one fixture security for the owner of the field. */
  private void store(UDFMetadataSecurity field, int securityIndex, String rawJson) {
    jdbc.update("INSERT INTO udf_data (id_user, entity, id_entity, json_values) VALUES (?, 'Security', ?, ?)",
        field.getIdUser(), securities[securityIndex].getIdSecuritycurrency(),
        "{\"f" + field.getIdUDFMetadata() + "\": " + rawJson + "}");
  }

  private AdvancedSearchCriterion criterion(UDFMetadataSecurity field) {
    AdvancedSearchCriterion criterion = new AdvancedSearchCriterion();
    criterion.setField(AdvancedSearchField.KEY_PREFIX_UDF + field.getIdUDFMetadata());
    return criterion;
  }

  private AdvancedSearchCriterion number(UDFMetadataSecurity field, AdvancedSearchOperator operator, Double from,
      Double to) {
    AdvancedSearchCriterion criterion = criterion(field);
    criterion.setOperator(operator);
    criterion.setNumber(from);
    criterion.setNumberTo(to);
    return criterion;
  }

  private AdvancedSearchResult search(AdvancedSearchCriterion... criteria) {
    SecuritycurrencySearch search = new SecuritycurrencySearch();
    search.setName(PREFIX);
    search.setAdvancedCriteria(new ArrayList<>(List.of(criteria)));
    return securityJpaRepository.searchWithAdvancedCriteria(null, null, search, idTenant);
  }

  private List<String> numbersFound(AdvancedSearchResult result) {
    return result.securities().stream().map(s -> s.getName().substring(PREFIX.length() + 1)).sorted().toList();
  }

  private void storeNumbers() {
    store(numberField, 0, "\"10\"");
    store(numberField, 1, "20");
    store(numberField, 2, "\"20.5\"");
    store(numberField, 3, "\"\"");
    store(numberField, 4, "\"12abc\"");
    store(numberField, 5, "null");
    store(numberField, 6, "\"5\"");
  }

  @Test
  @DisplayName("A number stored as a string and one stored as a JSON number fall into the same range")
  void numberRange() {
    storeNumbers();

    AdvancedSearchResult result = search(number(numberField, AdvancedSearchOperator.LESS_EQUAL, 20.0, null));

    // 4 is an empty string, 5 a partly numeric text, 6 a JSON null, 3 is above 20; 7 is 5 but out of the field scope.
    assertThat(numbersFound(result)).isEqualTo(lastSecurityOutOfScope ? List.of("1", "2") : List.of("1", "2", "7"));
    assertThat(result.values().get(securities[1].getIdSecuritycurrency()))
        .containsEntry(AdvancedSearchField.KEY_PREFIX_UDF + numberField.getIdUDFMetadata(), 20.0);
  }

  @Test
  @DisplayName("Less than excludes the boundary, between and equal include it, a missing value never matches")
  void numberOperators() {
    storeNumbers();

    assertThat(numbersFound(search(number(numberField, AdvancedSearchOperator.LESS_THAN, 20.0, null))))
        .contains("1").doesNotContain("2", "3", "4", "5", "6");
    assertThat(numbersFound(search(number(numberField, AdvancedSearchOperator.BETWEEN, 10.0, 20.0))))
        .isEqualTo(List.of("1", "2"));
    assertThat(numbersFound(search(number(numberField, AdvancedSearchOperator.EQUAL, 20.0, null))))
        .isEqualTo(List.of("2"));
    assertThat(numbersFound(search(number(numberField, AdvancedSearchOperator.GREATER_THAN, 20.0, null))))
        .isEqualTo(List.of("3"));
    assertThat(numbersFound(search(number(numberField, AdvancedSearchOperator.BETWEEN, -1.0, 1000.0))))
        .as("an unreadable text is not the number 0").doesNotContain("4", "5", "6");
  }

  @Test
  @DisplayName("A date and a date-time value fall into a date range, whatever zone the time carries")
  void dateRange() {
    store(dateField, 0, "\"2026-03-15\"");
    store(dateField, 1, "\"2026-03-15T23:30:00+05:00\"");
    store(dateField, 2, "\"2026-04-01\"");
    AdvancedSearchCriterion criterion = criterion(dateField);
    criterion.setOperator(AdvancedSearchOperator.BETWEEN);
    criterion.setDate(LocalDate.of(2026, 3, 1));
    criterion.setDateTo(LocalDate.of(2026, 3, 31));

    assertThat(numbersFound(search(criterion))).isEqualTo(List.of("1", "2"));
  }

  @Test
  @DisplayName("A flag selects only its explicit value, a security without a value matches neither")
  void flagCriterion() {
    store(flagField, 0, "true");
    store(flagField, 1, "false");
    AdvancedSearchCriterion wantTrue = criterion(flagField);
    wantTrue.setFlag(true);
    AdvancedSearchCriterion wantFalse = criterion(flagField);
    wantFalse.setFlag(false);

    assertThat(numbersFound(search(wantTrue))).isEqualTo(List.of("1"));
    assertThat(numbersFound(search(wantFalse))).isEqualTo(List.of("2"));
  }

  @Test
  @DisplayName("A text criterion is a substring in which the percent sign is an ordinary character")
  void textCriterion() {
    store(textField, 0, "\"Fully 100% sure\"");
    store(textField, 1, "\"100x sure\"");
    AdvancedSearchCriterion criterion = criterion(textField);
    criterion.setText("100%");

    assertThat(numbersFound(search(criterion))).isEqualTo(List.of("1"));
  }

  @Test
  @DisplayName("A system-wide field selects through the stored row of user 0")
  void systemWideField() {
    store(systemField, 0, "\"7\"");
    store(systemField, 1, "\"9\"");

    assertThat(numbersFound(search(number(systemField, AdvancedSearchOperator.LESS_THAN, 8.0, null))))
        .isEqualTo(List.of("1"));
  }

  @Test
  @DisplayName("The field of another user is refused and its values select nothing")
  void fieldOfAnotherUserIsRefused() {
    store(otherUsersField, 0, "\"1\"");

    assertThatThrownBy(() -> search(number(otherUsersField, AdvancedSearchOperator.LESS_THAN, 100.0, null)))
        .isInstanceOfSatisfying(DataViolationException.class, e -> assertThat(
            e.getDataViolation().getFirst().getMessageKey()).isEqualTo("gt.advanced.search.unknown.field"));
  }

  @Test
  @DisplayName("Two criteria combine with AND")
  void criteriaCombine() {
    storeNumbers();
    AdvancedSearchCriterion text = criterion(textField);
    text.setText("anything");

    AdvancedSearchResult result = search(number(numberField, AdvancedSearchOperator.LESS_EQUAL, 20.0, null), text);

    assertThat(result.securities()).isEmpty();
  }

  @Test
  @DisplayName("An ISIN returns its security although a criterion would exclude it, and reads no value")
  void isinOverridesCriteria() {
    storeNumbers();
    SecuritycurrencySearch search = new SecuritycurrencySearch();
    search.setIsin(ISIN_OF_FIRST);
    search.setAdvancedCriteria(
        new ArrayList<>(List.of(number(numberField, AdvancedSearchOperator.GREATER_THAN, 1000.0, null))));

    AdvancedSearchResult result = securityJpaRepository.searchWithAdvancedCriteria(null, null, search, idTenant);

    assertThat(numbersFound(result)).isEqualTo(List.of("1"));
    assertThat(result.values()).isEmpty();
  }

  @Test
  @DisplayName("The yield to maturity, which is only computed for the watchlist, is not offered")
  void computedFieldIsNotOffered() {
    assertThat(advancedSearchFieldService.resolve(user).values())
        .noneMatch(f -> f.udf() != null && f.udf().getUdfSpecialType() == YIELD_TO_MATURITY);
    assertThat(advancedSearchFieldService.resolve(user)).containsKeys(
        AdvancedSearchField.KEY_PREFIX_UDF + numberField.getIdUDFMetadata(),
        AdvancedSearchField.KEY_PREFIX_INDICATOR + priceColumn.getIdWatchlistIndicatorColumn());
  }

  @Test
  @DisplayName("A field that holds a link is not offered, a text field is")
  void linkFieldIsNotOffered() {
    assertThat(advancedSearchFieldService.resolve(user))
        .doesNotContainKey(AdvancedSearchField.KEY_PREFIX_UDF + linkField.getIdUDFMetadata())
        .containsKey(AdvancedSearchField.KEY_PREFIX_UDF + textField.getIdUDFMetadata());
    assertThatThrownBy(() -> search(criterion(linkField)))
        .isInstanceOfSatisfying(DataViolationException.class, e -> assertThat(
            e.getDataViolation().getFirst().getMessageKey()).isEqualTo("gt.advanced.search.unknown.field"));
  }

  private AdvancedSearchCriterion indicator(WatchlistIndicatorColumn column) {
    AdvancedSearchCriterion criterion = new AdvancedSearchCriterion();
    criterion.setField(AdvancedSearchField.KEY_PREFIX_INDICATOR + column.getIdWatchlistIndicatorColumn());
    return criterion;
  }

  @Test
  @DisplayName("A numeric indicator compares its value, a security without price is counted as not evaluated")
  void numericIndicator() {
    AdvancedSearchCriterion criterion = indicator(priceColumn);
    criterion.setOperator(AdvancedSearchOperator.LESS_EQUAL);
    criterion.setNumber(20.0);

    AdvancedSearchResult result = search(criterion);

    assertThat(numbersFound(result)).isEqualTo(List.of("1", "2"));
    assertThat(result.notEvaluated()).isEqualTo(4);
    assertThat(result.values().get(securities[0].getIdSecuritycurrency()))
        .containsEntry(AdvancedSearchField.KEY_PREFIX_INDICATOR + priceColumn.getIdWatchlistIndicatorColumn(), 10.0);
  }

  @Test
  @DisplayName("A boolean indicator matches its flag")
  void booleanIndicator() {
    AdvancedSearchCriterion criterion = indicator(aboveFifteenColumn);
    criterion.setFlag(true);

    AdvancedSearchResult result = search(criterion);

    assertThat(numbersFound(result)).isEqualTo(List.of("2", "3"));
    assertThat(result.notEvaluated()).isEqualTo(4);
  }

  @Test
  @DisplayName("A UDF and an indicator criterion both have to be satisfied")
  void udfAndIndicator() {
    storeNumbers();
    AdvancedSearchCriterion price = indicator(priceColumn);
    price.setOperator(AdvancedSearchOperator.GREATER_EQUAL);
    price.setNumber(20.0);

    AdvancedSearchResult result = search(number(numberField, AdvancedSearchOperator.LESS_EQUAL, 20.0, null), price);

    assertThat(numbersFound(result)).isEqualTo(List.of("2"));
  }
}
