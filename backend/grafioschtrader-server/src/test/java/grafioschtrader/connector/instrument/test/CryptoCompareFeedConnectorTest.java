package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.connector.instrument.cryptocompare.CryptoCompareFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
class CryptoCompareFeedConnectorTest {
  
  @Autowired
  private CryptoCompareFeedConnector cryptoCompareFeedConnector;
  
  @Test
  void updateCurrencyPairLastPriceTest() {
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("CHF", "BTC"));
    
    currencies.parallelStream().forEach(currencyPair -> {
      try {
        cryptoCompareFeedConnector.updateCurrencyPairLastPrice(currencyPair);
      } catch (Exception e) {
        e.printStackTrace();
      }
      System.out.println(currencyPair);
      assertThat(currencyPair.getSLast()).isNotNull().isPositive();
    });
    
  }
  
  
  @Test
  void getEodCurrencyHistoryTest() {
    final LocalDate from = LocalDate.parse("2001-01-01");
    final LocalDate to = LocalDate.parse("2021-02-10");
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    
    
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("CHF", "BTC"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("BTC", "CHF"));
    
    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = cryptoCompareFeedConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      System.out.println(historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(2800);
    });
  }

}
