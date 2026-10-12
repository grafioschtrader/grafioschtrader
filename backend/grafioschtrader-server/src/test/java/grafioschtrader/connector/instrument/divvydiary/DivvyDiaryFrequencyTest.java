package grafioschtrader.connector.instrument.divvydiary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.types.DistributionFrequency;

/** Mapping of DivvyDiary's {@code dividendFrequency}, with the values observed on 2026-10-11. */
class DivvyDiaryFrequencyTest {

  @Test
  @DisplayName("annually, biannually, quarterly, monthly and none map; anything else stays open")
  void mapping() {
    assertEquals(DistributionFrequency.DF_YEAR, DivvyDiaryConnector.toDistributionFrequency("annually"));
    assertEquals(DistributionFrequency.DF_SEMI_ANNUAL, DivvyDiaryConnector.toDistributionFrequency("biannually"));
    assertEquals(DistributionFrequency.DF_QUARTERLY, DivvyDiaryConnector.toDistributionFrequency("quarterly"));
    assertEquals(DistributionFrequency.DF_MONTHLY, DivvyDiaryConnector.toDistributionFrequency("monthly"));
    assertEquals(DistributionFrequency.DF_NONE, DivvyDiaryConnector.toDistributionFrequency("none"));
    assertNull(DivvyDiaryConnector.toDistributionFrequency("irregular"));
    assertNull(DivvyDiaryConnector.toDistributionFrequency(null));
  }
}
