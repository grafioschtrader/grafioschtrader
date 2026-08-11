package grafioschtrader.connector.instrument.test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafiosch.common.DataHelper;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.coinmarketcap.CoinMarketCapFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("test")
class CoinMarketCapFeedConnectorTest extends BaseFeedConnectorCheck {

  private static final String BTC_CHF_MAPPING = "BTC=1,CHF=2785";
  private static final String BTC_JPY_MAPPING = "BTC=1,JPY=2797";
  private static final String BTC_USD_MAPPING = "BTC=1,USD=2781";
  private static final String ETH_USD_MAPPING = "ETH=1027,USD=2781";

  @Autowired
  private CoinMarketCapFeedConnector coinMarketCapFeedConnector;

  @Test
  void getEodCurrencyHistoryTest() {
    getEodCurrencyHistory();
  }

  @Test
  void updateCurrencyPairLastPriceTest() throws Exception {
    Currencypair currencypair = createCurrencyPair(GlobalConstants.CC_BTC, GlobalConstants.MC_CHF);
    LocalDateTime earliestTimestamp =
        LocalDateTime.now().minusSeconds(coinMarketCapFeedConnector.getIntradayDelayedSeconds());

    coinMarketCapFeedConnector.updateCurrencyPairLastPrice(currencypair);

    LocalDateTime latestTimestamp =
        LocalDateTime.now().minusSeconds(coinMarketCapFeedConnector.getIntradayDelayedSeconds());
    Assertions.assertThat(currencypair.getSLast()).isNotNull().isGreaterThan(0.0);
    Assertions.assertThat(currencypair.getSTimestamp()).isBetween(earliestTimestamp, latestTimestamp);
    assertRounded(currencypair.getSLast(), 2);
    assertRounded(currencypair.getSOpen(), 2);
    assertRounded(currencypair.getSHigh(), 2);
    assertRounded(currencypair.getSLow(), 2);
    assertRounded(currencypair.getSPrevClose(), 2);
  }

  @Test
  void inverseCurrencyPairHistoryTest() throws Exception {
    LocalDate from = LocalDate.of(2020, 1, 15);
    LocalDate to = LocalDate.of(2020, 1, 17);
    Currencypair direct = createCurrencyPair(GlobalConstants.CC_BTC, GlobalConstants.MC_CHF);
    Currencypair inverse = createCurrencyPair(GlobalConstants.MC_CHF, GlobalConstants.CC_BTC);

    List<Historyquote> directQuotes = coinMarketCapFeedConnector.getEodCurrencyHistory(direct, from, to);
    List<Historyquote> inverseQuotes = coinMarketCapFeedConnector.getEodCurrencyHistory(inverse, from, to);

    Assertions.assertThat(inverseQuotes).hasSameSizeAs(directQuotes);
    for (int i = 0; i < directQuotes.size(); i++) {
      Assertions.assertThat(inverseQuotes.get(i).getDate()).isEqualTo(directQuotes.get(i).getDate());
      assertRounded(directQuotes.get(i).getClose(), 2);
      assertRounded(inverseQuotes.get(i).getClose(), 8);
      Assertions.assertThat(inverseQuotes.get(i).getClose()).isCloseTo(1.0 / directQuotes.get(i).getClose(),
          Assertions.within(1e-8));
    }
  }

  @Test
  void quoteCurrencyWithZeroPrecisionTest() throws Exception {
    LocalDate from = LocalDate.of(2020, 1, 15);
    LocalDate to = LocalDate.of(2020, 1, 17);
    Currencypair currencypair = createCurrencyPair(GlobalConstants.CC_BTC, GlobalConstants.MC_JPY, BTC_JPY_MAPPING);

    List<Historyquote> historyquotes = coinMarketCapFeedConnector.getEodCurrencyHistory(currencypair, from, to);

    Assertions.assertThat(historyquotes).isNotEmpty();
    historyquotes.forEach(historyquote -> assertRounded(historyquote.getClose(), 0));
  }

  @Test
  void rejectsMappingForDifferentCurrencyPair() {
    Currencypair currencypair = new Currencypair(GlobalConstants.CC_BTC, GlobalConstants.MC_USD);
    currencypair.setUrlHistoryExtend(BTC_CHF_MAPPING);

    Assertions.assertThatThrownBy(() -> coinMarketCapFeedConnector.checkAndClearSecuritycurrencyUrlExtend(currencypair,
        FeedSupport.FS_HISTORY)).isInstanceOf(GeneralNotTranslatedWithArgumentsException.class);
  }

  @Test
  void rejectsMalformedMapping() {
    Currencypair currencypair = new Currencypair(GlobalConstants.CC_BTC, GlobalConstants.MC_CHF);
    currencypair.setUrlHistoryExtend("BTC=1:CHF=2785");

    Assertions.assertThatThrownBy(() -> coinMarketCapFeedConnector.checkAndClearSecuritycurrencyUrlExtend(currencypair,
        FeedSupport.FS_HISTORY)).isInstanceOf(GeneralNotTranslatedWithArgumentsException.class);
  }

  @Override
  protected List<CurrencyPairHistoricalDate> getHistoricalCurrencies() {
    List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();
    try {
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_CHF, 1098,
          "2020-01-01", "2023-01-02", BTC_CHF_MAPPING));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_CHF, GlobalConstants.CC_BTC, 1098,
          "2020-01-01", "2023-01-02", BTC_CHF_MAPPING));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, 5870,
          "2010-07-13", "2026-08-07", BTC_USD_MAPPING));
      currencies.add(new CurrencyPairHistoricalDate("ETH", GlobalConstants.MC_USD, 3872, "2016-01-01", "2026-08-07",
          ETH_USD_MAPPING));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return currencies;
  }

  private Currencypair createCurrencyPair(String fromCurrency, String toCurrency) {
    return createCurrencyPair(fromCurrency, toCurrency, BTC_CHF_MAPPING);
  }

  private Currencypair createCurrencyPair(String fromCurrency, String toCurrency, String mapping) {
    Currencypair currencypair = new Currencypair(fromCurrency, toCurrency);
    currencypair.setUrlHistoryExtend(mapping);
    currencypair.setUrlIntraExtend(mapping);
    return currencypair;
  }

  private void assertRounded(Double price, int precision) {
    Assertions.assertThat(price).isNotNull().isEqualTo(DataHelper.round(price, precision));
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return coinMarketCapFeedConnector;
  }
}
