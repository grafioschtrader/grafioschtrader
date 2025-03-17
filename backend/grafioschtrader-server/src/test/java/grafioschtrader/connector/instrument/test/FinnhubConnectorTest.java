package grafioschtrader.connector.instrument.test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.finnhub.FinnhubConnector;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.SubscriptionType;

@SpringBootTest(classes = GTforTest.class)
class FinnhubConnectorTest {

  private final FinnhubConnector finnhubConnector;

  @Autowired
  public FinnhubConnectorTest(FinnhubConnector finnhubConnector) {
    this.finnhubConnector = finnhubConnector;
    assumeTrue(finnhubConnector.isActivated());
  }

  final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
      .withLocale(Locale.GERMAN);

  @Test
  void getEodSecurityHistoryTest() {
    if (finnhubConnector.getSubscriptionType() != SubscriptionType.FINNHUB_FREE) {
      final List<Security> securities = new ArrayList<>();

      final LocalDate from = LocalDate.parse("03.01.2010", germanFormatter);
      final LocalDate to = LocalDate.parse("26.03.2020", germanFormatter);

      final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
      final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

      // Indices may not be supported anymore
      // securities.add(createSecurity("^NDX", GlobalConstants.STOCK_EX_MIC_NYSE,
      // GlobalConstants.MC_USD,
      // SpecialInvestmentInstruments.NON_INVESTABLE_INDICES));
      // securities.add(createSecurity("^GSPC", GlobalConstants.STOCK_EX_MIC_NYSE,
      // GlobalConstants.MC_USD,
      // SpecialInvestmentInstruments.NON_INVESTABLE_INDICES));

      securities.add(createSecurity("csco", GlobalConstants.STOCK_EX_MIC_NYSE, GlobalConstants.MC_USD,
          SpecialInvestmentInstruments.DIRECT_INVESTMENT));
      // securities.add(createSecurity("XSGL.MI", "MTA", GlobalConstants.MC_EUR,
      // SpecialInvestmentInstruments.ETF));
      // securities.add(createSecurity("NESN.SW", GlobalConstants.STOCK_EX_MIC_SIX,
      // GlobalConstants.MC_CHF, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
      // securities.add(createSecurity("XSGL.MI", "MTA", GlobalConstants.MC_EUR,
      // SpecialInvestmentInstruments.ETF));
      // securities.add(createSecurity("CAC.PA", "Euronext", GlobalConstants.MC_EUR,
      // SpecialInvestmentInstruments.ETF));
      // securities.add(createSecurity("OILUSA.SW", GlobalConstants.STOCK_EX_MIC_SIX,
      // GlobalConstants.MC_USD, SpecialInvestmentInstruments.ETF));

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
  }

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();

    // Indices may not be supported anymore
    // securities.add(createSecurity("^GSPC", GlobalConstants.STOCK_EX_MIC_NYSE,
    // GlobalConstants.MC_USD,
    // SpecialInvestmentInstruments.NON_INVESTABLE_INDICES));

    // Not supporting for free
    // securities.add(createSecurity("LYHLT.SW", GlobalConstants.STOCK_EX_MIC_SIX,
    // GlobalConstants.MC_EUR, SpecialInvestmentInstruments.ETF));
    // securities.add(createSecurity("NESN.SW", GlobalConstants.STOCK_EX_MIC_SIX,
    // GlobalConstants.MC_CHF, SpecialInvestmentInstruments.DIRECT_INVESTMENT));

    // Only US Market for free
    securities.add(createSecurity("csco", "America/New_York", GlobalConstants.MC_USD,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    securities.add(createSecurity("SPY", "America/New_York", GlobalConstants.MC_USD, SpecialInvestmentInstruments.ETF));

    // securities.add(createSecurity("^NDX", GlobalConstants.STOCK_EX_MIC_NYSE,
    // GlobalConstants.MC_USD,
    // SpecialInvestmentInstruments.NON_INVESTABLE_INDICES));

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
  void getSplitsTest() throws ParseException {
    if (finnhubConnector.getSubscriptionType() != SubscriptionType.FINNHUB_FREE) {
      ConnectorTestHelper.standardSplitTest(finnhubConnector, null);
    }
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
