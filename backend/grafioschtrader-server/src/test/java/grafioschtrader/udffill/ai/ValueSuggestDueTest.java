package grafioschtrader.udffill.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFDataFill;
import grafioschtrader.entities.UDFDataFill.UDFDataFillKey;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.types.UDFFillPeriod;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFFillState;
import grafioschtrader.udffill.UDFFillService;
import grafioschtrader.udffill.ai.ValueSuggestService.SuggestDue;

/**
 * Which cells a value proposal run of a language model asks for. A wrong answer either spends money on a cell the user
 * owns or that cannot have a current value, or leaves an accepted value to age although the user chose a period.
 */
class ValueSuggestDueTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 12, 0);
  private static final int RULE_VERSION = 2;

  @Test
  @DisplayName("Without a period only empty cells without a row of the current rule are asked, never a refresh")
  void onRequestOnly() {
    UDFMetadataSecurity field = field(null);
    LocalDateTime refreshBefore = null;
    assertThat(due(field, active(), null, null, refreshBefore)).isEqualTo(SuggestDue.EMPTY);
    assertThat(due(field, active(), fill(UDFFillState.NOT_FOUND, null, NOW.minusYears(2), RULE_VERSION - 1), null,
        refreshBefore)).isEqualTo(SuggestDue.EMPTY);
    assertThat(due(field, active(), fill(UDFFillState.NOT_FOUND, null, NOW.minusYears(2), RULE_VERSION), null,
        refreshBefore)).isEqualTo(SuggestDue.NONE);
    assertThat(due(field, active(), fill(UDFFillState.APPLIED, "18.4", NOW.minusYears(2), RULE_VERSION), "18.4",
        refreshBefore)).isEqualTo(SuggestDue.NONE);
  }

  @Test
  @DisplayName("Quarterly: an accepted value older than three months is refreshed, a younger one is not")
  void quarterlyRefresh() {
    UDFMetadataSecurity field = field(UDFFillPeriod.FILL_PERIOD_QUARTERLY);
    LocalDateTime refreshBefore = UDFFillService.refreshBefore(field.getFillPeriod(), NOW, 0);
    assertThat(refreshBefore).isEqualTo(NOW.minusMonths(3));
    assertThat(due(field, active(), fill(UDFFillState.APPLIED, "18.4", NOW.minusMonths(4), RULE_VERSION - 1), "18.4",
        refreshBefore)).isEqualTo(SuggestDue.REFRESH);
    assertThat(due(field, active(), fill(UDFFillState.APPLIED, "18.4", NOW.minusMonths(2), RULE_VERSION), "18.4",
        refreshBefore)).isEqualTo(SuggestDue.NONE);
    assertThat(due(field, active(), fill(UDFFillState.NOT_FOUND, null, NOW.minusMonths(4), RULE_VERSION), null,
        refreshBefore)).isEqualTo(SuggestDue.EMPTY);
    assertThat(due(field, active(), fill(UDFFillState.DISCARDED, null, NOW.minusMonths(4), RULE_VERSION), null,
        refreshBefore)).isEqualTo(SuggestDue.EMPTY);
  }

  @Test
  @DisplayName("Never asked: a value the user changed, a locked cell, a rejected proposal, a delisted or private one")
  void neverAsked() {
    UDFMetadataSecurity field = field(UDFFillPeriod.FILL_PERIOD_MONTHLY);
    LocalDateTime refreshBefore = UDFFillService.refreshBefore(field.getFillPeriod(), NOW, 0);
    LocalDateTime old = NOW.minusYears(1);
    assertThat(due(field, active(), fill(UDFFillState.APPLIED, "18.4", old, RULE_VERSION), "21", refreshBefore))
        .isEqualTo(SuggestDue.NONE);
    assertThat(due(field, active(), fill(UDFFillState.LOCKED, null, old, RULE_VERSION), null, refreshBefore))
        .isEqualTo(SuggestDue.NONE);
    assertThat(due(field, active(), fill(UDFFillState.REJECTED, "18.4", old, RULE_VERSION), null, refreshBefore))
        .isEqualTo(SuggestDue.NONE);
    Security delisted = active();
    delisted.setActiveToDate(TODAY.minusDays(1));
    assertThat(due(field, delisted, null, null, refreshBefore)).isEqualTo(SuggestDue.NONE);
    Security privateSecurity = active();
    privateSecurity.setIdTenantPrivate(7);
    assertThat(due(field, privateSecurity, null, null, refreshBefore)).isEqualTo(SuggestDue.NONE);
    assertThat(due(field, active(), null, "18.4", refreshBefore)).isEqualTo(SuggestDue.NONE);
  }

  @Test
  @DisplayName("Once: an empty cell is asked, a checked cell never again; without a period the global days apply")
  void onceAndDefault() {
    assertThat(UDFFillService.refreshBefore(UDFFillPeriod.FILL_PERIOD_ONCE, NOW, 30)).isNull();
    assertThat(UDFFillService.refreshBefore(null, NOW, 30)).isEqualTo(NOW.minusDays(30));
    UDFMetadataSecurity field = field(UDFFillPeriod.FILL_PERIOD_ONCE);
    assertThat(due(field, active(), null, null, null)).isEqualTo(SuggestDue.EMPTY);
    assertThat(due(field, active(), fill(UDFFillState.NOT_FOUND, null, NOW.minusYears(3), RULE_VERSION), null, null))
        .isEqualTo(SuggestDue.NONE);
  }

  private static SuggestDue due(UDFMetadataSecurity field, Security security, UDFDataFill fill, String value,
      LocalDateTime refreshBefore) {
    return ValueSuggestService.suggestDue(field, security, fill, value, TODAY, refreshBefore);
  }

  private static UDFMetadataSecurity field(UDFFillPeriod period) {
    UDFMetadataSecurity field = new UDFMetadataSecurity();
    field.setFillSource(UDFFillSource.LLM);
    field.setFillRuleVersion(RULE_VERSION);
    field.setFillPeriod(period);
    return field;
  }

  private static Security active() {
    Security security = new Security();
    security.setIdSecuritycurrency(10);
    security.setActiveToDate(TODAY.plusYears(10));
    return security;
  }

  private static UDFDataFill fill(UDFFillState state, String filledValue, LocalDateTime checked, int ruleVersion) {
    UDFDataFill fill = new UDFDataFill(new UDFDataFillKey(1, 10));
    fill.setFillSource(UDFFillSource.LLM);
    fill.setFillState(state);
    fill.setFilledValue(filledValue);
    fill.setCheckedTime(checked);
    fill.setRuleVersion(ruleVersion);
    return fill;
  }
}
