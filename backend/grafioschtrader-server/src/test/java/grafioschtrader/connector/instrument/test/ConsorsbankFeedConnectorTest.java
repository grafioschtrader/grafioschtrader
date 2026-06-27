package grafioschtrader.connector.instrument.test;

import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.consorsbank.ConsorsbankFeedConnector;
import grafioschtrader.entities.Security;
import grafioschtrader.types.SpecialInvestmentInstruments;

class ConsorsbankFeedConnectorTest {

  private ConsorsbankFeedConnector consorsbankFeedConnector = new ConsorsbankFeedConnector();

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();
    securities.add(ConnectorTestHelper.createIntraSecurity("Gold", "_1326189,@W$",
        SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "GOLD"));
    securities.add(ConnectorTestHelper.createIntraSecurity("0.25 Societe Generale 20-27", "_282883661,SWX",
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "SGP20"));
    securities.add(ConnectorTestHelper.createIntraSecurity("SPDR S&P U.S. Energy Select Sector (USD)", "_139582518,SWX",
        SpecialInvestmentInstruments.ETF, "SXLE"));
    securities.parallelStream().forEach(security -> {
      try {
        consorsbankFeedConnector.updateSecurityLastPrice(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      Assertions.assertThat(security.getSLast()).isGreaterThan(0.0);
    });
  }

}
