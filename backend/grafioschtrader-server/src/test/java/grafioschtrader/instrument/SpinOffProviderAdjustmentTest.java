package grafioschtrader.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.types.SecurityActionType;
import grafioschtrader.types.SpinOffHistoryBasis;

/**
 * Revert of the delivered history of a spin-off parent, with the Yahoo closes of General Electric (GE HealthCare on
 * 2023-01-04, GE Vernova on 2024-04-02) as delivered on 2026-11-21 and the as-traded closes stored in the production
 * database before Yahoo adjusted its series.
 */
class SpinOffProviderAdjustmentTest {

  private static final LocalDate HEALTHCARE = LocalDate.of(2023, 1, 4);
  private static final LocalDate VERNOVA = LocalDate.of(2024, 4, 2);

  @Test
  void restoresTheAsTradedClosesOfGeneralElectric() {
    List<Historyquote> quotes = yahooQuotes();
    SpinOffProviderAdjustment.revert(List.of(spinOff(HEALTHCARE, SpinOffHistoryBasis.ADJUSTED, 1.281),
        spinOff(VERNOVA, SpinOffHistoryBasis.ADJUSTED, 1.253)), quotes, false);

    double[] stored = { 84.98, 70.20, 175.36, 136.47 };
    for (int i = 0; i < stored.length; i++) {
      assertThat(quotes.get(i).getClose()).isCloseTo(stored[i], within(5e-4));
    }
    Historyquote first = quotes.getFirst();
    assertThat(first.getHigh()).isCloseTo(60.0 * 1.281 * 1.253, within(1e-9));
    assertThat(first.getOpen()).isCloseTo(50.0 * 1.281 * 1.253, within(1e-9));
    assertThat(first.getLow()).isNull();
    assertThat(first.getVolume()).isEqualTo(10_280_781L);
  }

  @Test
  void leavesAsTradedOpenHighLowOfAnAdjustedCloseUnchanged() {
    List<Historyquote> quotes = yahooQuotes();
    SpinOffProviderAdjustment.revert(List.of(spinOff(HEALTHCARE, SpinOffHistoryBasis.ADJUSTED, 1.281)), quotes, true);

    Historyquote first = quotes.getFirst();
    assertThat(first.getClose()).isCloseTo(52.94397354 * 1.281, within(1e-9));
    assertThat(first.getHigh()).isEqualTo(60.0);
    assertThat(first.getOpen()).isEqualTo(50.0);
  }

  @Test
  void changesNothingForUnadjustedOrUndeterminedSpinOffsAndFromTheLatestActionDateOn() {
    List<Historyquote> quotes = yahooQuotes();
    SpinOffProviderAdjustment.revert(List.of(spinOff(HEALTHCARE, SpinOffHistoryBasis.UNADJUSTED, null),
        spinOff(VERNOVA, SpinOffHistoryBasis.UNDETERMINED, null)), quotes, false);
    assertThat(quotes).extracting(Historyquote::getClose).containsExactly(52.94397354, 56.02553940, 139.95211792,
        136.47000122);

    List<Historyquote> later = yahooQuotes();
    SpinOffProviderAdjustment.revert(List.of(spinOff(HEALTHCARE, SpinOffHistoryBasis.ADJUSTED, 1.281),
        spinOff(VERNOVA, SpinOffHistoryBasis.ADJUSTED, 1.253)), later, false);
    assertThat(later.getLast().getClose()).isEqualTo(136.47000122);
  }

  @Test
  void scalesTheOlderStretchOnlyByTheFactorsOfAdjustedSpinOffs() {
    List<Historyquote> quotes = yahooQuotes();
    SpinOffProviderAdjustment.revert(List.of(spinOff(HEALTHCARE, SpinOffHistoryBasis.UNDETERMINED, null),
        spinOff(VERNOVA, SpinOffHistoryBasis.ADJUSTED, 1.253)), quotes, false);

    assertThat(quotes.get(0).getClose()).isCloseTo(52.94397354 * 1.253, within(1e-9));
    assertThat(quotes.get(1).getClose()).isCloseTo(56.02553940 * 1.253, within(1e-9));
  }

  private static List<Historyquote> yahooQuotes() {
    Historyquote first = quote(LocalDate.of(2023, 1, 3), 52.94397354);
    first.setOpen(50.0);
    first.setHigh(60.0);
    first.setVolume(10_280_781L);
    return List.of(first, quote(HEALTHCARE, 56.02553940), quote(LocalDate.of(2024, 4, 1), 139.95211792),
        quote(VERNOVA, 136.47000122));
  }

  static Historyquote quote(LocalDate date, double close) {
    Historyquote quote = new Historyquote(date);
    quote.setClose(close);
    return quote;
  }

  static SecurityAction spinOff(LocalDate date, SpinOffHistoryBasis basis, Double factor) {
    SecurityAction action = new SecurityAction();
    action.setActionType(SecurityActionType.SPIN_OFF);
    action.setActionDate(date);
    action.setHistoryBasis(basis);
    action.setHistoryFactor(factor);
    return action;
  }
}
