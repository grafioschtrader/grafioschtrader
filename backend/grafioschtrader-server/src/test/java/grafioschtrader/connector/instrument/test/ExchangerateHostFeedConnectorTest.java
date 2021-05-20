package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.exchangeratehost.ExchangerateHostFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;


class ExchangerateHostFeedConnectorTest {
  
  
  @Test
  void getEodCurrencyHistoryTest() {
    
    ExchangerateHostFeedConnector exchangerateHostFeedConnector = new ExchangerateHostFeedConnector();
    
    final LocalDate from = LocalDate.parse("2000-01-01");
    final LocalDate to = LocalDate.parse("2021-02-12");
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
  
    
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", "NOK"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("JPY", "SEK"));

    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = exchangerateHostFeedConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      System.out.println(historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(7600);
    });
  }
  
  
  @Test
  void updateCurrencyPairLastPriceTest() {
    ExchangerateHostFeedConnector exchangerateHostFeedConnector = new ExchangerateHostFeedConnector();
    
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("ETH", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "GBP"));
    currencies.parallelStream().forEach(currencyPair -> {
      try {
        exchangerateHostFeedConnector.updateCurrencyPairLastPrice(currencyPair);
      } catch (final IOException e) {
        e.printStackTrace();
      }
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }
}
