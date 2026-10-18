package grafioschtrader.udffill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.User;
import grafiosch.types.UDFDataType;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFFillState;
import grafioschtrader.udffill.UDFFillOutcome.None;
import grafioschtrader.udffill.UDFFillOutcome.Retryable;
import grafioschtrader.udffill.UDFFillOutcome.Value;
import grafioschtrader.udffill.UDFFillWriter.ProposalRecord;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Applies every row of the write rule "manual values always win" to the real tables of {@code grafioschtrader_t}. The
 * writer opens its own transactions and locks rows, so the fixture is committed and removed explicitly instead of being
 * rolled back.
 */
@GTIntegrationTestContext
@TestInstance(Lifecycle.PER_CLASS)
class UDFFillWriterTest {

  private static final String V = "https://www.marketbeat.com/stocks/NASDAQ/AAPL/short-interest/";
  private static final String OTHER = "https://www.marketbeat.com/stocks/NASDAQ/AAPL2/short-interest/";
  private static final String TYPED = "https://www.example.com/typed-by-the-user";

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Autowired
  private UDFFillWriter writer;

  private TransactionTemplate tx;
  private Integer idTenant;
  private Integer idUser;
  private Integer idSecurity;
  private UDFMetadataSecurity field;
  private UDFMetadataSecurity sibling;

  @BeforeAll
  void createFixture() {
    tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(_ -> {
      Tenant tenant = new Tenant("UDF fill writer", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(tenant);
      idTenant = tenant.getId();
      User user = new User("udf-fill-writer@test.local", "unused", "UDF fill writer", "en", 0);
      user.setIdTenant(idTenant);
      em.persist(user);
      idUser = user.getIdUser();
      Assetclass assetclass = em.createQuery("SELECT a FROM Assetclass a", Assetclass.class).setMaxResults(1)
          .getSingleResult();
      Stockexchange exchange = em.createQuery("SELECT s FROM Stockexchange s", Stockexchange.class).setMaxResults(1)
          .getSingleResult();
      Security security = new Security("UDF fill writer security", "USD", assetclass, exchange,
          LocalDate.of(2000, 1, 3), LocalDate.of(2099, 12, 31), null, "AAPL", null);
      security.setLeverageFactor(1f);
      em.persist(security);
      idSecurity = security.getIdSecuritycurrency();
      field = newField("UDF fill writer field", (byte) 91);
      em.persist(field);
      sibling = newField("UDF fill writer sibling", (byte) 92);
      em.persist(sibling);
    });
  }

  private UDFMetadataSecurity newField(String description, byte uiOrder) {
    UDFMetadataSecurity f = new UDFMetadataSecurity(0, 0, idUser, null, description, null, UDFDataType.UDF_URLString,
        null, uiOrder);
    f.setFillSource(UDFFillSource.TEMPLATE);
    f.setFillRule("https://www.marketbeat.com/stocks/NASDAQ/{ticker}/short-interest/");
    return f;
  }

  @AfterAll
  void removeFixture() {
    jdbc.update("DELETE FROM udf_data WHERE id_user = ?", idUser);
    jdbc.update("DELETE FROM udf_metadata WHERE id_user = ?", idUser);
    jdbc.update("DELETE FROM security WHERE id_securitycurrency = ?", idSecurity);
    jdbc.update("DELETE FROM securitycurrency WHERE id_securitycurrency = ?", idSecurity);
    jdbc.update("DELETE FROM user WHERE id_user = ?", idUser);
    jdbc.update("DELETE FROM tenant WHERE id_tenant = ?", idTenant);
  }

  @BeforeEach
  void resetCell() {
    jdbc.update("DELETE FROM udf_data_fill WHERE id_entity = ?", idSecurity);
    jdbc.update("DELETE FROM udf_data WHERE id_user = ?", idUser);
    jdbc.update("UPDATE udf_metadata_security SET fill_source = 1, fill_rule_version = 0 WHERE id_udf_metadata = ?",
        field.getIdUDFMetadata());
    field.setFillRuleVersion(0);
    field.setFillSource(UDFFillSource.TEMPLATE);
  }

  @Test
  @DisplayName("No row, empty cell, value: the value is written and the cell becomes automatic")
  void writesIntoEmptyCell() {
    assertThat(writer.write(field, idSecurity, new Value(V, null))).isEqualTo(UDFFillWriteResult.WRITTEN);
    assertThat(cell()).isEqualTo(V);
    assertFill(UDFFillState.APPLIED, V);
  }

  @Test
  @DisplayName("No row, first insert of the udf_data row of the owner, sibling written later survives")
  void firstInsertAndSiblingSurvives() {
    writer.write(field, idSecurity, new Value(V, null));
    writer.write(sibling, idSecurity, new Value(OTHER, null));
    writer.write(field, idSecurity, new None());

    assertThat(cell()).isNull();
    assertThat(cell(sibling)).isEqualTo(OTHER);
  }

  @Test
  @DisplayName("No row, equal existing value: the value is adopted as automatic")
  void adoptsEqualValue() {
    setCell(V);
    assertThat(writer.write(field, idSecurity, new Value(V, null))).isEqualTo(UDFFillWriteResult.ADOPTED);
    assertFill(UDFFillState.APPLIED, V);
  }

  @Test
  @DisplayName("No row, different existing value: the manual value stays and no row is written")
  void keepsDifferentManualValue() {
    setCell(TYPED);
    assertThat(writer.write(field, idSecurity, new Value(V, null))).isEqualTo(UDFFillWriteResult.MANUAL);
    assertThat(cell()).isEqualTo(TYPED);
    assertNoFill();
  }

  @Test
  @DisplayName("NOT_FOUND or proposal row, empty cell, value: written; different value: row deleted, value kept")
  void notFoundAndProposalRows() {
    setFill(UDFFillState.NOT_FOUND, null, 0);
    assertThat(writer.write(field, idSecurity, new Value(V, null))).isEqualTo(UDFFillWriteResult.WRITTEN);
    assertFill(UDFFillState.APPLIED, V);

    resetCell();
    setCell(TYPED);
    setFill(UDFFillState.REJECTED, OTHER, 0);
    assertThat(writer.write(field, idSecurity, new Value(V, null))).isEqualTo(UDFFillWriteResult.MANUAL);
    assertThat(cell()).isEqualTo(TYPED);
    assertNoFill();
  }

  @Test
  @DisplayName("APPLIED and unedited: a new value replaces it, the same value leaves it unchanged")
  void appliedUnedited() {
    setCell(V);
    setFill(UDFFillState.APPLIED, V, 0);
    assertThat(writer.write(field, idSecurity, new Value(V, null))).isEqualTo(UDFFillWriteResult.UNCHANGED);
    assertThat(writer.write(field, idSecurity, new Value(OTHER, null))).isEqualTo(UDFFillWriteResult.WRITTEN);
    assertThat(cell()).isEqualTo(OTHER);
    assertFill(UDFFillState.APPLIED, OTHER);
  }

  @Test
  @DisplayName("APPLIED but cleared by the user: filled again")
  void appliedCleared() {
    setFill(UDFFillState.APPLIED, V, 0);
    assertThat(writer.write(field, idSecurity, new Value(V, null))).isEqualTo(UDFFillWriteResult.WRITTEN);
    assertThat(cell()).isEqualTo(V);
  }

  @Test
  @DisplayName("APPLIED but edited by the user: the edit survives, loses its mark, for a value and for none")
  void appliedEdited() {
    setCell(TYPED);
    setFill(UDFFillState.APPLIED, V, 0);
    assertThat(writer.write(field, idSecurity, new Value(OTHER, null))).isEqualTo(UDFFillWriteResult.MANUAL);
    assertThat(cell()).isEqualTo(TYPED);
    assertNoFill();

    setFill(UDFFillState.APPLIED, V, 0);
    assertThat(writer.write(field, idSecurity, new None())).isEqualTo(UDFFillWriteResult.MANUAL);
    assertThat(cell()).isEqualTo(TYPED);
    assertNoFill();
  }

  @Test
  @DisplayName("APPLIED and unedited, the template no longer matches: the value and the row are removed")
  void appliedRemovedByTemplate() {
    setCell(V);
    setFill(UDFFillState.APPLIED, V, 0);
    assertThat(writer.write(field, idSecurity, new None())).isEqualTo(UDFFillWriteResult.REMOVED);
    assertThat(cell()).isNull();
    assertNoFill();
  }

  @Test
  @DisplayName("APPLIED and unedited, a resolver answers none: the value is removed, the row becomes NOT_FOUND")
  void appliedRemovedByResolver() {
    switchFieldTo(UDFFillSource.RESOLVER);
    setCell(V);
    setFill(UDFFillState.APPLIED, V, 0);
    assertThat(writer.write(field, idSecurity, new None())).isEqualTo(UDFFillWriteResult.REMOVED);
    assertThat(cell()).isNull();
    assertFill(UDFFillState.NOT_FOUND, null);
  }

  @Test
  @DisplayName("None for an empty cell: a template writes nothing, a resolver records NOT_FOUND")
  void noneForEmptyCell() {
    assertThat(writer.write(field, idSecurity, new None())).isEqualTo(UDFFillWriteResult.NOT_FOUND);
    assertNoFill();

    switchFieldTo(UDFFillSource.RESOLVER);
    assertThat(writer.write(field, idSecurity, new None())).isEqualTo(UDFFillWriteResult.NOT_FOUND);
    assertFill(UDFFillState.NOT_FOUND, null);
  }

  @Test
  @DisplayName("NOT_FOUND row and a manual value, none: the row is deleted, the value stays")
  void notFoundWithManualValue() {
    setCell(TYPED);
    setFill(UDFFillState.NOT_FOUND, null, 0);
    assertThat(writer.write(field, idSecurity, new None())).isEqualTo(UDFFillWriteResult.MANUAL);
    assertThat(cell()).isEqualTo(TYPED);
    assertNoFill();
  }

  @Test
  @DisplayName("A locked cell is never touched, empty or not")
  void lockedCell() {
    setFill(UDFFillState.LOCKED, null, 0);
    assertThat(writer.write(field, idSecurity, new Value(V, null))).isEqualTo(UDFFillWriteResult.LOCKED);
    assertThat(cell()).isNull();
    setCell(TYPED);
    assertThat(writer.write(field, idSecurity, new None())).isEqualTo(UDFFillWriteResult.LOCKED);
    assertThat(cell()).isEqualTo(TYPED);
    assertFill(UDFFillState.LOCKED, null);
  }

  @Test
  @DisplayName("A technical failure leaves value, row and checked time unchanged")
  void retryableChangesNothing() {
    setCell(V);
    setFill(UDFFillState.APPLIED, V, 0);
    LocalDateTime before = checkedTime();
    assertThat(writer.write(field, idSecurity, new Retryable("timeout"))).isEqualTo(UDFFillWriteResult.RETRY);
    assertThat(cell()).isEqualTo(V);
    assertThat(checkedTime()).isEqualTo(before);
  }

  @Test
  @DisplayName("An outcome computed for an earlier rule version or a field switched to manual is stale")
  void staleOutcome() {
    jdbc.update("UPDATE udf_metadata_security SET fill_rule_version = 1 WHERE id_udf_metadata = ?",
        field.getIdUDFMetadata());
    assertThat(writer.write(field, idSecurity, new Value(V, null))).isEqualTo(UDFFillWriteResult.STALE);
    assertThat(cell()).isNull();
    assertNoFill();

    jdbc.update("UPDATE udf_metadata_security SET fill_rule_version = 0, fill_source = 0 WHERE id_udf_metadata = ?",
        field.getIdUDFMetadata());
    assertThat(writer.write(field, idSecurity, new Value(V, null))).isEqualTo(UDFFillWriteResult.STALE);
  }

  @Test
  @DisplayName("Refresh of an accepted model value: a failure sets the check time, a proposal replaces it, an edit wins")
  void refreshAppliedModelValue() {
    switchFieldTo(UDFFillSource.LLM);
    setCell(V);
    setFill(UDFFillState.APPLIED, V, 0);
    LocalDateTime before = checkedTime();
    assertThat(writer.refreshApplied(field, idSecurity,
        new ProposalRecord(UDFFillState.NOT_FOUND, null, null, null, null, "The model found no value"))).isTrue();
    assertThat(cell()).isEqualTo(V);
    assertThat(checkedTime()).isAfter(before);

    assertThat(writer.refreshApplied(field, idSecurity,
        new ProposalRecord(UDFFillState.PROPOSED, OTHER, OTHER, "short interest", null, null))).isTrue();
    assertThat(cell()).isEqualTo(OTHER);
    assertFill(UDFFillState.APPLIED, OTHER);

    setCell(TYPED);
    assertThat(writer.refreshApplied(field, idSecurity,
        new ProposalRecord(UDFFillState.PROPOSED, V, V, "short interest", null, null))).isFalse();
    assertThat(cell()).isEqualTo(TYPED);
  }

  @Test
  @DisplayName("Locking keeps the value, unlocking deletes the row")
  void lockAndDelete() {
    setCell(TYPED);
    writer.lock(field, idSecurity);
    assertFill(UDFFillState.LOCKED, TYPED);
    writer.deleteFillRow(field, idSecurity);
    assertNoFill();
    assertThat(cell()).isEqualTo(TYPED);
  }

  private void switchFieldTo(UDFFillSource fillSource) {
    jdbc.update("UPDATE udf_metadata_security SET fill_source = ? WHERE id_udf_metadata = ?", fillSource.getValue(),
        field.getIdUDFMetadata());
    field.setFillSource(fillSource);
  }

  private void setCell(String value) {
    jdbc.update("""
        INSERT INTO udf_data (id_user, entity, id_entity, json_values) VALUES (?, 'Security', ?, JSON_OBJECT(?, ?))
        ON DUPLICATE KEY UPDATE json_values = JSON_SET(json_values, CONCAT('$.', ?), ?)""", idUser, idSecurity,
        key(field), value, key(field), value);
  }

  private void setFill(UDFFillState state, String filledValue, int ruleVersion) {
    jdbc.update("""
        REPLACE INTO udf_data_fill (id_udf_metadata, id_entity, fill_source, fill_state, rule_version, filled_value,
        checked_time) VALUES (?, ?, 1, ?, ?, ?, '2026-01-01 00:00:00')""", field.getIdUDFMetadata(), idSecurity,
        state.getValue(), ruleVersion, filledValue);
  }

  private String cell() {
    return cell(field);
  }

  private String cell(UDFMetadataSecurity f) {
    List<String> values = jdbc.queryForList(
        "SELECT JSON_VALUE(json_values, CONCAT('$.', ?)) FROM udf_data WHERE id_user = ? AND entity = 'Security' AND id_entity = ?",
        String.class, key(f), idUser, idSecurity);
    return values.isEmpty() ? null : values.getFirst();
  }

  private void assertFill(UDFFillState state, String filledValue) {
    Map<String, Object> row = jdbc.queryForMap(
        "SELECT fill_state, filled_value, rule_version FROM udf_data_fill WHERE id_udf_metadata = ? AND id_entity = ?",
        field.getIdUDFMetadata(), idSecurity);
    assertThat(((Number) row.get("fill_state")).byteValue()).isEqualTo(state.getValue());
    assertThat(row.get("filled_value")).isEqualTo(filledValue);
    assertThat(((Number) row.get("rule_version")).intValue()).isEqualTo(field.getFillRuleVersion());
  }

  private void assertNoFill() {
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM udf_data_fill WHERE id_udf_metadata = ? AND id_entity = ?",
        Integer.class, field.getIdUDFMetadata(), idSecurity)).isZero();
  }

  private LocalDateTime checkedTime() {
    return jdbc.queryForObject("SELECT checked_time FROM udf_data_fill WHERE id_udf_metadata = ? AND id_entity = ?",
        LocalDateTime.class, field.getIdUDFMetadata(), idSecurity);
  }

  private static String key(UDFMetadataSecurity f) {
    return "f" + f.getIdUDFMetadata();
  }
}
