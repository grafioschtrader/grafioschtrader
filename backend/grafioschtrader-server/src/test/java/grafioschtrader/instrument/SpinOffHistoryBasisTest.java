package grafioschtrader.instrument;

import static grafioschtrader.instrument.SpinOffProviderAdjustmentTest.quote;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.instrument.SpinOffHistoryBasisDecider.Decision;
import grafioschtrader.instrument.SpinOffHistoryBasisDecider.Input;
import grafioschtrader.types.SecurityActionType;
import grafioschtrader.types.SpinOffHistoryBasis;
import grafioschtrader.types.SpinOffHistoryBasisOrigin;

/**
 * Decision of the history basis on recorded series: General Electric from Yahoo (step on a freshly loaded parent,
 * reference against the as-traded closes of the production database), and the foreign sources Onvista and SIX.
 */
class SpinOffHistoryBasisTest {

  private static final String YAHOO = "gt.datafeed.yahoo";
  private static final LocalDate HEALTHCARE = LocalDate.of(2023, 1, 4);

  @Test
  void stepDecidesBothSpinOffsOfAFreshlyLoadedGeneralElectric() {
    Decision healthcare = decide(spinOff(HEALTHCARE, 1.281), true, false, false, 1.0, List.of(),
        series(HEALTHCARE, 52.94397354, 56.02553940));
    assertThat(healthcare.basis()).isEqualTo(SpinOffHistoryBasis.ADJUSTED);
    assertThat(healthcare.factor()).isEqualTo(1.281);
    assertThat(healthcare.origin()).isEqualTo(SpinOffHistoryBasisOrigin.STEP);

    LocalDate vernova = LocalDate.of(2024, 4, 2);
    Decision vernovaDecision = decide(spinOff(vernova, 1.253), true, false, false, 1.0, List.of(),
        series(vernova, 139.95211792, 136.47000122));
    assertThat(vernovaDecision.basis()).isEqualTo(SpinOffHistoryBasis.ADJUSTED);
    assertThat(vernovaDecision.factor()).isEqualTo(1.253);
  }

  @Test
  void referenceMeasuresTheFactorAgainstTheAsTradedClosesOfTheProductionDatabase() {
    List<Historyquote> delivered = window(HEALTHCARE, 50.0, 55.0);
    // Stored before both spin-offs: before HealthCare carries 1.281 x 1.253, from it on 1.253 (Vernova still ahead)
    List<Historyquote> stored = scaled(delivered, HEALTHCARE, 1.6051, 1.2530);
    Decision decision = decide(spinOff(HEALTHCARE, 1.281), true, true, true, 1.0, stored, delivered);

    assertThat(decision.basis()).isEqualTo(SpinOffHistoryBasis.ADJUSTED);
    assertThat(decision.factor()).isCloseTo(1.2810, within(1e-4));
    assertThat(decision.origin()).isEqualTo(SpinOffHistoryBasisOrigin.REFERENCE);
  }

  @Test
  void foreignSourcesYieldNoFactorFromTheSplitConnector() {
    LocalDate viatris = LocalDate.of(2020, 11, 17);
    Decision pfizer = decide(spinOff(viatris, 1.054), false, false, false, 1.0, List.of(),
        series(viatris, 33.4767, 36.04));
    assertThat(pfizer).isEqualTo(Decision.UNDETERMINED);

    Decision lookingAdjusted = decide(spinOff(HEALTHCARE, 1.281), false, false, false, 1.0, List.of(),
        series(HEALTHCARE, 52.94397354, 56.02553940));
    assertThat(lookingAdjusted).isEqualTo(Decision.UNDETERMINED);

    LocalDate daimlerTruck = LocalDate.of(2021, 12, 10);
    Decision mercedes = decide(spinOff(daimlerTruck, 1.20), false, false, false, 1.0, List.of(),
        series(daimlerTruck, 86.18, 74.25));
    assertThat(mercedes.basis()).isEqualTo(SpinOffHistoryBasis.UNADJUSTED);
    assertThat(mercedes.origin()).isEqualTo(SpinOffHistoryBasisOrigin.STEP);

    LocalDate siemensEnergy = LocalDate.of(2020, 9, 28);
    Decision siemens = decide(spinOff(siemensEnergy, 1.05), false, false, false, 1.0, List.of(),
        series(siemensEnergy, 111.68, 109.96));
    assertThat(siemens).isEqualTo(Decision.UNDETERMINED);
  }

  @Test
  void anUnadjustedDeliveryIsStoredAsItIsUntilTheProviderAdjustsItsSeries() {
    List<Historyquote> raw = window(HEALTHCARE, 84.0, 70.0);
    SecurityAction spinOff = spinOff(HEALTHCARE, 1.281);
    Decision first = decide(spinOff, true, true, true, 1.0, raw, raw);
    assertThat(first.basis()).isEqualTo(SpinOffHistoryBasis.UNADJUSTED);
    assertThat(first.origin()).isEqualTo(SpinOffHistoryBasisOrigin.REFERENCE);

    apply(spinOff, first);
    List<Historyquote> adjusted = scaled(raw, HEALTHCARE, 1 / 1.281, 1.0);
    Decision later = decide(spinOff, true, true, false, 1.0, raw, adjusted);
    assertThat(later.basis()).isEqualTo(SpinOffHistoryBasis.ADJUSTED);
    assertThat(later.factor()).isCloseTo(1.281, within(1e-9));
  }

  @Test
  void aPendingAdministratorSettingBeatsTheReferenceAnAppliedOneYieldsToIt() {
    List<Historyquote> raw = window(HEALTHCARE, 84.0, 70.0);
    SecurityAction spinOff = spinOff(HEALTHCARE, 1.281);
    spinOff.setHistoryBasis(SpinOffHistoryBasis.ADJUSTED);
    spinOff.setHistoryFactor(1.3);
    spinOff.setHistoryBasisOrigin(SpinOffHistoryBasisOrigin.ADMINISTRATOR);
    spinOff.setHistoryConnector(YAHOO);
    spinOff.setHistoryUrlExtend("GE");

    Decision pending = decide(spinOff, true, true, true, 1.0, raw, raw);
    assertThat(pending)
        .isEqualTo(new Decision(SpinOffHistoryBasis.ADJUSTED, 1.3, SpinOffHistoryBasisOrigin.ADMINISTRATOR));

    spinOff.setHistoryBasisApplied(true);
    Decision applied = decide(spinOff, true, true, true, 1.0, raw, raw);
    assertThat(applied.basis()).isEqualTo(SpinOffHistoryBasis.UNADJUSTED);
    assertThat(applied.origin()).isEqualTo(SpinOffHistoryBasisOrigin.REFERENCE);

    spinOff.setHistoryBasisApplied(false);
    spinOff.setHistoryUrlExtend("GE.OLD");
    Decision otherSource = decide(spinOff, true, false, false, 1.0, List.of(),
        series(HEALTHCARE, 52.94397354, 56.02553940));
    assertThat(otherSource.origin()).isEqualTo(SpinOffHistoryBasisOrigin.STEP);
    assertThat(otherSource.factor()).isEqualTo(1.281);
  }

  @Test
  void aRealSplitOnTheActionDateIsDividedOutOfTheProviderFigure() {
    SecurityAction spinOff = spinOff(HEALTHCARE, 2.5);
    Decision step = decide(spinOff, true, false, false, 2.0, List.of(), series(HEALTHCARE, 40.0, 41.0));
    assertThat(step.basis()).isEqualTo(SpinOffHistoryBasis.ADJUSTED);
    assertThat(step.factor()).isCloseTo(1.25, within(1e-12));

    List<Historyquote> delivered = window(HEALTHCARE, 40.0, 41.0);
    List<Historyquote> storedBeforeSplit = scaled(delivered, HEALTHCARE, 2.5, 1.0);
    Decision reference = decide(spinOff, true, true, true, 2.0, storedBeforeSplit, delivered);
    assertThat(reference.factor()).isCloseTo(1.25, within(1e-12));
  }

  private static Decision decide(SecurityAction spinOff, boolean sameSource, boolean storedUnadjusted,
      boolean loadedBefore, double sameDaySplitFactor, List<Historyquote> stored, List<Historyquote> delivered) {
    return SpinOffHistoryBasisDecider.decide(new Input(spinOff, YAHOO, "GE", sameSource, storedUnadjusted,
        loadedBefore, sameDaySplitFactor, stored, delivered));
  }

  private static void apply(SecurityAction spinOff, Decision decision) {
    spinOff.setHistoryBasis(decision.basis());
    spinOff.setHistoryFactor(decision.factor());
    spinOff.setHistoryBasisOrigin(decision.origin());
    spinOff.setHistoryConnector(YAHOO);
    spinOff.setHistoryUrlExtend("GE");
    spinOff.setHistoryBasisApplied(decision.basis() != SpinOffHistoryBasis.UNDETERMINED);
  }

  private static SecurityAction spinOff(LocalDate date, double providerSplitFactor) {
    SecurityAction action = new SecurityAction();
    action.setActionType(SecurityActionType.SPIN_OFF);
    action.setActionDate(date);
    action.setProviderSplitFactor(providerSplitFactor);
    action.setHistoryBasis(SpinOffHistoryBasis.UNDETERMINED);
    return action;
  }

  /** The last close before and the close on the action date, one business day apart. */
  private static List<Historyquote> series(LocalDate actionDate, double closeBefore, double closeOn) {
    return List.of(quote(previousBusinessDay(actionDate), closeBefore), quote(actionDate, closeOn));
  }

  /** Business days of the ten-day window around the action date, flat at one price before and another from it on. */
  private static List<Historyquote> window(LocalDate actionDate, double before, double from) {
    List<Historyquote> quotes = new ArrayList<>();
    for (LocalDate d = actionDate.minusDays(10); !d.isAfter(actionDate.plusDays(10)); d = d.plusDays(1)) {
      if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
        quotes.add(quote(d, d.isBefore(actionDate) ? before : from));
      }
    }
    return quotes;
  }

  private static List<Historyquote> scaled(List<Historyquote> quotes, LocalDate actionDate, double before,
      double from) {
    return quotes.stream()
        .map(q -> quote(q.getDate(), q.getClose() * (q.getDate().isBefore(actionDate) ? before : from))).toList();
  }

  private static LocalDate previousBusinessDay(LocalDate date) {
    LocalDate d = date.minusDays(1);
    while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
      d = d.minusDays(1);
    }
    return d;
  }
}
