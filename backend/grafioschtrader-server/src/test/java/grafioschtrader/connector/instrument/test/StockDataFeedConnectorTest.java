package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.connector.instrument.stockdata.StockDataFeedConnector;
import grafioschtrader.connector.instrument.stockdata.StockDataFeedConnector.StockexchangeStockdata;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.StockexchangeMic;
import grafioschtrader.repository.StockexchangeMicJpaRepository;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;

@SpringBootTest(classes = GTforTest.class)
@Transactional
public class StockDataFeedConnectorTest {

  @Autowired
  private StockDataFeedConnector stockdataConnector;

  @Autowired
  private StockexchangeMicJpaRepository stockexchangeMicJpaRepository;

  final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
      .withLocale(Locale.GERMAN);

  @Test
  void getEodSecurityHistoryTest() {

    final List<Security> securities = new ArrayList<>();

    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);
    final LocalDate to = LocalDate.parse("26.01.2022", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    securities.add(ConnectorTestHelper.createHistoricalSecurity("Cisco Systems", "csco",
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "NAS"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("iShares SMIM ETF (CH)", "CSSMIM.SW",
        SpecialInvestmentInstruments.ETF, "SIX"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("ZKB Gold ETF (CHF)", "ZGLD.SW",
        SpecialInvestmentInstruments.ETF, "SIX"));
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
  void getEodCurrencyHistoryTest() {
    final LocalDate from = LocalDate.parse("2000-01-01");
    final LocalDate to = LocalDate.parse("2022-01-28");
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("BTC", "USD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", "NOK"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("JPY", "SEK"));
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
    currencies.add(ConnectorTestHelper.createCurrencyPair("ETH", "USD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("BTC", "USD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "GBP"));
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

  @Test
  @Rollback(false)
  void setTimezoneToStockexchangeMic() {

    try {
      List<StockexchangeMic> stockexchangeMicList = stockexchangeMicJpaRepository.findAll();
      List<StockexchangeStockdata> ssList = stockdataConnector.getAllStockexchanges().data;
      Map<String, StockexchangeStockdata> ssMap = transformToMapRemoveDuplicate(ssList);
      for (StockexchangeMic sm : stockexchangeMicList) {
        StockexchangeStockdata ss = ssMap.get(sm.getMic());
        if (ss != null) {
          sm.setTimeZone(ss.timezone_name);
          sm.setName(ss.stock_exchange_long);
          stockexchangeMicJpaRepository.save(sm);
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private Map<String, StockexchangeStockdata> transformToMapRemoveDuplicate(List<StockexchangeStockdata> ssList) {
    final Map<String, StockexchangeStockdata> mapToReturn = new HashMap<>();
    final Map<String, StockexchangeStockdata> mapDuplicate = new HashMap<>();

    for (StockexchangeStockdata ss : ssList) {
      if (!mapToReturn.containsKey(ss.mic_code)) {
        mapToReturn.put(ss.mic_code, ss);
      } else {
        System.out.println(ss);
        mapDuplicate.put(ss.mic_code, ss);
      }
    }
    return mapToReturn;
  }

}
