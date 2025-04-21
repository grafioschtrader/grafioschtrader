package grafioschtrader.connector.instrument.finanzench;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import grafiosch.common.DateHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.finanzen.FinanzenHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/*-
 * At one time, historical data could also be queried via this connector.
 * However, access to these pages was better protected against non-browser
 * queries. At present, the source code does not yet fully reflect this
 * situation.
 *
 * A regex pattern check is active. In addition, a check of the URL by the
 * accessibility of the instrument .
 *
 * Security
 * Gberit (CH0030170408)
 * https://www.finanzen.ch/aktien/geberit-aktie/swx
 * https://www.finanzen.ch/Ajax/SharesController_HistoricPriceList/geberit/SWX/18.3.2023_18.4.2025
 * 
 *
 * Bond:
 * Crédit Agricole S.A.SF-Preferred MTN 2021(29) (CH1118460984) 
 * https://www.finanzen.ch/obligationen/cr%C3%A9dit_agricole_sasf-preferred_mtn_202129-obligation-2029-ch1118460984
 * https://www.finanzen.ch/Ajax/BondController_HistoricPriceList/cr%C3%A9dit_agricole_sasf-preferred_mtn_202129-obligation-2029-ch1118460984/DUS/19.3.2025_19.4.2025
 * https://www.finanzen.ch/Ajax/BondController_HistoricPriceList/cr%C3%A9dit_agricole_sasf-preferred_mtn_202129-obligation-2029-ch1118460984/SWX/19.3.2025_19.4.2025
 *
 * Index:
 * SLI (CH0030252883)
 * https://www.finanzen.ch/index/sli
 * https://www.finanzen.ch/Ajax/IndicesController_HistoricPriceList/sli/19.3.2025_19.4.2025
 *
 * ETF:
 * Swisscanto (CH) Gold ETF EA CHF (CH0139101593)
 * https://www.finanzen.ch/etf/swisscanto-ch-gold-etf-ea-ch0139101593/swx
 * https://www.finanzen.ch/Ajax/FundController_HistoricPriceList/swisscanto-ch-gold-etf-ea-ch0139101593/SWX/19.3.2024_19.4.2025
 *
 * Fonds
 * Allianz Global Investors Fund - Allianz Income and Growth AMg2 (H2-CAD) Fonds (LU1597252862)
 * https://www.finanzen.ch/fonds/allianz-global-investors-fund-allianz-income-and-growth-amg2-h2-cad-lu1597252862/sonst
 * https://www.finanzen.ch/Ajax/FundController_HistoricPriceList/allianz-global-investors-fund-allianz-income-and-growth-amg2-h2-cad-lu1597252862/Sonst/19.3.2025_19.4.2025
 *
 * https://www.finanzen.ch/rohstoffe/goldpreis
 *
 * Currency pair:
 * https://www.finanzen.ch/devisen/schweizer_franken-euro-kurs
 * https://www.finanzen.ch/Ajax/ExchangeRateController_HistoricPriceList/schweizer_franken-euro-kurs/19.2.2025_19.4.2025
 *
 *
 * https://www.finanzen.ch/devisen/us_dollar-yen-kurs
 * https://www.finanzen.ch/Ajax/ExchangeRateController_HistoricPriceList/us_dollar-yen-kurs/19.2.2025_19.4.2025
 *
 * Crypto:
 * https://www.finanzen.ch/devisen/bitcoin-franken-kurs
 * https://www.finanzen.ch/Ajax/ExchangeRateController_HistoricPriceList/bitcoin-franken-kurs/19.2.2025_19.4.2025
 *
 */
@Component
public class FinanzenCHFeedConnector extends BaseFeedConnector {

  protected static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final String domain = "https://www.finanzen.ch/";
  private static final String currencyIntraPrefix = "devisen/";
  private static final String HIST_DEVISEN_CONTROLLER = "ExchangeRateController";
  private static final String URL_SECURITY_HISTORICAL_REGEX = "^[^/]+(/[A-Za-z]+)?$";
  private static final Locale FC_LOCALE = Locale.of("de", "CH");

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
    supportedFeed.put(FeedSupport.FS_INTRA,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
  }

  private NumberFormat numberFormat;

  public FinanzenCHFeedConnector() {
    super(supportedFeed, "finanzench", "Finanzen CH", null, EnumSet.of(UrlCheck.INTRADAY));
    numberFormat = NumberFormat.getNumberInstance(FC_LOCALE);
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return domain + FinanzenHelper.getAssetClassIntra(security) + security.getUrlIntraExtend();
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return domain + currencyIntraPrefix + currencypair.getUrlIntraExtend();
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  protected <S extends Securitycurrency<S>> boolean clearAndCheckUrlPatternSecuritycurrencyConnector(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport, String urlExtend, String errorMsgKey,
      FeedIdentifier feedIdentifier, SpecialInvestmentInstruments specialInvestmentInstruments,
      AssetclassType assetclassType) {

    boolean clear = super.clearAndCheckUrlPatternSecuritycurrencyConnector(securitycurrency, feedSupport, urlExtend,
        errorMsgKey, feedIdentifier, specialInvestmentInstruments, assetclassType);
    switch (feedSupport) {
    case FS_HISTORY:
      checkUrlExtendsionWithRegex(new String[] { URL_SECURITY_HISTORICAL_REGEX }, urlExtend);
      break;
    default:
      checkUrlExtendsionWithRegex(new String[] { URL_SECURITY_HISTORICAL_REGEX }, urlExtend);
    }
    return clear;
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencyPair) throws Exception {
    final String url = getCurrencypairIntradayDownloadLink(currencyPair);
    updateLastPrice(currencyPair, url, false);
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    final String url = getSecurityIntradayDownloadLink(security);
    boolean useStockSelector = security.getAssetClass().getCategoryType() == AssetclassType.EQUITIES
        && security.getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT;
    if (security.getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.ETF) {
      updateLstPriceETF(security, url);
    } else {
      updateLastPrice(security, url, useStockSelector);
    }
  }

  /**
   * Reading the data from a sidebar, which is displayed in the form of a table.
   */
  private <S extends Securitycurrency<S>> void updateLastPrice(Securitycurrency<S> securitycurrency, String url,
      boolean useStockSelector) throws Exception {

    final Document doc = getDoc(url);
    final Elements elementPrices = useStockSelector ? doc.select("table.drop-up-enabled td")
        : doc.select("table.pricebox th");

    final Iterator<Element> iter = elementPrices.iterator();
    final double lastPrice = NumberFormat.getNumberInstance(FC_LOCALE).parse(iter.next().text().replace("'", ""))
        .doubleValue();
    final double dailyChange = NumberFormat.getNumberInstance(FC_LOCALE).parse(iter.next().text().replace("'", ""))
        .doubleValue();
    final double changePercentage = NumberFormat.getNumberInstance(FC_LOCALE)
        .parse(iter.next().text().replaceAll("[ %]", "")).doubleValue();

    setLastPrice(securitycurrency, lastPrice, dailyChange, changePercentage);
  }

  /**
   * Reading data from a header line. The display is based on "Div".
   */
  private void updateLstPriceETF(Security security, final String url) throws Exception {
    final Document doc = getDoc(url);
    final Elements elementPrices = doc.select("div.snapshot__values");
    String[] splited = elementPrices.text().split("\\s+");
    final double lastPrice = NumberFormat.getNumberInstance(FC_LOCALE).parse(splited[0].replace("'", "")).doubleValue();
    final double dailyChange = NumberFormat.getNumberInstance(FC_LOCALE).parse(splited[2].replace("'", ""))
        .doubleValue();
    final double changePercentage = NumberFormat.getNumberInstance(FC_LOCALE).parse(splited[4].replaceAll("[ %]", ""))
        .doubleValue();
    setLastPrice(security, lastPrice, dailyChange, changePercentage);
  }

  private Document getDoc(String url) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
    HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
    return Jsoup.parse(response.body());
  }

  private <S extends Securitycurrency<S>> void setLastPrice(Securitycurrency<S> securitycurrency, double lastPrice,
      double dailyChange, double changePercentage) {
    securitycurrency.setSLast(lastPrice);
    securitycurrency.setSPrevClose(lastPrice - dailyChange);
    securitycurrency.setSChangePercentage(changePercentage);
    securitycurrency.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    Date to = DateHelper.setTimeToZeroAndAddDay(new Date(), -1);
    Date from = DateHelper.setTimeToZeroAndAddDay(to, -10);
    return getSecurityHistoricalDownloadLinkByDate(security, from, to);
  }

  private String getSecurityHistoricalDownloadLinkByDate(final Security security, final Date from, final Date to) {
    return getHistoricalDownloadLinkByDate(
        FinanzenHelper.getAjaxController(security) + stripAktie(security.getUrlHistoryExtend()), from, to);
  }

  private String getHistoricalDownloadLinkByDate(final String ajaxContAndExtend, final Date from, final Date to) {
    SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", FC_LOCALE);
    return domain + "Ajax/" + ajaxContAndExtend + "/" + dateFormat.format(from) + "_" + dateFormat.format(to);
  }

  private String stripAktie(String symbol) {
    return symbol.replaceFirst("-aktie(?=/|$)", "");
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    String url = getSecurityHistoricalDownloadLinkByDate(security, from, to);
    return readHistoricalQuotes(url);
  }

  private List<Historyquote> readHistoricalQuotes(String url) throws Exception {
    // System.out.println("URL: " + url);
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(Redirect.NORMAL)
        .build();
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
        .header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        .header("Accept-Language", "en-US,en;q=0.5").version(HttpClient.Version.HTTP_1_1) // Explicitly set HTTP version
        .POST(HttpRequest.BodyPublishers.noBody()).build();
    HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new IOException("Failed to fetch data: " + response.statusCode());
    } else {
      return parseHistoricalData(response);
    }

  }

  private List<Historyquote> parseHistoricalData(HttpResponse<String> response) throws Exception {
    SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", FC_LOCALE);
    List<Historyquote> historyquotes = new ArrayList<>();
    Document doc = Jsoup.parse(response.body());

    // Select the table body containing the historical data
    Elements rows = doc.select("tr");
    int[] colMapping = new int[] { 0, 1, 2, 3, 4, 5 };
    for (int i = 0; i < rows.size(); i++) {
      Element row = rows.get(i);

      if (i == 0) {
        Elements cols = row.select("th");
        colMapping = createMapping(cols);
      } else {

        Elements cols = row.select("td");
        String date = cols.get(0).text();
        if (!date.equals("00:00")) {
          final Historyquote historyquote = new Historyquote();

          historyquote.setDate(dateFormat.parse(date));

          if (colMapping[1] != -1) {
            final Double open = parseDouble(colMapping[1], cols);
            if (open != null) {
              historyquote.setOpen(open);
            }
          }

          final Double close = parseDouble(colMapping[2], cols);
          if (close != null) {
            historyquote.setClose(close);
          } else {
            continue;
          }

          if (colMapping[3] != -1) {
            final Double high = parseDouble(colMapping[3], cols);
            if (high != null) {
              historyquote.setHigh(high);
            }
          }

          if (colMapping[4] != -1) {
            final Double low = parseDouble(colMapping[4], cols);
            if (low != null) {
              historyquote.setLow(low);
            }
          }
          if (colMapping[5] != -1) {
            final Long volume = parseLong(colMapping[5], cols);
            historyquote.setVolume(volume == null ? null : volume);
          }
          historyquotes.add(historyquote);
        }
      }
    }
    return historyquotes;
  }

  private Double parseDouble(final int column, final Elements cols) {
    Number number = parseNumber(column, cols);
    return number == null ? null : number.doubleValue();
  }

  private Long parseLong(final int column, final Elements cols) {
    Number number = parseNumber(column, cols);
    return number == null ? null : number.longValue();
  }

  private Number parseNumber(final int column, final Elements cols) {
    String text = cols.get(column).text().trim().replace("'", "");
    if (!text.isEmpty() && !text.equals("-")) {
      try {
        return numberFormat.parse(text);
      } catch (ParseException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  /**
   * The order and number of columns may vary. Therefore, the mapping to the
   * desired columns takes place with the evaluation of the table header.
   * 
   * @param headerCols
   * @return
   */
  private int[] createMapping(Elements headerCols) {
    int[] mapping = new int[] { -1, -1, -1, -1, -1, -1 };

    for (int i = 0; i < headerCols.size(); i++) {
      String text = headerCols.get(i).text();
      if (text.contains(" ")) {
        text = text.split(" ")[0];
      }
      switch (text) {
      case "Datum":
        mapping[0] = i;
        break;
      case "Eröffnung":
        mapping[1] = i;
        break;
      case "Schluss":
      case "Schlusskurs":
        mapping[2] = i;
        break;
      case "Tageshoch":
        mapping[3] = i;
        break;
      case "Tagestief":
        mapping[4] = i;
        break;
      case "Volumen":
      case "Umsatz":
        mapping[5] = i;
        break;
      }
    }
    return mapping;
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    Date to = DateHelper.setTimeToZeroAndAddDay(new Date(), -1);
    Date from = DateHelper.setTimeToZeroAndAddDay(to, -10);
    return getHistoricaCurrencypairlDownloadLinkByDate(currencypair, from, to);
  }

  private String getHistoricaCurrencypairlDownloadLinkByDate(final Currencypair currencypair, final Date from,
      final Date to) {
    return getHistoricalDownloadLinkByDate(
        HIST_DEVISEN_CONTROLLER + FinanzenHelper.HIST_SUFFIX + currencypair.getUrlHistoryExtend(), from, to);
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencypair, final Date from, final Date to)
      throws Exception {
    String url = getHistoricaCurrencypairlDownloadLinkByDate(currencypair, from, to);
    return readHistoricalQuotes(url);
  }
}
