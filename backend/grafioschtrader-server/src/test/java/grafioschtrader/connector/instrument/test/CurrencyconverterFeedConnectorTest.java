package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import grafioschtrader.connector.instrument.currencyconverter.CurrencyconverterFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.test.start.GTforTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class)
class CurrencyconverterFeedConnectorTest {

  @Autowired
  private CurrencyconverterFeedConnector currencyconverterFeedConnector;

  @Test
  void updateCurrencyPairLastPriceTest() {
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("BTC", "USD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "GBP"));
    currencies.parallelStream().forEach(currencyPair -> {
      try {
        currencyconverterFeedConnector.updateCurrencyPairLastPrice(currencyPair);
      } catch (final IOException e) {
        e.printStackTrace();
      }
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

}
