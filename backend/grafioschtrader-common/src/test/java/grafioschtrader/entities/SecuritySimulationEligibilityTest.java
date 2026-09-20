package grafioschtrader.entities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.types.SpecialInvestmentInstruments;

@DisplayName("Simulation excludes margin, inverse and leveraged securities")
class SecuritySimulationEligibilityTest {
  @Test
  void marginIsExcludedEvenWithUnitLeverage() {
    assertThat(Security.simulationTradingExcluded(SpecialInvestmentInstruments.CFD, 1)).isTrue();
    assertThat(Security.simulationTradingExcluded(SpecialInvestmentInstruments.FOREX, 1)).isTrue();
  }

  @Test
  void onlyExactlyUnitLeverageIsPermitted() {
    for (double factor : new double[] { -1, 2, 0.5, 0, Double.NaN })
      assertThat(Security.simulationTradingExcluded(SpecialInvestmentInstruments.ETF, factor)).isTrue();
    assertThat(Security.simulationTradingExcluded(SpecialInvestmentInstruments.ETF, 1)).isFalse();
  }

  @Test
  void responseFlagUsesTheSameRule() {
    var security = new Security();
    var asset = new Assetclass();
    asset.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.ETF);
    security.setAssetClass(asset);
    security.setLeverageFactor(-1);
    assertThat(security.isSimulationTradingExcluded()).isTrue();
    security.setLeverageFactor(1);
    assertThat(security.isSimulationTradingExcluded()).isFalse();
  }
}
