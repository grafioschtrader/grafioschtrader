package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.entities.Historyquote;

public abstract class BaseFeedConnectorCheck {

  protected abstract IFeedConnector getIFeedConnector();

  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    return null;
  }

  void updateSecurityLastPrice() {
    final List<SecurityHistoricalDate> hisoricalDate = getHistoricalSecurities();
    hisoricalDate.parallelStream().forEach(hd -> {
      hd.security.setUrlIntraExtend(hd.security.getUrlHistoryExtend());
      hd.security.setUrlHistoryExtend(null);
      try {
        getIFeedConnector().updateSecurityLastPrice(hd.security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(String.format("%s URL: %s last:%f change percentage: %f high: %f low: %f timestamp: %tc", hd.security.getName(),
          hd.security.getUrlIntraExtend(), hd.security.getSLast(), hd.security.getSChangePercentage(),
          hd.security.getSHigh(), hd.security.getSLow(), hd.security.getSTimestamp()));
      assertThat(hd.security.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  void getEodSecurityHistory(boolean needSort) {
    final List<SecurityHistoricalDate> hisoricalDate = getHistoricalSecurities();
    hisoricalDate.parallelStream().forEach(hd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = getIFeedConnector().getEodSecurityHistory(hd.security, hd.from, hd.to);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      if (needSort) {
        Collections.sort(historyquotes, Comparator.comparing(Historyquote::getDate));
      }
      assertThat(historyquotes.size()).isEqualTo(hd.expectedRows);
      assertThat(historyquotes.getFirst().getDate()).isEqualTo(hd.from);
      assertThat(historyquotes.getLast().getDate()).isEqualTo(hd.to);
    });
  }

  protected List<CurrencyPairHistoricalDate> getHistoricalCurrencies() {
    return null;
  }

  protected void getEodCurrencyHistory() {
    final List<CurrencyPairHistoricalDate> currencies = this.getHistoricalCurrencies();
    currencies.parallelStream().forEach(cphd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = getIFeedConnector().getEodCurrencyHistory(cphd.currencypair, cphd.from, cphd.to);
      } catch (Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquotes.size()).isEqualTo(cphd.expectedRows);
      assertThat(historyquotes.get(0).getDate()).isEqualTo(cphd.from);
      assertThat(historyquotes.get(historyquotes.size() - 1).getDate()).isEqualTo(cphd.to);
      ConnectorTestHelper.checkHistoryquoteUniqueDate(cphd.currencypair.getName(), historyquotes);
    });
  }

  protected void updateCurrencyPairLastPrice() {
    getHistoricalCurrencies().parallelStream().forEach(cphd -> {
      try {
        getIFeedConnector().updateCurrencyPairLastPrice(cphd.currencypair);
        System.out.println(String.format("%s/%s last:%f change: %f high: %f low: %f", cphd.currencypair.getFromCurrency(),
            cphd.currencypair.getToCurrency(), cphd.currencypair.getSLast(), cphd.currencypair.getSChangePercentage(),
            cphd.currencypair.getSHigh(), cphd.currencypair.getSLow()));
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(cphd.currencypair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

}
