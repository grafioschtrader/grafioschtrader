package grafioschtrader.fundamental;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.fundamental.FundamentalAnswer;
import grafioschtrader.entities.SecurityFundamental;
import grafioschtrader.types.FundamentalMetric;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.types.FundamentalSourceCheck;
import grafioschtrader.types.FundamentalState;

/**
 * The load-time checks of {@link FundamentalSourceChecks} and the replacement rule of
 * {@link FundamentalDataLoadService#combine}: which rows survive a run, and which grade a checked row reaches.
 */
class FundamentalSourceChecksTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
  private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 10, 10, 4, 23);
  private static final LocalDate QUARTER = LocalDate.of(2026, 6, 30);
  private static final LocalDate FISCAL_YEAR = LocalDate.of(2025, 12, 31);
  private static final FundamentalSourceChecks.ExchangeRate NO_PAIR = (_, _) -> null;

  @Test
  @DisplayName("Novo Nordisk in EUR: 3.52 EUR trailing EPS reproduces the P/E with the EUR price and passes")
  void epsInQuoteCurrencyPasses() {
    SecurityFundamental eps = row(FundamentalSource.YAHOO, FundamentalMetric.EPS_TTM, 3.52, "EUR", QUARTER);
    SecurityFundamental pe = row(FundamentalSource.YAHOO, FundamentalMetric.PE_CHECK, 17.1, null, null);
    FundamentalSourceChecks.apply(List.of(eps, pe), List.of(eps, pe), Map.of(), 60.0, "EUR", NO_PAIR);
    assertThat(eps.getSourceCheck()).isEqualTo(FundamentalSourceCheck.PASSED);
    assertThat(pe.getSourceCheck()).isEqualTo(FundamentalSourceCheck.NOT_CHECKED);
  }

  @Test
  @DisplayName("Hitachi: price divided by the EPS gives 0.18 against a P/E of 31.6, the EPS fails")
  void epsNotFittingThePriceFails() {
    SecurityFundamental eps = row(FundamentalSource.YAHOO, FundamentalMetric.EPS_TTM, 22_222.2, "JPY", QUARTER);
    SecurityFundamental pe = row(FundamentalSource.YAHOO, FundamentalMetric.PE_CHECK, 31.6, null, null);
    FundamentalSourceChecks.apply(List.of(eps, pe), List.of(eps, pe), Map.of(), 4000.0, "JPY", NO_PAIR);
    assertThat(eps.getSourceCheck()).isEqualTo(FundamentalSourceCheck.FAILED);
  }

  @Test
  @DisplayName("The price is converted into the quote currency of the Yahoo symbol; without a pair nothing is checked")
  void priceConvertedIntoQuoteCurrency() {
    SecurityFundamental eps = row(FundamentalSource.YAHOO, FundamentalMetric.EPS_TTM, 5.0, "USD", QUARTER);
    SecurityFundamental pe = row(FundamentalSource.YAHOO, FundamentalMetric.PE_CHECK, 20.0, null, null);
    FundamentalSourceChecks.apply(List.of(eps), List.of(eps, pe), Map.of(), 80.0, "CHF",
        (from, to) -> "CHF".equals(from) && "USD".equals(to) ? 1.25 : null);
    assertThat(eps.getSourceCheck()).isEqualTo(FundamentalSourceCheck.PASSED);

    FundamentalSourceChecks.apply(List.of(eps), List.of(eps, pe), Map.of(), 80.0, "CHF", NO_PAIR);
    assertThat(eps.getSourceCheck()).isEqualTo(FundamentalSourceCheck.NOT_CHECKED);
  }

  @Test
  @DisplayName("A negative EPS or a missing P/E leaves the EPS not checked")
  void missingInputNotChecked() {
    SecurityFundamental loss = row(FundamentalSource.YAHOO, FundamentalMetric.EPS_TTM, -0.65, "USD", QUARTER);
    SecurityFundamental pe = row(FundamentalSource.YAHOO, FundamentalMetric.PE_CHECK, 20.0, null, null);
    FundamentalSourceChecks.apply(List.of(loss), List.of(loss, pe), Map.of(), 10.0, "USD", NO_PAIR);
    assertThat(loss.getSourceCheck()).isEqualTo(FundamentalSourceCheck.NOT_CHECKED);

    SecurityFundamental eps = row(FundamentalSource.YAHOO, FundamentalMetric.EPS_TTM, 2.0, "USD", QUARTER);
    FundamentalSourceChecks.apply(List.of(eps), List.of(eps), Map.of(), 10.0, "USD", NO_PAIR);
    assertThat(eps.getSourceCheck()).isEqualTo(FundamentalSourceCheck.NOT_CHECKED);
  }

  @Test
  @DisplayName("ADR: the count of ordinary shares behind the ADRs fails against the market capitalisation")
  void adrShareCountFails() {
    SecurityFundamental marketCap = row(FundamentalSource.YAHOO, FundamentalMetric.MCAP_CHECK, 288e9, "USD", null);
    SecurityFundamental adrShares = row(FundamentalSource.YAHOO, FundamentalMetric.SHARES, 2.4e9, null, null);
    SecurityFundamental ordinaryShares = row(FundamentalSource.EULERPOOL, FundamentalMetric.SHARES, 19.2e9, null,
        FISCAL_YEAR);
    List<SecurityFundamental> rows = List.of(marketCap, adrShares, ordinaryShares);
    FundamentalSourceChecks.apply(rows, rows, Map.of(), 120.0, "USD", NO_PAIR);
    assertThat(adrShares.getSourceCheck()).isEqualTo(FundamentalSourceCheck.PASSED);
    assertThat(ordinaryShares.getSourceCheck()).isEqualTo(FundamentalSourceCheck.FAILED);
  }

  @Test
  @DisplayName("Eulerpool units: a share count off by the millions factor fails, a net income of 0 is not checked")
  void eulerpoolUnits() {
    SecurityFundamental eps = row(FundamentalSource.EULERPOOL, FundamentalMetric.EPS_FY, 2.0, "EUR", FISCAL_YEAR);
    SecurityFundamental shares = row(FundamentalSource.EULERPOOL, FundamentalMetric.SHARES, 500e12, null, FISCAL_YEAR);
    List<SecurityFundamental> rows = List.of(eps, shares);
    FundamentalSourceChecks.apply(rows, rows, evidence(1.0e9), 30.0, "EUR", NO_PAIR);
    assertThat(shares.getSourceCheck()).isEqualTo(FundamentalSourceCheck.FAILED);

    shares.setValue(500e6);
    FundamentalSourceChecks.apply(rows, rows, evidence(1.0e9), 30.0, "EUR", NO_PAIR);
    assertThat(shares.getSourceCheck()).isEqualTo(FundamentalSourceCheck.NOT_CHECKED);

    shares.setValue(500e12);
    FundamentalSourceChecks.apply(rows, rows, evidence(0.0), 30.0, "EUR", NO_PAIR);
    assertThat(shares.getSourceCheck()).isEqualTo(FundamentalSourceCheck.NOT_CHECKED);
  }

  @Test
  @DisplayName("The rows of a failed source survive, the rows of an answering source are replaced")
  void failedSourceRowsSurvive() {
    SecurityFundamental storedFy = row(FundamentalSource.EULERPOOL, FundamentalMetric.EPS_FY, 7.34, "USD", FISCAL_YEAR);
    SecurityFundamental storedTtm = row(FundamentalSource.YAHOO, FundamentalMetric.EPS_TTM, 1.10, "USD",
        LocalDate.of(2026, 3, 31));
    SecurityFundamental storedBvps = row(FundamentalSource.YAHOO, FundamentalMetric.BVPS, 18.0, "USD", QUARTER);
    SecurityFundamental newTtm = row(FundamentalSource.YAHOO, FundamentalMetric.EPS_TTM, 1.26, "USD", QUARTER);
    SecurityFundamental newPe = row(FundamentalSource.YAHOO, FundamentalMetric.PE_CHECK, 80.0, null, null);
    Map<FundamentalSource, FundamentalAnswer> usable = new EnumMap<>(FundamentalSource.class);
    usable.put(FundamentalSource.YAHOO, FundamentalAnswer.usable(List.of(newTtm, newPe), Map.of()));

    List<SecurityFundamental> result = FundamentalDataLoadService.combine(List.of(storedFy, storedTtm, storedBvps),
        usable, 100.0, "USD", NO_PAIR, TODAY);

    assertThat(result).containsExactlyInAnyOrder(storedFy, newTtm, newPe);
    assertThat(newTtm.getSourceCheck()).isEqualTo(FundamentalSourceCheck.PASSED);
    assertThat(newTtm.getState()).isEqualTo(FundamentalState.CHECKED);
    assertThat(storedFy.getState()).isEqualTo(FundamentalState.SINGLE);
  }

  private static Map<FundamentalSource, Map<String, Double>> evidence(double netIncome) {
    return Map.of(FundamentalSource.EULERPOOL, Map.of(FundamentalAnswer.EVIDENCE_NET_INCOME, netIncome));
  }

  private static SecurityFundamental row(FundamentalSource source, FundamentalMetric metric, double value,
      String currency, LocalDate periodEnd) {
    return new SecurityFundamental(1, metric, source, value, currency, periodEnd, FETCHED);
  }
}
