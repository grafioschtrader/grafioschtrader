package grafioschtrader.connector.instrument.test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;

import grafiosch.common.DateHelper;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;

public abstract class BaseFeedConnectorCheck {
  public static final Map<String, String> micToTimeZoneMap = new HashMap<>();

  static {

    micToTimeZoneMap.put(GlobalConstants.STOCK_EX_MIC_AUSTRALIA, "Australia/Sydney");
    micToTimeZoneMap.put(GlobalConstants.STOCK_EX_MIC_NASDAQ, "America/New_York");
    micToTimeZoneMap.put(GlobalConstants.STOCK_EX_MIC_SIX, "Europe/Zurich");
    micToTimeZoneMap.put(GlobalConstants.STOCK_EX_MIC_UK, "Europe/London");
    micToTimeZoneMap.put(GlobalConstants.STOCK_EX_MIC_FRANCE, "Europe/Paris");
    micToTimeZoneMap.put(GlobalConstants.STOCK_EX_MIC_WARSAW, "Europe/Warsaw");
    // Add more if needed
  }

  public static final Map<String, String> MIC_TO_COUNTRY_CODE;

  static {
    Map<String, String> map = new HashMap<>();
    map.put(GlobalConstants.STOCK_EX_MIC_UK, "GB");
    map.put(GlobalConstants.STOCK_EX_MIC_NASDAQ, "US");
    map.put(GlobalConstants.STOCK_EX_MIC_NYSE, "US");
    map.put(GlobalConstants.STOCK_EX_MIC_SIX, "CH");
    map.put(GlobalConstants.STOCK_EX_MIC_XETRA, "DE");
    map.put(GlobalConstants.STOCK_EX_MIC_FRANKFURT, "DE");
    map.put(GlobalConstants.STOCK_EX_MIC_SPAIN, "ES");
    map.put(GlobalConstants.STOCK_EX_MIC_ITALY, "IT");
    map.put(GlobalConstants.STOCK_EX_MIC_JAPAN, "JP");
    map.put(GlobalConstants.STOCK_EX_MIC_AUSTRIA, "AT");
    map.put(GlobalConstants.STOCK_EX_MIC_FRANCE, "FR");
    map.put(GlobalConstants.STOCK_EX_MIC_AUSTRALIA, "AU");
    map.put(GlobalConstants.STOCK_EX_MIC_ZKB, "CH");
    map.put(GlobalConstants.STOCK_EX_MIC_STUTTGART, "DE");
    map.put(GlobalConstants.STOCK_EX_MIC_WARSAW, "PL");
    MIC_TO_COUNTRY_CODE = Collections.unmodifiableMap(map);
  }

  protected abstract IFeedConnector getIFeedConnector();

  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    return null;
  }

  void updateSecurityLastPriceByHistoricalData() {
    List<Security> securities = getHistoricalSecurities().stream().map(hd -> hd.security).collect(Collectors.toList());
    updateSecurityLastPrice(securities);
  }

  void updateSecurityLastPrice(List<Security> securties) {
    securties.forEach(security -> {
      if (security.getUrlHistoryExtend() != null) {
        security.setUrlIntraExtend(security.getUrlHistoryExtend());
        security.setUrlHistoryExtend(null);
      }
      if (security.getActiveToDate() == null) {
        security.setActiveToDate(DateHelper.setTimeToZeroAndAddDay(new Date(), 365));
      }
      checkRegularPattern(security, FeedSupport.FS_INTRA);
      try {
        getIFeedConnector().updateSecurityLastPrice(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(String.format("%s URL: %s last:%f change percentage: %f high: %f low: %f timestamp: %tc",
          security.getName(), security.getUrlIntraExtend(), security.getSLast(), security.getSChangePercentage(),
          security.getSHigh(), security.getSLow(), security.getSTimestamp()));
      Assertions.assertThat(security.getSLast()).as("Last price for " + security.getName()).isNotNull()
          .isGreaterThan(0.0);
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

    final ZoneId zoneId = ZoneId.systemDefault(); // Time zone for date comparison

    hisoricalDate.parallelStream().forEach(hd -> {
      checkRegularPattern(hd.security, FeedSupport.FS_HISTORY);

      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = getIFeedConnector().getEodSecurityHistory(hd.security, hd.from, hd.to);
      } catch (final Exception e) {
        System.err
            .println("Error retrieving history quotes for Security " + hd.security.getName() + ": " + e.getMessage());
        e.printStackTrace();
      }

      Assertions.assertThat(historyquotes)
          .as("History quotes should not be null or empty for Security " + hd.security.getName()).isNotEmpty();

      if (needSort) {
        Collections.sort(historyquotes, Comparator.comparing(Historyquote::getDate));
      }

      // Convert dates for comparison (date part only)
      LocalDate firstQuoteDate = Instant.ofEpochMilli(historyquotes.getFirst().getDate().getTime()).atZone(zoneId)
          .toLocalDate();
      LocalDate hdFromDate = Instant.ofEpochMilli(hd.from.getTime()).atZone(zoneId).toLocalDate();
      LocalDate lastQuoteDate = Instant.ofEpochMilli(historyquotes.getLast().getDate().getTime()).atZone(zoneId)
          .toLocalDate();
      LocalDate hdToDate = Instant.ofEpochMilli(hd.to.getTime()).atZone(zoneId).toLocalDate();

      // Assert size and date range (only date part)
      Assertions.assertThat(historyquotes.size()).as("Number of history quotes for Security " + hd.security.getName())
          .isEqualTo(hd.expectedRows);
      Assertions.assertThat(firstQuoteDate).as("Start date of the first quote for Security " + hd.security.getName())
          .isEqualTo(hdFromDate);
      Assertions.assertThat(lastQuoteDate).as("End date of the last quote for Security " + hd.security.getName())
          .isEqualTo(hdToDate);

      // Check for weekends
      boolean hasWeekend = historyquotes.stream().map(Historyquote::getDate)
          .map(date -> date.toInstant().atZone(zoneId).toLocalDate()).map(LocalDate::getDayOfWeek)
          .anyMatch(day -> day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY);

      Assertions.assertThat(hasWeekend)
          .as("No Historyquote should fall on a Saturday or Sunday for Security " + hd.security.getName()).isFalse();
    });
  }

  void checkRegularPattern(Security security, FeedSupport feedSupport) {
    try {
      getIFeedConnector().checkAndClearSecuritycurrencyUrlExtend(security, feedSupport);
    } catch (GeneralNotTranslatedWithArgumentsException e) {
      throw new AssertionError("URL-Extension does not match for this security " + security.getName(), e);
    }
  }

  protected List<CurrencyPairHistoricalDate> getHistoricalCurrencies() {
    return null;
  }

  protected void getEodCurrencyHistory() {
    getEodCurrencyHistory(false);
  }

  protected void getEodCurrencyHistory(boolean needSort) {
    final List<CurrencyPairHistoricalDate> currencies = this.getHistoricalCurrencies();

    final ZoneId zoneId = ZoneId.systemDefault(); // Time zone for date comparison

    currencies.parallelStream().forEach(cphd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = getIFeedConnector().getEodCurrencyHistory(cphd.currencypair, cphd.from, cphd.to);
      } catch (Exception e) {
        System.err.println(
            "Error retrieving history quotes for CurrencyPair " + cphd.currencypair.getName() + ": " + e.getMessage());
        e.printStackTrace();
      }

      Assertions.assertThat(historyquotes)
          .as("History quotes should not be null or empty for CurrencyPair " + cphd.currencypair.getName())
          .isNotEmpty();

      if (needSort) {
        Collections.sort(historyquotes, Comparator.comparing(Historyquote::getDate));
      }

      // Convert dates for comparison (date part only)
      LocalDate firstQuoteDate = Instant.ofEpochMilli(historyquotes.get(0).getDate().getTime()).atZone(zoneId)
          .toLocalDate();
      LocalDate cphdFromDate = Instant.ofEpochMilli(cphd.from.getTime()).atZone(zoneId).toLocalDate();
      LocalDate lastQuoteDate = Instant.ofEpochMilli(historyquotes.get(historyquotes.size() - 1).getDate().getTime())
          .atZone(zoneId).toLocalDate();
      LocalDate cphdToDate = Instant.ofEpochMilli(cphd.to.getTime()).atZone(zoneId).toLocalDate();

      // Assert size and date range (only date part)
      Assertions.assertThat(historyquotes.size())
          .as("Number of history quotes for CurrencyPair " + cphd.currencypair.getName()).isEqualTo(cphd.expectedRows);
      Assertions.assertThat(firstQuoteDate)
          .as("Start date of the first quote for CurrencyPair " + cphd.currencypair.getName()).isEqualTo(cphdFromDate);
      Assertions.assertThat(lastQuoteDate)
          .as("End date of the last quote for CurrencyPair " + cphd.currencypair.getName()).isEqualTo(cphdToDate);

      ConnectorTestHelper.checkHistoryquoteUniqueDate(cphd.currencypair.getName(), historyquotes);
    });
  }

  protected void updateCurrencyPairLastPrice() {
    List<Currencypair> currencypairs = getHistoricalCurrencies().stream().map(hd -> hd.currencypair)
        .collect(Collectors.toList());
    updateCurrencyPairLastPrice(currencypairs);
  }

  protected void updateCurrencyPairLastPrice(List<Currencypair> currencypairs) {
    currencypairs.parallelStream().forEach(currencypair -> {
      try {
        if (currencypair.getUrlHistoryExtend() != null && currencypair.getUrlIntraExtend() == null) {
          currencypair.setUrlIntraExtend(currencypair.getUrlHistoryExtend());
        }
        getIFeedConnector().updateCurrencyPairLastPrice(currencypair);
        System.out.println(String.format("%s/%s last:%f change: %f high: %f low: %f", currencypair.getFromCurrency(),
            currencypair.getToCurrency(), currencypair.getSLast(), currencypair.getSChangePercentage(),
            currencypair.getSHigh(), currencypair.getSLow()));
      } catch (final Exception e) {
        e.printStackTrace();
      }
      Assertions.assertThat(currencypair.getSLast()).as("Last price for " + currencypair.getName()).isNotNull()
          .isGreaterThan(0.0);
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