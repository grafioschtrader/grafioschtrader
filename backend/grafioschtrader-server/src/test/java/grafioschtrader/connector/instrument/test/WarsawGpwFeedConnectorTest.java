package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.warsawgpw.WarsawGpwFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class WarsawGpwFeedConnectorTest {

  private WarsawGpwFeedConnector warsawGpwFeedConnector = new WarsawGpwFeedConnector();

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();
    securities.add(ConnectorTestHelper.createIntraSecurity("WIG", "PL9999999995",
        SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, GlobalConstants.STOCK_EX_MIC_WARSAW));
    securities.add(ConnectorTestHelper.createIntraSecurity("PKOBP", "PLPKO0000016",
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, GlobalConstants.STOCK_EX_MIC_WARSAW));
    securities.add(ConnectorTestHelper.createIntraSecurity("GETIN", "PLGSPR000014",
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, GlobalConstants.STOCK_EX_MIC_WARSAW));
  
  
    securities.parallelStream().forEach(security -> {
      try {
        warsawGpwFeedConnector.updateSecurityLastPrice(security);
        System.out.println(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(security.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  @Test
  void getEodSecurityHistoryTest() {
    final List<Security> securities = new ArrayList<>();
    final LocalDate from = LocalDate.parse("2000-03-01");
    final LocalDate to = LocalDate.parse("2022-11-18");
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    securities.add(ConnectorTestHelper.createHistoricalSecurity("WIG20", "PL9999999987"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("PKO BANK POLSKI SA", "PLPKO0000016"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("BETA ETF WIG20LEV Portfelowy", "PLBEW2L00019"));
    securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = warsawGpwFeedConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println("Ticker=" + security.getUrlHistoryExtend() + " Historyquote-Size=" + historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(687);
    });
  }

}
