package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;

public abstract class BaseFeedConnectorCheck {
  private static final Map<String, String> micToTimeZoneMap = new HashMap<>();

  
  static {
    micToTimeZoneMap.put(GlobalConstants.STOCK_EX_MIC_AUSTRALIA, "Australia/Sydney");
    micToTimeZoneMap.put(GlobalConstants.STOCK_EX_MIC_NASDAQ, "America/New_York");
    micToTimeZoneMap.put(GlobalConstants.STOCK_EX_MIC_SIX, "Europe/Zurich");
    micToTimeZoneMap.put(GlobalConstants.STOCK_EX_MIC_UK, "Europe/London");
    micToTimeZoneMap.put(GlobalConstants.STOCK_EX_MIC_FRANCE, "Europe/Paris");
    // Add more if needed
  }
  
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
      System.out.println(String.format("%s URL: %s last:%f change percentage: %f high: %f low: %f timestamp: %tc",
          hd.security.getName(), hd.security.getUrlIntraExtend(), hd.security.getSLast(),
          hd.security.getSChangePercentage(), hd.security.getSHigh(), hd.security.getSLow(),
          hd.security.getSTimestamp()));
      assertThat(hd.security.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  void getEodSecurityHistory(boolean needSort) {
    getEodSecurityHistory(needSort, false);
  }
  
  
  void getEodSecurityHistory(boolean needSort, boolean addTimeZone) {
    final List<SecurityHistoricalDate> hisoricalDate = getHistoricalSecurities();
    if (addTimeZone) {
      applyTimeZonesToSecurities(hisoricalDate);
    }
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

      // Assert size and date range
      assertThat(historyquotes.size()).isEqualTo(hd.expectedRows);
      assertThat(historyquotes.getFirst().getDate()).isEqualTo(hd.from);
      assertThat(historyquotes.getLast().getDate()).isEqualTo(hd.to);

      // Convert java.util.Date to LocalDate and check for weekends
      boolean hasWeekend = historyquotes.stream().map(Historyquote::getDate)
          .map(date -> date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()).map(LocalDate::getDayOfWeek)
          .anyMatch(day -> day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY);

      assertThat(hasWeekend).as("No Historyquote should fall on a Saturday or Sunday").isFalse();
    });
  }

  protected List<CurrencyPairHistoricalDate> getHistoricalCurrencies() {
    return null;
  }

  protected void getEodCurrencyHistory() {
    getEodCurrencyHistory(false);
  }
  
  protected void getEodCurrencyHistory(boolean needSort) {
    final List<CurrencyPairHistoricalDate> currencies = this.getHistoricalCurrencies();
    currencies.parallelStream().forEach(cphd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = getIFeedConnector().getEodCurrencyHistory(cphd.currencypair, cphd.from, cphd.to);
      } catch (Exception e) {
        e.printStackTrace();
      }
      if (needSort) {
        Collections.sort(historyquotes, Comparator.comparing(Historyquote::getDate));
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
        if (cphd.currencypair.getUrlHistoryExtend() != null && cphd.currencypair.getUrlIntraExtend() == null) {
          cphd.currencypair.setUrlIntraExtend(cphd.currencypair.getUrlHistoryExtend());
        }
        getIFeedConnector().updateCurrencyPairLastPrice(cphd.currencypair);
        System.out.println(String.format("%s/%s last:%f change: %f high: %f low: %f",
            cphd.currencypair.getFromCurrency(), cphd.currencypair.getToCurrency(), cphd.currencypair.getSLast(),
            cphd.currencypair.getSChangePercentage(), cphd.currencypair.getSHigh(), cphd.currencypair.getSLow()));
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(cphd.currencypair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  
  private void applyTimeZonesToSecurities(List<SecurityHistoricalDate> historicalDates) {
    for (SecurityHistoricalDate shd : historicalDates) {
      Security security = shd.security;
      if (security != null && security.getStockexchange() != null) {
        Stockexchange stockexchange = security.getStockexchange();
        String micCode = stockexchange.getMic();
        String timeZone = micToTimeZoneMap.get(micCode);
        if (timeZone != null) {
          stockexchange.setTimeZone(timeZone);
        } else {
          System.err.println("No time zone found for MIC: " + micCode);
        }
      }
    }
  }
}
