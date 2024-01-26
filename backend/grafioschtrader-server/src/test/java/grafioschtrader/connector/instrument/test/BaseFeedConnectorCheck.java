package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHisoricalDate;
import grafioschtrader.entities.Historyquote;

public abstract class BaseFeedConnectorCheck {

  protected abstract IFeedConnector getIFeedConnector();

  protected abstract List<SecurityHisoricalDate> getHistoricalSecurities();


  void updateSecurityLastPrice() {
    final List<SecurityHisoricalDate> hisoricalDate = getHistoricalSecurities();
    hisoricalDate.parallelStream().forEach(hd -> {
      hd.security.setUrlIntraExtend(hd.security.getUrlHistoryExtend());
      hd.security.setUrlHistoryExtend(null);
      try {
        getIFeedConnector().updateSecurityLastPrice(hd.security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(String.format("%s URL: %s last:%f change: %f high: %f low: %f", hd.security.getName(), hd.security.getUrlIntraExtend(),
          hd.security.getSLast(), hd.security.getSChangePercentage(), hd.security.getSHigh(), hd.security.getSLow()));
      assertThat(hd.security.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }


  void getEodSecurityHistory(boolean needSort) {
    final List<SecurityHisoricalDate> hisoricalDate = getHistoricalSecurities();
    hisoricalDate.parallelStream().forEach(hd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = getIFeedConnector().getEodSecurityHistory(hd.security, hd.from, hd.to);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      if(needSort) {
        Collections.sort(historyquotes, Comparator.comparing(Historyquote::getDate));
      }
      assertThat(historyquotes.size()).isEqualTo(hd.expectedRows);
      assertThat(historyquotes.getFirst().getDate()).isEqualTo(hd.from);
      assertThat(historyquotes.getLast().getDate()).isEqualTo(hd.to);
    });
  }
}
