package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class ConnectorTestHelper {

  final static SimpleDateFormat sdf = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);

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
    Assetclass assetclass = new Assetclass();
    assetclass.setSpecialInvestmentInstrument(specialInvestmentInstrument);
    if (assetclassType != null) {
      assetclass.setCategoryType(assetclassType);
    }
    security.setAssetClass(assetclass);
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

  public static Security createIntraSecurity(final String name, final String urlIntraExtend) {
    return createIntraHistoricalSecurity(name, urlIntraExtend, ExtendKind.INTRA);
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

  private static enum ExtendKind {
    EOD, INTRA, DIVIDEND, SPLIT;
  }

  public static class HisoricalDate {

    public Security security;
    public int expectedRows;
    public Date from;
    public Date to;

     
    public HisoricalDate(final String name, String isin, SpecialInvestmentInstruments specialInvestmentInstrument,
        String urlExtend, String mic, int expectedRows, String fromStr, String toStr) throws ParseException {
      this(name, isin, specialInvestmentInstrument, null, urlExtend, mic, expectedRows, fromStr, toStr);
    }

    public HisoricalDate(final String name, String isin, SpecialInvestmentInstruments specialInvestmentInstrument,
        AssetclassType assetclassType, String urlExtend, String mic, int expectedRows, String fromStr, String toStr)
        throws ParseException {
      security = new Security();
      security.setName(name);
      security.setIsin(isin);
      security.setUrlHistoryExtend(urlExtend);
      this.expectedRows = expectedRows;
      this.from = sdf.parse(fromStr);
      this.to = sdf.parse(toStr);
      setAssetclassAndStockexchange(security, specialInvestmentInstrument, assetclassType, mic);
    }

  }

}
