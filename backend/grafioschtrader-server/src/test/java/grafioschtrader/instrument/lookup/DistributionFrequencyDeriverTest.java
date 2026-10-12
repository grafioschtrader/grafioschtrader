package grafioschtrader.instrument.lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.types.DistributionFrequency;

/**
 * Distribution frequency from the ex-dates of a dividend connector, with 11 October 2026 as reference day: the counted
 * years are 2023 to 2025, the history starts on 1 January 2021.
 */
class DistributionFrequencyDeriverTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

  /** Ex-dates in the given months of every year from 2021 to 2026. */
  private static List<LocalDate> everyYear(int... months) {
    List<LocalDate> dates = new ArrayList<>();
    for (int year = 2021; year <= 2026; year++) {
      for (int month : months) {
        dates.add(LocalDate.of(year, month, 15));
      }
    }
    return dates;
  }

  private static DistributionFrequency derive(List<LocalDate> exDates) {
    return DistributionFrequencyDeriver.derive(exDates, null, TODAY);
  }

  @Test
  @DisplayName("Regular payers: annual, semi-annual, quarterly and monthly")
  void regular() {
    assertEquals(DistributionFrequency.DF_YEAR, derive(everyYear(4)));
    assertEquals(DistributionFrequency.DF_SEMI_ANNUAL, derive(everyYear(3, 9)));
    assertEquals(DistributionFrequency.DF_QUARTERLY, derive(everyYear(3, 6, 9, 12)));
    assertEquals(DistributionFrequency.DF_MONTHLY, derive(everyYear(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)));
  }

  @Test
  @DisplayName("A special dividend or an ex-date moving into January does not change the frequency")
  void robustAgainstSingleDeviations() {
    List<LocalDate> annualWithSpecial = new ArrayList<>(everyYear(4));
    annualWithSpecial.add(LocalDate.of(2024, 11, 20));
    assertEquals(DistributionFrequency.DF_YEAR, derive(annualWithSpecial));

    List<LocalDate> quarterlyShifted = new ArrayList<>(everyYear(3, 6, 9));
    List.of(2021, 2022, 2023, 2025, 2026).forEach(y -> quarterlyShifted.add(LocalDate.of(y, 12, 20)));
    quarterlyShifted.add(LocalDate.of(2025, 1, 3));
    assertEquals(DistributionFrequency.DF_QUARTERLY, derive(quarterlyShifted));
  }

  @Test
  @DisplayName("Irregular payments become ad hoc")
  void irregular() {
    List<LocalDate> dates = List.of(LocalDate.of(2023, 5, 1), LocalDate.of(2024, 2, 1), LocalDate.of(2024, 6, 1),
        LocalDate.of(2024, 9, 1), LocalDate.of(2025, 3, 1), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 8, 1),
        LocalDate.of(2021, 3, 1));
    assertEquals(DistributionFrequency.DF_AD_HOC, derive(dates));
  }

  @Test
  @DisplayName("No distribution at all means none, unless the listing is younger than a year")
  void none() {
    assertEquals(DistributionFrequency.DF_NONE, derive(List.of()));
    assertEquals(DistributionFrequency.DF_NONE,
        DistributionFrequencyDeriver.derive(List.of(), LocalDate.of(2022, 4, 1), TODAY));
    assertNull(DistributionFrequencyDeriver.derive(List.of(), LocalDate.of(2026, 1, 5), TODAY));
  }

  @Test
  @DisplayName("Provider frequency first; 'none' is never proposed for a distributing fund or a young listing")
  void combine() {
    assertEquals(DistributionFrequency.DF_SEMI_ANNUAL,
        DistributionFrequencyDeriver.combine(DistributionFrequency.DF_SEMI_ANNUAL, everyYear(4), null, null, TODAY));
    assertEquals(DistributionFrequency.DF_YEAR,
        DistributionFrequencyDeriver.combine(null, everyYear(4), null, null, TODAY));
    assertNull(DistributionFrequencyDeriver.combine(null, List.of(), LocalDate.of(2020, 1, 1), Boolean.TRUE, TODAY),
        "a distributing fund without dividends yet");
    assertNull(
        DistributionFrequencyDeriver.combine(DistributionFrequency.DF_NONE, List.of(), null, Boolean.TRUE, TODAY),
        "the provider's 'none' for a distributing fund");
    assertNull(DistributionFrequencyDeriver.combine(DistributionFrequency.DF_NONE, List.of(), LocalDate.of(2026, 5, 1),
        null, TODAY), "the provider's 'none' for a young listing");
    assertEquals(DistributionFrequency.DF_NONE,
        DistributionFrequencyDeriver.combine(DistributionFrequency.DF_NONE, List.of(), null, Boolean.FALSE, TODAY));
  }

  @Test
  @DisplayName("Distributions that started recently: the incomplete first year is dropped or the field stays open")
  void startedRecently() {
    List<LocalDate> sinceMid2024 = new ArrayList<>(List.of(LocalDate.of(2024, 9, 15), LocalDate.of(2024, 12, 15)));
    List.of(3, 6, 9, 12).forEach(m -> sinceMid2024.add(LocalDate.of(2025, m, 15)));
    assertEquals(DistributionFrequency.DF_QUARTERLY, derive(sinceMid2024));

    assertNull(derive(List.of(LocalDate.of(2026, 3, 15))), "only distributions of the current year");
    assertNull(DistributionFrequencyDeriver.derive(List.of(LocalDate.of(2025, 9, 15), LocalDate.of(2025, 12, 15)),
        LocalDate.of(2025, 6, 1), TODAY), "the only counted year is the year the listing started");
  }
}
