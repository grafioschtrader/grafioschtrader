package grafioschtrader.fundamental;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import grafioschtrader.connector.fundamental.FundamentalAnswer;
import grafioschtrader.entities.SecurityFundamental;
import grafioschtrader.types.FundamentalMetric;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.types.FundamentalSourceCheck;
import grafioschtrader.types.FundamentalState;

/** Regressions for the Adobe/Booking stock-versus-average conflict and selection of original SEC disclosures. */
class FundamentalSharesTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
  private static final LocalDateTime FETCHED = TODAY.atTime(6, 0);

  @ParameterizedTest
  @CsvSource({ "389200000, 427000000, 238.54, 97389510656", "774878436, 815975000, 161.20, 114782748672" })
  @DisplayName("Adobe/Booking: a fiscal-year denominator cannot suppress a checked outstanding stock")
  void fiscalYearAverageDoesNotContradictStock(double stock, double average, double price, double marketCap) {
    SecurityFundamental yahoo = row(FundamentalSource.YAHOO, FundamentalMetric.SHARES, stock, null);
    SecurityFundamental fiscalYear = row(FundamentalSource.EULERPOOL, FundamentalMetric.SHARES_FY, average,
        TODAY.minusYears(1));
    SecurityFundamental cap = row(FundamentalSource.YAHOO, FundamentalMetric.MCAP_CHECK, marketCap, null);
    cap.setCurrency("USD");
    List<SecurityFundamental> all = FundamentalDataLoadService.combine(List.of(),
        Map.of(FundamentalSource.YAHOO, FundamentalAnswer.usable(List.of(yahoo, cap), Map.of()),
            FundamentalSource.EULERPOOL, FundamentalAnswer.usable(List.of(fiscalYear), Map.of())),
        price, "USD", (_, _) -> null, TODAY, null);
    assertThat(FundamentalSelector.select(all, TODAY).get(FundamentalMetric.SHARES)).isSameAs(yahoo);
    assertThat(yahoo.getState()).isEqualTo(FundamentalState.CHECKED);
    assertThat(fiscalYear.getState()).isEqualTo(FundamentalState.CANDIDATE);
  }

  @Test
  @DisplayName("A dated SEC stock wins over Yahoo, even when an obsolete vendor market cap would fail its check")
  void originalDisclosureWins() {
    SecurityFundamental sec = row(FundamentalSource.SEC, FundamentalMetric.SHARES, 743_701_257,
        LocalDate.of(2026, 10, 26));
    SecurityFundamental yahoo = checkedYahoo();
    SecurityFundamental cap = row(FundamentalSource.YAHOO, FundamentalMetric.MCAP_CHECK, 1e12, null);
    cap.setCurrency("USD");
    FundamentalSourceChecks.apply(List.of(sec), List.of(sec, yahoo, cap), Map.of(), 161.2, "USD", (_, _) -> null);

    assertThat(FundamentalSelector.select(List.of(sec, yahoo), TODAY).get(FundamentalMetric.SHARES)).isSameAs(sec);
    assertThat(sec.getSourceCheck()).isEqualTo(FundamentalSourceCheck.NOT_CHECKED);
    assertThat(sec.getState()).isEqualTo(FundamentalState.SINGLE);
  }

  @Test
  @DisplayName("After a split, an old SEC stock downloaded today cannot suppress a checked post-split Yahoo stock")
  void preSplitDisclosureFallsBackToYahoo() {
    SecurityFundamental sec = row(FundamentalSource.SEC, FundamentalMetric.SHARES, 32_000_000,
        LocalDate.of(2026, 9, 18));
    SecurityFundamental yahoo = checkedYahoo();
    var selected = FundamentalSelector.select(List.of(sec, yahoo), TODAY, LocalDate.of(2026, 10, 1));
    assertThat(selected.get(FundamentalMetric.SHARES)).isSameAs(yahoo);
  }

  @Test
  @DisplayName("A fresh download cannot make an obsolete measurement current; a disabled SEC feed expires too")
  void oldStockAndOldFetchFallBackToYahoo() {
    SecurityFundamental sec = row(FundamentalSource.SEC, FundamentalMetric.SHARES, 800_000_000,
        TODAY.minusMonths(6).minusDays(1));
    SecurityFundamental yahoo = checkedYahoo();
    assertThat(FundamentalSelector.select(List.of(sec, yahoo), TODAY).get(FundamentalMetric.SHARES)).isSameAs(yahoo);
    sec.setPeriodEnd(TODAY.minusDays(30));
    sec.setFetchedTime(FETCHED.minusDays(22));
    assertThat(FundamentalSelector.select(List.of(sec, yahoo), TODAY).get(FundamentalMetric.SHARES)).isSameAs(yahoo);
  }

  private static SecurityFundamental checkedYahoo() {
    SecurityFundamental yahoo = row(FundamentalSource.YAHOO, FundamentalMetric.SHARES, 774_878_436, null);
    yahoo.setSourceCheck(FundamentalSourceCheck.PASSED);
    return yahoo;
  }

  private static SecurityFundamental row(FundamentalSource source, FundamentalMetric metric, double value,
      LocalDate date) {
    return new SecurityFundamental(1, metric, source, value, null, date, FETCHED);
  }
}
