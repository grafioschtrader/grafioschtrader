package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import grafiosch.BaseConstants;
import grafiosch.common.DateHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class ConnectorTestHelper {

  public final static String ISIN_TLT = "IE00BSKRJZ44";
  public final static String ISIN_Nestle = "CH0038863350";
  final static String ISIN_Apple = "US0378331005";
  final static String ISIN_Walmart = "US9311421039";

  final static SecureRandom rnd = new SecureRandom();

  final static SimpleDateFormat sdf = new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT);

  public static void checkHistoryquoteUniqueDate(String name, List<Historyquote> historyquotes) {
    Set<Date> dateSet = new HashSet<>();

    List<Historyquote> historyquotesDuplicateByDate = historyquotes.stream().filter(h -> !dateSet.add(h.getDate()))
        .collect(Collectors.toList());
    assertThat(historyquotesDuplicateByDate.size()).as("Duplicate date entries for %s", name).isEqualTo(0);
  }

  public static Currencypair createCurrencyPair(final String fromCurrency, final String toCurrency) {
    return createCurrencyPair(fromCurrency, toCurrency, null);
  }

  public static Currencypair createCurrencyPair(final String fromCurrency, final String toCurrency,
      final String urlHistoryExtend) {
    final Currencypair currencyPair = new Currencypair();
    currencyPair.setFromCurrency(fromCurrency);
    currencyPair.setToCurrency(toCurrency);
    currencyPair.setUrlHistoryExtend(urlHistoryExtend);
    return currencyPair;
  }

  public static Currencypair createIntraCurrencyPair(final String fromCurrency, final String toCurrency,
      final String urlIntraExtend) {
    return createIntraHistoricalCurrencyPair(fromCurrency, toCurrency, urlIntraExtend, null);
  }

  public static Currencypair createHistoricalCurrencyPair(final String fromCurrency, final String toCurrency,
      final String urlUrlHistoryExtend) {
    return createIntraHistoricalCurrencyPair(fromCurrency, toCurrency, null, urlUrlHistoryExtend);
  }

  private static Currencypair createIntraHistoricalCurrencyPair(final String fromCurrency, final String toCurrency,
      String urlIntraExtend, String urlHistoryExtend) {
    final Currencypair currencyPair = new Currencypair();
    currencyPair.setFromCurrency(fromCurrency);
    currencyPair.setToCurrency(toCurrency);
    currencyPair.setUrlIntraExtend(urlIntraExtend);
    currencyPair.setUrlHistoryExtend(urlHistoryExtend);
    return currencyPair;
  }

  public static Security setAssetclassAndStockexchange(Security security,
      SpecialInvestmentInstruments specialInvestmentInstrument, String mic) {
    return setAssetclassAndStockexchange(security, specialInvestmentInstrument, null, mic);
  }

  public static Security setAssetclassAndStockexchange(Security security,
      SpecialInvestmentInstruments specialInvestmentInstrument, AssetclassType assetclassType, String mic) {
    final Stockexchange stockexchange = new Stockexchange();
    stockexchange.setMic(mic);
    if (mic != null) {
      stockexchange.setCountryCode(BaseFeedConnectorCheck.MIC_TO_COUNTRY_CODE.get(mic));
      stockexchange.setTimeZone(BaseFeedConnectorCheck.micToTimeZoneMap.get(mic));
    }

    Assetclass assetclass = new Assetclass();
    if (specialInvestmentInstrument != null) {
      assetclass.setSpecialInvestmentInstrument(specialInvestmentInstrument);
    }
    if (assetclassType != null) {
      assetclass.setCategoryType(assetclassType);
    }
    security.setAssetClass(assetclass);

    security.setStockexchange(stockexchange);
    return security;
  }

  public static Security createSecurityAndStockexchange(String name, String isin, String tickerSymbol, String mic) {
    Security security = createIntraSecurityWithIsin(name, isin, null);
    security.setTickerSymbol(tickerSymbol);
    final Stockexchange stockexchange = new Stockexchange();
    stockexchange.setMic(mic);
    security.setStockexchange(stockexchange);
    return security;
  }

  public static Security createIntraSecurityWithIsin(final String name, final String isin,
      final String urlIntraExtend) {
    final Security security = new Security();
    security.setName(name);
    security.setIsin(isin);
    security.setUrlIntraExtend(urlIntraExtend);
    return security;
  }

  public static Security createIntraSecurity(final String name, final String urlIntraExtend,
      SpecialInvestmentInstruments specialInvestmentInstrument, String mic, String currency) {
    Security security = setAssetclassAndStockexchange(
        createIntraHistoricalSecurity(name, urlIntraExtend, ExtendKind.INTRA), specialInvestmentInstrument, mic);
    security.setCurrency(currency);
    return security;
  }

  public static Security createIntraSecurity(final String name, final String urlIntraExtend,
      SpecialInvestmentInstruments specialInvestmentInstrument, String mic) {
    return setAssetclassAndStockexchange(createIntraHistoricalSecurity(name, urlIntraExtend, ExtendKind.INTRA),
        specialInvestmentInstrument, mic);
  }

  public static Security createSecurityWithMic(final String name, String isin,
      SpecialInvestmentInstruments specialInvestmentInstrument, String mic) {
    final Security security = new Security();
    security.setName(name);
    security.setIsin(isin);
    return setAssetclassAndStockexchange(security, specialInvestmentInstrument, mic);
  }

//  public static Security createIntraSecurity(final String name, final String urlIntraExtend) {
//    return createIntraHistoricalSecurity(name, urlIntraExtend, ExtendKind.INTRA);
//  }

  public static Security createHistoricalSecurity(final String name, final String urlHistoryExtend,
      SpecialInvestmentInstruments specialInvestmentInstrument, String mic, String currency) {
    Security security = setAssetclassAndStockexchange(
        createIntraHistoricalSecurity(name, urlHistoryExtend, ExtendKind.EOD), specialInvestmentInstrument, mic);
    security.setCurrency(currency);
    return security;
  }

  public static Security createHistoricalSecurity(final String name, final String urlHistoryExtend,
      SpecialInvestmentInstruments specialInvestmentInstrument, String mic) {
    return setAssetclassAndStockexchange(createIntraHistoricalSecurity(name, urlHistoryExtend, ExtendKind.EOD),
        specialInvestmentInstrument, mic);
  }

  public static Security createHistoricalSecurity(final String name, final String urlHistoryExtend) {
    return createIntraHistoricalSecurity(name, urlHistoryExtend, ExtendKind.EOD);
  }

  public static Security createDividendSecurity(final String name, final String urlDividendExtend) {
    return createIntraHistoricalSecurity(name, urlDividendExtend, ExtendKind.DIVIDEND);
  }

  public static Security createSplitSecurity(final String name, final String urlSplitExtend) {
    return createIntraHistoricalSecurity(name, urlSplitExtend, ExtendKind.SPLIT);
  }

  private static Security createIntraHistoricalSecurity(final String name, String urlExtend, ExtendKind extendKind) {
    final Security security = new Security();
    security.setName(name);
    switch (extendKind) {
    case EOD:
      security.setUrlHistoryExtend(urlExtend);
      break;

    case INTRA:
      security.setUrlIntraExtend(urlExtend);
      break;

    case DIVIDEND:
      security.setUrlDividendExtend(urlExtend);
      break;

    case SPLIT:
      security.setUrlSplitExtend(urlExtend);
      break;
    }
    return security;
  }

  public static void standardSplitTest(BaseFeedConnector baseFeedConnector, Map<String, String> symbolMappingMap)
      throws ParseException {
    List<SplitCount> splitCount = new ArrayList<>();
    splitCount.add(
        new SplitCount("Hub Group Inc", getSymbolMapping("HUBG", symbolMappingMap), 3, "2000-01-03", "2024-01-31"));
    splitCount
        .add(new SplitCount("Apple Inc", getSymbolMapping("AAPL", symbolMappingMap), 3, "2000-01-03", "2014-06-09"));
    splitCount
        .add(new SplitCount("Apple Inc", getSymbolMapping("AAPL", symbolMappingMap), 4, "2000-01-03", "2023-06-09"));
    splitCount.add(new SplitCount("NIKE", getSymbolMapping("NKE", symbolMappingMap), 3, "2007-04-03", "2023-06-09"));
    splitCount.add(new SplitCount("NIKE", getSymbolMapping("NKE", symbolMappingMap), 3, "2000-01-03", "2023-06-09"));

    splitCount.parallelStream().forEach(sc -> {
      List<Securitysplit> seucritysplitList = new ArrayList<>();
      try {
        seucritysplitList = baseFeedConnector.getSplitHistory(sc.security, DateHelper.getLocalDate(sc.from),
            DateHelper.getLocalDate(sc.to));
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(seucritysplitList.size()).isEqualTo(sc.expectedRows);
    });
  }

  public static void standardDividendTest(BaseFeedConnector baseFeedConnector, Map<String, String> symbolMappingMap,
      Map<String, Integer> overrideExpectedRowsMap) throws ParseException {
    List<DividendCount> dividendCount = new ArrayList<>();
    dividendCount.add(new DividendCount("Nestlé S.A", ISIN_Nestle, getSymbolMapping("NESN.SW", symbolMappingMap),
        getExpectedRowsMapping(ISIN_Nestle, 22, overrideExpectedRowsMap), "2000-01-03", "2024-03-31",
        GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    dividendCount.add(new DividendCount("Apple Inc", ISIN_Apple, getSymbolMapping("AAPL", symbolMappingMap),
        getExpectedRowsMapping(ISIN_Apple, 47, overrideExpectedRowsMap), "2000-01-03", "2024-03-31", "America/New_York",
        GlobalConstants.MC_USD, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    dividendCount.add(new DividendCount("iShares 20+ Year Treasury Bond ETF", ISIN_TLT,
        getSymbolMapping("TLT", symbolMappingMap), getExpectedRowsMapping(ISIN_TLT, 258, overrideExpectedRowsMap),
        "2000-01-03", "2024-03-31", "America/New_York", GlobalConstants.MC_USD, SpecialInvestmentInstruments.ETF));
    dividendCount.add(new DividendCount("Walmart Inc", ISIN_Walmart, getSymbolMapping("WMT", symbolMappingMap),
        getExpectedRowsMapping(ISIN_Walmart, 97, overrideExpectedRowsMap), "2000-01-03", "2024-03-31",
        "America/New_York", GlobalConstants.MC_USD, SpecialInvestmentInstruments.DIRECT_INVESTMENT));

    dividendCount.parallelStream().forEach(dc -> {
      List<Dividend> dividends = new ArrayList<>();
      try {
        dividends = baseFeedConnector.getDividendHistory(dc.security, DateHelper.getLocalDate(dc.from)).stream()
            .filter(d -> !d.getEventDate().after(dc.to)).collect(Collectors.toList());

      } catch (final Exception e) {
        e.printStackTrace();
      }

      assertThat(dividends.size()).isEqualTo(dc.expectedRows);
    });
  }

  private static String getSymbolMapping(String symbol, Map<String, String> symbolMappingMap) {
    return symbolMappingMap == null ? symbol : symbolMappingMap.getOrDefault(symbol, symbol);

  }

  private static Integer getExpectedRowsMapping(String isin, Integer expectedRows,
      Map<String, Integer> overrideExpectedRowsMap) {
    return overrideExpectedRowsMap == null ? expectedRows : overrideExpectedRowsMap.getOrDefault(isin, expectedRows);

  }

  private static enum ExtendKind {
    EOD, INTRA, DIVIDEND, SPLIT;
  }

  public static class HistoricalDate {
    public int expectedRows;
    public Date from;
    public Date to;

    public HistoricalDate(int expectedRows, String fromStr, String toStr) throws ParseException {
      if (fromStr != null) {
        this.expectedRows = expectedRows;
        this.from = sdf.parse(fromStr);
        this.to = sdf.parse(toStr);
      }
    }

  }

  public static class HistoricalDateLocalDate {
    public int expectedRows;
    public LocalDate from;
    public LocalDate to;

    public HistoricalDateLocalDate(int expectedRows, String fromStr, String toStr) throws ParseException {
      this.expectedRows = expectedRows;
      this.from = LocalDate.parse(fromStr);
      this.to = LocalDate.parse(toStr);
    }

  }

  public static class CurrencyPairHistoricalDate extends HistoricalDate {
    public Currencypair currencypair;

    public CurrencyPairHistoricalDate(final String fromCurrency, final String toCurrency, int expectedRows,
        String fromStr, String toStr) throws ParseException {
      this(fromCurrency, toCurrency, expectedRows, fromStr, toStr, null);
    }

    public CurrencyPairHistoricalDate(final String fromCurrency, final String toCurrency, int expectedRows,
        String fromStr, String toStr, String urlHistoryExtend) throws ParseException {
      super(expectedRows, fromStr, toStr);
      this.currencypair = createCurrencyPair(fromCurrency, toCurrency);
      this.currencypair.setUrlHistoryExtend(urlHistoryExtend);
    }

  }

  public static class SecurityHistoricalDate extends HistoricalDate {

    public Security security;

    public SecurityHistoricalDate(final String name, String urlExtend, int expectedRows, String fromStr, String toStr)
        throws ParseException {
      this(name, "", null, null, urlExtend, null, expectedRows, fromStr, toStr);

    }

    public SecurityHistoricalDate(final String name, int expectedRows, String fromStr, String toStr)
        throws ParseException {
      this(name, "", null, null, expectedRows, fromStr, toStr);
    }

    public SecurityHistoricalDate(final String name, SpecialInvestmentInstruments specialInvestmentInstrument,
        String urlExtend) throws ParseException {
      this(name, null, specialInvestmentInstrument, urlExtend, null, 0, null, null);
    }

    public SecurityHistoricalDate(final String name, SpecialInvestmentInstruments specialInvestmentInstrument,
        String urlExtend, int expectedRows, String fromStr, String toStr) throws ParseException {
      this(name, null, specialInvestmentInstrument, urlExtend, null, expectedRows, fromStr, toStr);
    }

    public SecurityHistoricalDate(final String name, String isin,
        SpecialInvestmentInstruments specialInvestmentInstrument, String mic, int expectedRows, String fromStr,
        String toStr) throws ParseException {
      this(name, isin, specialInvestmentInstrument, null, mic, expectedRows, fromStr, toStr);
    }

    public SecurityHistoricalDate(final String name, String isin,
        SpecialInvestmentInstruments specialInvestmentInstrument, String urlExtend, String mic, int expectedRows,
        String fromStr, String toStr) throws ParseException {
      this(name, isin, specialInvestmentInstrument, null, urlExtend, mic, null, expectedRows, fromStr, toStr);
    }

    public SecurityHistoricalDate(final String name, String isin,
        SpecialInvestmentInstruments specialInvestmentInstrument, AssetclassType assetclassType, String urlExtend,
        String mic, int expectedRows, String fromStr, String toStr) throws ParseException {
      this(name, isin, specialInvestmentInstrument, assetclassType, urlExtend, mic, null, expectedRows, fromStr, toStr);
    }

    public SecurityHistoricalDate(final String name, String isin,
        SpecialInvestmentInstruments specialInvestmentInstrument, AssetclassType assetclassType, String urlExtend,
        String mic, String currency, int expectedRows, String fromStr, String toStr) throws ParseException {
      super(expectedRows, fromStr, toStr);
      security = new Security();
      security.setName(name);
      security.setIsin(isin);
      security.setUrlHistoryExtend(urlExtend);
      security.setCurrency(currency);
      security.setActiveToDate(DateHelper.setTimeToZeroAndAddDay(new Date(), 365));

      setAssetclassAndStockexchange(security, specialInvestmentInstrument, assetclassType, mic);
    }

    public SecurityHistoricalDate(final String name, SpecialInvestmentInstruments specialInvestmentInstrument,
        String urlExtend, String mic, String currency, int expectedRows, String fromStr, String toStr)
        throws ParseException {
      super(expectedRows, fromStr, toStr);
      security = new Security();
      security.setName(name);
      security.setUrlHistoryExtend(urlExtend);
      security.setCurrency(currency);
      security.setActiveToDate(DateHelper.setTimeToZeroAndAddDay(new Date(), 365));

      setAssetclassAndStockexchange(security, specialInvestmentInstrument, mic);
    }

  }

  public static class SplitCount extends DividendSplitCount {
    public SplitCount(String name, String urlSplitExtend, int expectedRows, String fromStr, String toStr)
        throws ParseException {
      super(name, expectedRows, fromStr, toStr);
      security.setUrlSplitExtend(urlSplitExtend);
    }
  }

  public static class DividendCount extends DividendSplitCount {
    public DividendCount(String name, String isin, String urlDividendExtend, int expectedRows, String fromStr,
        String toStr, String mic, String currency, SpecialInvestmentInstruments specialInvestmentInstrument)
        throws ParseException {
      super(name, isin, expectedRows, fromStr, toStr, mic, currency, specialInvestmentInstrument);
      security.setUrlDividendExtend(urlDividendExtend);
    }

  }

  public static abstract class DividendSplitCount extends SecurityHistoricalDate {

    public DividendSplitCount(String name, int expectedRows, String fromStr, String toStr) throws ParseException {
      super(name, expectedRows, fromStr, toStr);
    }

    public DividendSplitCount(String name, String isin, int expectedRows, String fromStr, String toStr, String mic,
        String currency, SpecialInvestmentInstruments specialInvestmentInstrument) throws ParseException {
      super(name, isin, specialInvestmentInstrument, null, mic, currency, expectedRows, fromStr, toStr);
    }

  }

}
