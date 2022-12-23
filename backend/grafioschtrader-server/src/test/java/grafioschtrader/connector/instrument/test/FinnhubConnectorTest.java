package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.finnhub.FinnhubConnector;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;

@SpringBootTest(classes = GTforTest.class)
class FinnhubConnectorTest {

  @Autowired
  private FinnhubConnector finnhubConnector;
  
  final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
      .withLocale(Locale.GERMAN);
  
  @Test
  void getEodSecurityHistoryTest() {
  
    final List<Security> securities = new ArrayList<>();
    
    final LocalDate from = LocalDate.parse("03.01.2010", germanFormatter);
    final LocalDate to = LocalDate.parse("26.03.2020", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
  
    // Indices may not be supported anymore
    // securities.add(createSecurity("^NDX", "NYSE", "USD", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES));
    // securities.add(createSecurity("^GSPC", "NYSE", "USD", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES));

    securities.add(createSecurity("csco", "NYSE", "USD", SpecialInvestmentInstruments.DIRECT_INVESTMENT));
   // securities.add(createSecurity("XSGL.MI", "MTA", "EUR", SpecialInvestmentInstruments.ETF));
   // securities.add(createSecurity("NESN.SW", "SIX", "CHF", SpecialInvestmentInstruments.DIRECT_INVESTMENT));
   //  securities.add(createSecurity("XSGL.MI", "MTA", "EUR", SpecialInvestmentInstruments.ETF));
   // securities.add(createSecurity("CAC.PA", "Euronext", "EUR", SpecialInvestmentInstruments.ETF));
   // securities.add(createSecurity("OILUSA.SW", "SIX", "USD", SpecialInvestmentInstruments.ETF));

    securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = finnhubConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println("Ticker=" + security.getUrlHistoryExtend() + " Historyquote-Size=" + historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(300);
    });
  }
  
  
  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();
    
    // Indices may not be supported anymore
    //  securities.add(createSecurity("^GSPC", "NYSE", "USD", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES));
    
    // Not supporting for free
    // securities.add(createSecurity("LYHLT.SW", "SIX", "EUR", SpecialInvestmentInstruments.ETF)); 
    securities.add(createSecurity("NESN.SW", "SIX", "CHF", SpecialInvestmentInstruments.DIRECT_INVESTMENT));

    // Only US Market for free
    // securities.add(createSecurity("csco", "America/New_York", "USD", SpecialInvestmentInstruments.DIRECT_INVESTMENT));
   
    securities.parallelStream().forEach(security -> {
      try {
        System.out.println(security);
        finnhubConnector.updateSecurityLastPrice(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
     
      assertThat(security.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }
  
  /**
   * Finnhub.io only for Premium
   */
  @Test
  @Disabled
  void getSplitHistoryTest() {
    final List<Security> securities = new ArrayList<>();
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);
    securities.add(createSecurity("NESN.SW", GlobalConstants.STOCK_EX_MIC_SIX, "CHF", SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    securities.parallelStream().forEach(security -> {
      List<Securitysplit> splits = new ArrayList<>();
      try {
        splits = finnhubConnector.getSplitHistory(security, from);
      } catch (Exception e) {

        e.printStackTrace();
      }
      assertThat(splits.size()).isGreaterThanOrEqualTo(1);
    });
  }
  
  
  private Security createSecurity(final String ticker, final String mic, String currency,
      SpecialInvestmentInstruments specialInvestmentInstrument) {
    final Stockexchange stockexchange = new Stockexchange();
    stockexchange.setMic(mic);
    Assetclass assetclass = new Assetclass();
    assetclass.setSpecialInvestmentInstrument(specialInvestmentInstrument);
    final Security security = new Security();
    security.setAssetClass(assetclass);
    security.setStockexchange(stockexchange);
    security.setUrlHistoryExtend(ticker);
    security.setUrlIntraExtend(ticker);
    security.setUrlSplitExtend(ticker);
    security.setCurrency(currency);
    return security;
  }
}
