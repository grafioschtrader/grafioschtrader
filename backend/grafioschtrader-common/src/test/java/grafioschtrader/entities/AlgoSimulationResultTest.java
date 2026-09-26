package grafioschtrader.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AlgoSimulationResultTest {

  @Test
  void roundsRatioMetricsToTwoPercentageDecimalPlaces() {
    AlgoSimulationResult result = new AlgoSimulationResult();
    result.setTotalReturn(0.6750871383455774);
    result.setAnnualizedReturn(0.08133225568430169);
    result.setMaxDrawdown(-0.17311875018935963);

    assertEquals(0.6751, result.getTotalReturn());
    assertEquals(0.0813, result.getAnnualizedReturn());
    assertEquals(-0.1731, result.getMaxDrawdown());
  }

  @Test
  void roundsAmountsWithTheStandardGrafioschtraderPrecision() {
    AlgoSimulationResult result = new AlgoSimulationResult();
    result.setPaidDividends(31791.654202761805);
    result.setDividendReceivables(435.05835700000006);
    result.setFxMarkupPaid(0.79999999999999);
    result.setFxUncoveredConversions(3);

    assertEquals(31791.65, result.getPaidDividends());
    assertEquals(435.06, result.getDividendReceivables());
    assertEquals(0.8, result.getFxMarkupPaid());
    assertEquals(3, result.getFxUncoveredConversions());
  }

  @Test
  void preservesMissingMetricsAndAmounts() {
    AlgoSimulationResult result = new AlgoSimulationResult();

    assertNull(result.getTotalReturn());
    assertNull(result.getAnnualizedReturn());
    assertNull(result.getMaxDrawdown());
    assertNull(result.getPaidDividends());
    assertNull(result.getDividendReceivables());
    assertNull(result.getFxMarkupPaid());
    assertNull(result.getFxUncoveredConversions());
  }
}
