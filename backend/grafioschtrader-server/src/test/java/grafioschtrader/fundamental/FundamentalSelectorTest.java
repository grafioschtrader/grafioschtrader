package grafioschtrader.fundamental;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.SecurityFundamental;
import grafioschtrader.types.FundamentalMetric;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.types.FundamentalSourceCheck;
import grafioschtrader.types.FundamentalState;

/**
 * Grades of {@link FundamentalSelector} on values recorded by the fundamental source probe on 2026-10-15, judged on
 * that day. No mocks: the selector is a pure function of the rows and the date.
 */
class FundamentalSelectorTest {

  private static final LocalDate AS_OF = LocalDate.of(2026, 10, 15);
  private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 10, 10, 4, 23);

  private static final FundamentalMetric EPS_TTM = FundamentalMetric.EPS_TTM;
  private static final FundamentalMetric EPS_FY = FundamentalMetric.EPS_FY;
  private static final FundamentalSource YAHOO = FundamentalSource.YAHOO;
  private static final FundamentalSource FINNHUB = FundamentalSource.FINNHUB;
  private static final FundamentalSource EULERPOOL = FundamentalSource.EULERPOOL;

  @Test
  @DisplayName("Apple: Finnhub and Yahoo trailing EPS agree, the preferred Finnhub value is confirmed")
  void appleConfirmed() {
    SecurityFundamental finnhub = row(FINNHUB, EPS_TTM, 8.72, null, FundamentalSourceCheck.NOT_CHECKED);
    SecurityFundamental yahoo = row(YAHOO, EPS_TTM, 8.76, "2026-06-27", FundamentalSourceCheck.PASSED);
    Map<FundamentalMetric, SecurityFundamental> selected = FundamentalSelector.select(List.of(finnhub, yahoo), AS_OF);
    assertThat(selected.get(EPS_TTM)).isSameAs(finnhub);
    assertThat(finnhub.getState()).isEqualTo(FundamentalState.CONFIRMED);
    assertThat(yahoo.getState()).isEqualTo(FundamentalState.CANDIDATE);
  }

  @Test
  @DisplayName("Novo Nordisk: Yahoo 3.52 EUR alone with a passed price check is checked")
  void novoNordiskChecked() {
    SecurityFundamental yahoo = row(YAHOO, EPS_TTM, 3.52, "2026-06-30", FundamentalSourceCheck.PASSED);
    Map<FundamentalMetric, SecurityFundamental> selected = FundamentalSelector.select(List.of(yahoo), AS_OF);
    assertThat(selected.get(EPS_TTM)).isSameAs(yahoo);
    assertThat(yahoo.getState()).isEqualTo(FundamentalState.CHECKED);
  }

  @Test
  @DisplayName("Hitachi: a Yahoo EPS that failed the price check is excluded")
  void hitachiExcluded() {
    SecurityFundamental yahoo = row(YAHOO, EPS_TTM, 0.18, "2026-06-30", FundamentalSourceCheck.FAILED);
    assertThat(FundamentalSelector.select(List.of(yahoo), AS_OF)).doesNotContainKey(EPS_TTM);
    assertThat(yahoo.getState()).isEqualTo(FundamentalState.CANDIDATE);
  }

  @Test
  @DisplayName("Trinity Biotech: fiscal year and trailing EPS of the same period contradict, both are suspect")
  void trinityBiotechBothSuspect() {
    SecurityFundamental fy = row(EULERPOOL, EPS_FY, -0.10, "2025-12-31", FundamentalSourceCheck.NOT_CHECKED);
    SecurityFundamental ttm = row(YAHOO, EPS_TTM, -59.24, "2025-12-31", FundamentalSourceCheck.NOT_CHECKED);
    Map<FundamentalMetric, SecurityFundamental> selected = FundamentalSelector.select(List.of(fy, ttm), AS_OF);
    assertThat(selected).doesNotContainKeys(EPS_FY, EPS_TTM);
    assertThat(fy.getState()).isEqualTo(FundamentalState.SUSPECT);
    assertThat(ttm.getState()).isEqualTo(FundamentalState.SUSPECT);
  }

  @Test
  @DisplayName("Merck: fiscal year 7.34 and trailing 1.26 are both selected, 181 days apart they are not compared")
  void merckBothSelected() {
    SecurityFundamental fy = row(EULERPOOL, EPS_FY, 7.34, "2025-12-31", FundamentalSourceCheck.NOT_CHECKED);
    SecurityFundamental ttm = row(YAHOO, EPS_TTM, 1.26, "2026-06-30", FundamentalSourceCheck.PASSED);
    Map<FundamentalMetric, SecurityFundamental> selected = FundamentalSelector.select(List.of(fy, ttm), AS_OF);
    assertThat(selected.get(EPS_FY)).isSameAs(fy);
    assertThat(selected.get(EPS_TTM)).isSameAs(ttm);
    assertThat(fy.getState()).isEqualTo(FundamentalState.SINGLE);
    assertThat(ttm.getState()).isEqualTo(FundamentalState.CHECKED);
  }

  @Test
  @DisplayName("Sony: Yahoo trailing 186.28 stays checked, Eulerpool fiscal year -55.2 one quarter earlier is suspect")
  void sonyFiscalYearSuspect() {
    SecurityFundamental fy = row(EULERPOOL, EPS_FY, -55.2, "2026-03-31", FundamentalSourceCheck.NOT_CHECKED);
    SecurityFundamental ttm = row(YAHOO, EPS_TTM, 186.28, "2026-06-30", FundamentalSourceCheck.PASSED);
    Map<FundamentalMetric, SecurityFundamental> selected = FundamentalSelector.select(List.of(fy, ttm), AS_OF);
    assertThat(selected.get(EPS_TTM)).isSameAs(ttm);
    assertThat(ttm.getState()).isEqualTo(FundamentalState.CHECKED);
    assertThat(selected).doesNotContainKey(EPS_FY);
    assertThat(fy.getState()).isEqualTo(FundamentalState.SUSPECT);
  }

  @Test
  @DisplayName("A fiscal year ending 2024-12-31 is stale before 2025-04-15 and excluded")
  void staleFiscalYearExcluded() {
    SecurityFundamental fy = row(EULERPOOL, EPS_FY, 2.10, "2024-12-31", FundamentalSourceCheck.NOT_CHECKED);
    assertThat(FundamentalSelector.select(List.of(fy), AS_OF)).doesNotContainKey(EPS_FY);
    assertThat(fy.getState()).isEqualTo(FundamentalState.CANDIDATE);
  }

  @Test
  @DisplayName("Two trailing EPS one quarter apart are not compared; the newer one is chosen over the preferred source")
  void newerPeriodChosen() {
    SecurityFundamental older = row(FINNHUB, EPS_TTM, 1.00, "2026-03-31", FundamentalSourceCheck.NOT_CHECKED);
    SecurityFundamental newer = row(YAHOO, EPS_TTM, 1.50, "2026-06-30", FundamentalSourceCheck.PASSED);
    Map<FundamentalMetric, SecurityFundamental> selected = FundamentalSelector.select(List.of(older, newer), AS_OF);
    assertThat(selected.get(EPS_TTM)).isSameAs(newer);
    assertThat(newer.getState()).isEqualTo(FundamentalState.CHECKED);
    assertThat(older.getState()).isEqualTo(FundamentalState.CANDIDATE);
  }

  @Test
  @DisplayName("An undated Finnhub value alone is not chosen")
  void undatedAloneNotChosen() {
    SecurityFundamental finnhub = row(FINNHUB, EPS_TTM, 8.72, null, FundamentalSourceCheck.NOT_CHECKED);
    assertThat(FundamentalSelector.select(List.of(finnhub), AS_OF)).doesNotContainKey(EPS_TTM);
    assertThat(finnhub.getState()).isEqualTo(FundamentalState.CANDIDATE);
  }

  @Test
  @DisplayName("Undated values that contradict a dated one make the whole group suspect")
  void contradictedGroupSuspect() {
    SecurityFundamental finnhub = row(FINNHUB, EPS_TTM, -0.65, null, FundamentalSourceCheck.NOT_CHECKED);
    SecurityFundamental yahoo = row(YAHOO, EPS_TTM, -22.0, "2026-06-30", FundamentalSourceCheck.NOT_CHECKED);
    assertThat(FundamentalSelector.select(List.of(finnhub, yahoo), AS_OF)).doesNotContainKey(EPS_TTM);
    assertThat(finnhub.getState()).isEqualTo(FundamentalState.SUSPECT);
    assertThat(yahoo.getState()).isEqualTo(FundamentalState.SUSPECT);
  }

  @Test
  @DisplayName("The preferred fiscal year made suspect by the trailing comparison is replaced by the next candidate")
  void suspectReplacedByNextCandidate() {
    SecurityFundamental ttm = row(YAHOO, EPS_TTM, 2.00, "2026-03-31", FundamentalSourceCheck.PASSED);
    SecurityFundamental preferred = row(FINNHUB, EPS_FY, -5.00, "2025-12-31", FundamentalSourceCheck.NOT_CHECKED);
    SecurityFundamental next = row(EULERPOOL, EPS_FY, 1.90, "2025-09-30", FundamentalSourceCheck.NOT_CHECKED);
    Map<FundamentalMetric, SecurityFundamental> selected = FundamentalSelector.select(List.of(ttm, preferred, next),
        AS_OF);
    assertThat(preferred.getState()).isEqualTo(FundamentalState.SUSPECT);
    assertThat(selected.get(EPS_FY)).isSameAs(next);
    assertThat(next.getState()).isEqualTo(FundamentalState.SINGLE);
    assertThat(selected.get(EPS_TTM)).isSameAs(ttm);
    assertThat(ttm.getState()).isEqualTo(FundamentalState.CHECKED);
  }

  @Test
  @DisplayName("Yahoo book value is selected in preference to an older Eulerpool value")
  void yahooBookValueSelected() {
    SecurityFundamental yahoo = row(YAHOO, FundamentalMetric.BVPS, 30.1, "2026-06-30",
        FundamentalSourceCheck.NOT_CHECKED);
    SecurityFundamental eulerpool = row(EULERPOOL, FundamentalMetric.BVPS, 29.4, "2025-12-31",
        FundamentalSourceCheck.NOT_CHECKED);
    Map<FundamentalMetric, SecurityFundamental> selected = FundamentalSelector.select(List.of(yahoo, eulerpool), AS_OF);
    assertThat(selected.get(FundamentalMetric.BVPS)).isSameAs(yahoo);
    assertThat(yahoo.getState()).isEqualTo(FundamentalState.SINGLE);
    assertThat(eulerpool.getState()).isEqualTo(FundamentalState.CANDIDATE);
  }

  @Test
  @DisplayName("Check metrics are never selected")
  void checkMetricsNeverSelected() {
    SecurityFundamental pe = row(YAHOO, FundamentalMetric.PE_CHECK, 31.6, null, FundamentalSourceCheck.NOT_CHECKED);
    assertThat(FundamentalSelector.select(List.of(pe), AS_OF)).isEmpty();
    assertThat(pe.getState()).isEqualTo(FundamentalState.CANDIDATE);
  }

  @Test
  @DisplayName("BioNTech: -4.70 USD and -5.14 EUR of the same fiscal year are not compared, so neither is suspect")
  void differentCurrenciesNotCompared() {
    SecurityFundamental finnhub = row(FINNHUB, EPS_FY, -4.7005, "2025-12-31", FundamentalSourceCheck.NOT_CHECKED);
    SecurityFundamental eulerpool = row(EULERPOOL, EPS_FY, -5.1372, "2025-12-31", FundamentalSourceCheck.NOT_CHECKED);
    eulerpool.setCurrency("EUR");
    SecurityFundamental ttm = row(YAHOO, EPS_TTM, -7.83, "2026-03-31", FundamentalSourceCheck.NOT_CHECKED);
    ttm.setCurrency("EUR");
    Map<FundamentalMetric, SecurityFundamental> selected = FundamentalSelector.select(List.of(finnhub, eulerpool, ttm),
        AS_OF);
    assertThat(selected.get(EPS_FY)).isSameAs(finnhub);
    assertThat(finnhub.getState()).isEqualTo(FundamentalState.SINGLE);
    assertThat(eulerpool.getState()).isEqualTo(FundamentalState.CANDIDATE);
    // Fiscal year in USD and trailing in EUR one quarter apart: rule 5 does not compare them either.
    assertThat(ttm.getState()).isEqualTo(FundamentalState.SINGLE);
  }

  private static SecurityFundamental row(FundamentalSource source, FundamentalMetric metric, double value,
      String periodEnd, FundamentalSourceCheck check) {
    SecurityFundamental row = new SecurityFundamental(1, metric, source, value, "USD",
        periodEnd == null ? null : LocalDate.parse(periodEnd), FETCHED);
    row.setSourceCheck(check);
    return row;
  }
}
