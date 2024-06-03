package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.stockdata.StockDataFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.StockexchangeMicJpaRepository;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.SubscriptionType;

@SpringBootTest(classes = GTforTest.class)
@Transactional
public class StockDataFeedConnectorTest {

  private final StockDataFeedConnector stockdataConnector;

  @Autowired
  private StockexchangeMicJpaRepository stockexchangeMicJpaRepository;


  @Autowired
  public StockDataFeedConnectorTest(StockDataFeedConnector stockdataConnector) {
    this.stockdataConnector = stockdataConnector;
    assumeTrue(stockdataConnector.isActivated());
  }

  final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
      .withLocale(Locale.GERMAN);



  @Test
  // Attention: Needs a subscription for Standard or more.
  void getEodSecurityHistoryTest() {

    if (stockdataConnector.getSubscriptionType() != SubscriptionType.STOCK_DATA_ORG_FREE) {
      final List<Security> securities = new ArrayList<>();

      final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);
      final LocalDate to = LocalDate.parse("26.01.2022", germanFormatter);

      final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
      final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
      securities.add(ConnectorTestHelper.createHistoricalSecurity("Cisco Systems", "csco",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, "NAS"));
      securities.add(ConnectorTestHelper.createHistoricalSecurity("iShares SMIM ETF (CH)", "CSSMIM.SW",
          SpecialInvestmentInstruments.ETF, GlobalConstants.STOCK_EX_MIC_SIX));
      securities.add(ConnectorTestHelper.createHistoricalSecurity("ZKB Gold ETF (CHF)", "ZGLD.SW",
          SpecialInvestmentInstruments.ETF, GlobalConstants.STOCK_EX_MIC_SIX));

      securities.parallelStream().forEach(security -> {
        List<Historyquote> historyquote = new ArrayList<>();
        try {
          historyquote = stockdataConnector.getEodSecurityHistory(security, fromDate, toDate);
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
    securities.add(ConnectorTestHelper.createIntraSecurity("Cisco Systems", "csco",
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "NAS"));
    securities.parallelStream().forEach(security -> {
      try {
        stockdataConnector.updateSecurityLastPrice(security);
        System.out.println(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(security.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  @Test
  // Attention: Needs a subscription for Standard or more, but now now...
  void getEodCurrencyHistoryTest() {
    final LocalDate from = LocalDate.parse("2000-01-01");
    final LocalDate to = LocalDate.parse("2022-01-28");
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.CC_BTC, GlobalConstants.MC_USD));
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", "NOK"));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_JPY, "SEK"));
    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = stockdataConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      System.out.println(historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(3400);
    });
  }

  @Test
  void updateCurrencyPairLastPriceTest() {
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("ETH", GlobalConstants.MC_USD));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.CC_BTC, GlobalConstants.MC_USD));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_GBP));
    currencies.parallelStream().forEach(currencyPair -> {
      try {
        stockdataConnector.updateCurrencyPairLastPrice(currencyPair);
        System.out.println(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }


}
