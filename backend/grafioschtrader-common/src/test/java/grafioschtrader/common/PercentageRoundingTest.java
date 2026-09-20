package grafioschtrader.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PercentageRoundingTest {

  @ParameterizedTest
  @CsvSource({ "12.3456,12.35", "-12.3456,-12.35", "1.234,1.23", "0.004,0", "-0.004,0", "0,0", "100,100" })
  void roundsPercentagePoints(double input, double expected) {
    assertEquals(expected, DataBusinessHelper.roundPercentage(input));
  }
}
