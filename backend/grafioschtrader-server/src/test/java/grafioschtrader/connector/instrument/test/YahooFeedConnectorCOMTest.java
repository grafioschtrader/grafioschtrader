package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.yahoo.YahooFeedConnectorCOM;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.types.SpecialInvestmentInstruments;

class YahooFeedConnectorCOMTest {

  private YahooFeedConnectorCOM yahooFeedConnector = new YahooFeedConnectorCOM();
  
  @Test
  void getEodCurrencyHistoryTest() {
   
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);

    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("BTC", "USD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("EUR", "CHF"));
    final LocalDate from = LocalDate.parse("08.08.2019", germanFormatter);
    final LocalDate to = LocalDate.parse("15.01.2021", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.of(GlobalConstants.TIME_ZONE)).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.of(GlobalConstants.TIME_ZONE)).toInstant());

    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = yahooFeedConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquotes.size()).isGreaterThan(350);
      assertTrue(DateHelper.isSameDay(historyquotes.get(0).getDate(), fromDate));
      assertTrue(DateHelper.isSameDay(historyquotes.get(historyquotes.size() - 1).getDate(), toDate));

    });

  }

  @Test
  void updateCurrencyPairLastPriceTest() {

    final List<Currencypair> currencies = new ArrayList<>();

    currencies.add(ConnectorTestHelper.createCurrencyPair("EUR", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("BTC", "USD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("CHF", "AUD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "EUR"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("GBP", "EUR"));

    currencies.parallelStream().forEach(currencyPair -> {
      try {
        yahooFeedConnector.updateCurrencyPairLastPrice(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  @Test
  void getEodSecurityHistoryTest() {
    final List<Security> securities = new ArrayList<>();

    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("04.08.2016", germanFormatter);
    final LocalDate to = LocalDate.parse("15.01.2021", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.of(GlobalConstants.TIME_ZONE)).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.of(GlobalConstants.TIME_ZONE)).toInstant());

    securities.add(createSecurity("csco", "NYSE", "USD", SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    securities.add(createSecurity("^NDX", "NYSE", "USD", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES));
    securities.add(createSecurity("NESN.SW", "SIX", "CHF", SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    securities.add(createSecurity("CAC.PA", "Euronext", "EUR", SpecialInvestmentInstruments.ETF));
    securities.add(createSecurity("OILUSA.SW", "SIX", "USD", SpecialInvestmentInstruments.ETF));
    securities.add(createSecurity("AMZN", "NYSE", "USD", SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    
    
    securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = yahooFeedConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println("Ticker=" + security.getUrlHistoryExtend() + " Historyquote-Size=" + historyquotes.size());
      assertThat(historyquotes.size()).isGreaterThan(1100);
      assertTrue(DateHelper.isSameDay(historyquotes.get(0).getDate(), fromDate));
      assertTrue(DateHelper.isSameDay(historyquotes.get(historyquotes.size() - 1).getDate(), toDate));
    });
  }

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();
    securities.add(createSecurity("NESN.SW", "SIX", "CHF", SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    securities.add(createSecurity("csco", "America/New_York", "USD", SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    securities.parallelStream().forEach(security -> {
      try {
        yahooFeedConnector.updateSecurityLastPrice(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(security.getSLast()).isNotNull().isGreaterThan(0.0);
    });

  }

  @Test
  void getSplitsTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);

    final List<Security> securities = new ArrayList<>();
    securities.add(createSecurity("NESN.SW", "SIX", "CHF", SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    securities.add(createSecurity("csco", "America/New_York", "USD", SpecialInvestmentInstruments.DIRECT_INVESTMENT));

    securities.parallelStream().forEach(security -> {
      List<Securitysplit> seucritysplitList = new ArrayList<>();
      try {
        seucritysplitList = yahooFeedConnector.getSplitHistory(security, from);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      seucritysplitList.forEach(System.out::println);
      assertThat(seucritysplitList.size()).isGreaterThanOrEqualTo(1);
    });

  }

  @Test
  void getDividendHistoryTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);

    final List<Security> securities = new ArrayList<>();
    securities.add(createSecurity("NESN.SW", "SIX", "CHF", SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    securities.add(createSecurity("AAPL", "America/New_York", "USD", SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    securities.add(createSecurity("TLT", "America/New_York", "USD", SpecialInvestmentInstruments.ETF));

    securities.parallelStream().forEach(security -> {
      List<Dividend> dividens = new ArrayList<>();
      try {
        dividens = yahooFeedConnector.getDividendHistory(security, from);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(dividens.size()).isGreaterThanOrEqualTo(1);
    });
  }

  private Security createSecurity(final String ticker, final String mic, String currency,
      SpecialInvestmentInstruments specialInvestmentInstrument) {
    final Stockexchange stockexchange = new Stockexchange();
    stockexchange.setMic(mic);
    Assetclass assetclass = new Assetclass();
    assetclass.setSpecialInvestmentInstrument(specialInvestmentInstrument);
    final Security security = new Security();
    security.setAssetClass(assetclass);
    security.setStockexchange(stockexchange);
    security.setUrlHistoryExtend(ticker);
    security.setUrlIntraExtend(ticker);
    security.setUrlSplitExtend(ticker);
    security.setUrlDividendExtend(ticker);
    security.setCurrency(currency);
    return security;
  }

}
