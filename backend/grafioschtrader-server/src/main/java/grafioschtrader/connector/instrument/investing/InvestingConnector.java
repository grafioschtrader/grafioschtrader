package grafioschtrader.connector.instrument.investing;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.common.FieldColumnMapping;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.common.PropertyOnlyCreation;
import grafioschtrader.common.ValueFormatConverter;
import grafioschtrader.connector.IConnectorNames;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * www.investing.com used manly a single ajax-call to load the data.
 *
 */
@Component
public class InvestingConnector extends BaseFeedConnector {

  private static final String URL_EXTENDED_SEPARATOR = ",";
  private static final char DECIMAL_SEPARATOR = '.';
  private static final char THOUSAND_SEPARATOR = ',';
  private static final String URL_ENDPOINT_AJAX = IConnectorNames.DOMAIN_INVESTING + "instruments/HistoricalDataAjax";
  private static final String DATE_FORMAT_FORM = "MM/dd/yyyy";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final int MAX_ROWS_DELIVERD = 5000;
  private static final String URL_HISTORICAL_REGEX = "^[A-Za-z\\-]+\\/[A-Za-z0-9_\\-\\.]+\\,\\d+\\,\\d+$";
  private static final String URL_INTRA_REGEX = "^[A-Za-z\\-]+\\/[A-Za-z0-9_\\-\\.]+$";
  private static final String INVESTING = "investing";

  Map<String, String> cryptoCurrencyMap = Map.of("BTC", "bitcoin", "BNB", "binance-coin", "ETH", "ethereum", "ETC",
      "ethereum-classic", "LTC", "litecoin", "XPR", "xrp");

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
  }

  public InvestingConnector() {
    super(supportedFeed, INVESTING, "Investing.com", null);
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return IConnectorNames.DOMAIN_INVESTING
        + (currencypair.isFromCryptocurrency() ? getCryptoMapping(currencypair) : "currencies") + "/"
        + currencypair.getFromCurrency().toLowerCase() + "-" + currencypair.getToCurrency().toLowerCase();
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws Exception {

    final Connection investingConnection = Jsoup.connect(getCurrencypairIntradayDownloadLink(currencypair));
    final Document doc = investingConnection.timeout(10000).get();
    updateSecuritycurrency(currencypair, doc.select("#last_last").parents().first());
  }

  private String getCryptoMapping(final Currencypair currencypair) {
    return "crypto/" + cryptoCurrencyMap.get(currencypair.getFromCurrency());
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return IConnectorNames.DOMAIN_INVESTING + security.getUrlIntraExtend();
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    final Connection investingConnection = Jsoup.connect(getSecurityIntradayDownloadLink(security));
    final Document doc = investingConnection.timeout(10000).get();
    Element div = doc.select("#last_last").parents().first();
    if (div == null) {
      div = doc.select("div[class^=instrument-price_instrument-price]").first();
    }
    updateSecuritycurrency(security, div);
  }

  private <T extends Securitycurrency<T>> void updateSecuritycurrency(T securitycurrency, Element div) {
    String[] numbers = StringUtils.normalizeSpace(div.text().replace("%", "").replace("(", " ").replace(")", ""))
        .split(" ");
    securitycurrency.setSLast(FeedConnectorHelper.parseDoubleUS(numbers[0]));
    securitycurrency
        .setSOpen(DataHelper.round(securitycurrency.getSLast() - FeedConnectorHelper.parseDoubleUS(numbers[1])));
    securitycurrency.setSChangePercentage(FeedConnectorHelper.parseDoubleUS(numbers[2]));
    securitycurrency.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return getHistoricalLink(security);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(Currencypair currencypair) {
    return getHistoricalLink(currencypair);
  }

  private <T extends Securitycurrency<T>> String getHistoricalLink(final Securitycurrency<T> securitycurreny) {
    return IConnectorNames.DOMAIN_INVESTING + securitycurreny.getUrlHistoryExtend().split(URL_EXTENDED_SEPARATOR)[0];
  }

  @Override
  protected <S extends Securitycurrency<S>> boolean checkAndClearSecuritycurrencyConnector(FeedSupport feedSupport,
      String urlExtend, String errorMsgKey, FeedIdentifier feedIdentifier,
      SpecialInvestmentInstruments specialInvestmentInstruments, AssetclassType assetclassType) {

    boolean clear = super.checkAndClearSecuritycurrencyConnector(feedSupport, urlExtend, errorMsgKey, feedIdentifier,
        specialInvestmentInstruments, assetclassType);
    if (feedIdentifier != null) {
      switch (feedSupport) {
      case HISTORY:
        checkUrlExtendsionWithRegex(new String[] { URL_HISTORICAL_REGEX }, urlExtend);
        break;
      default:
        checkUrlExtendsionWithRegex(new String[] { URL_INTRA_REGEX }, urlExtend);
      }
    }
    return clear;
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws Exception {
    return getEodHistory(currencyPair, from, to);
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    return getEodHistory(security, from, to);
  }

  private <T extends Securitycurrency<T>> List<Historyquote> getEodHistory(final Securitycurrency<T> securitycurreny,
      final Date from, final Date to) throws Exception {
    List<Historyquote> historyquotes = getEodHistoryLimitedRows(securitycurreny, from, to);
    if (historyquotes.size() >= MAX_ROWS_DELIVERD - 1
        && DateHelper.getDateDiff(historyquotes.get(0).getDate(), to, TimeUnit.DAYS) > 2) {
      historyquotes.addAll(getEodHistoryLimitedRows(securitycurreny,
          DateHelper.setTimeToZeroAndAddDay(historyquotes.get(0).getDate(), 1), to));
    }
    return historyquotes;
  }

  private <T extends Securitycurrency<T>> List<Historyquote> getEodHistoryLimitedRows(
      final Securitycurrency<T> securitycurreny, final Date from, final Date to) throws Exception {
    final SimpleDateFormat dateFormatUS = new SimpleDateFormat(DATE_FORMAT_FORM);
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    String[] ids = securitycurreny.getUrlHistoryExtend().split(URL_EXTENDED_SEPARATOR);

    Map<String, String> arguments = new HashMap<>();
    arguments.put("curr_id", ids[1]);
    arguments.put("smlID", ids[2]);
    // arguments.put("header", "SMI Historical Data");
    arguments.put("st_date", dateFormatUS.format(from));
    arguments.put("end_date", dateFormatUS.format(to));
    arguments.put("interval_sec", "Daily");
    arguments.put("sort_col", "date");
    arguments.put("sort_ord", "DESC");
    arguments.put("action", "historical_data");

    StringJoiner sj = new StringJoiner("&");
    for (Map.Entry<String, String> entry : arguments.entrySet())
      sj.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
          + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));

    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(URL_ENDPOINT_AJAX))
        .headers("Content-Type", "application/x-www-form-urlencoded", "x-requested-with", "XMLHttpRequest",
            "User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT)
        .POST(HttpRequest.BodyPublishers.ofString(sj.toString())).build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    List<FieldColumnMapping> fieldColumnMappings = parseTableHeader(Jsoup.parse(response.body()));

    return parseTableContent(Jsoup.parse(response.body()), fieldColumnMappings);
  }

  private List<Historyquote> parseTableContent(Document doc, List<FieldColumnMapping> fieldColumnMappings)
      throws Exception {
    ValueFormatConverter valueFormatConverter = new ValueFormatConverter(DECIMAL_SEPARATOR, THOUSAND_SEPARATOR);
    final List<Historyquote> historyquotes = new ArrayList<>();
    Elements rows = getHtmlTablePart(doc, "tbody", "tr");

    for (int r = 0; r < rows.size(); r++) {
      Element row = rows.get(r);
      Elements cols = row.select("td");
      Historyquote historyquote = new Historyquote();
      for (int c = 0; c < cols.size(); c++) {
        Element col = cols.get(c);
        FieldColumnMapping fcm = fieldColumnMappings.get(c);
        if (fcm.field != null) {
          String value = col.attr("data-real-value");
          valueFormatConverter.convertAndSetValue(historyquote, fcm.field.getName(), value, fcm.field.getType(), true);
        }
      }
      historyquotes.add(historyquote);
    }
    return historyquotes;
  }

  private List<FieldColumnMapping> parseTableHeader(Document doc) {
    List<FieldColumnMapping> fieldColumnMappings = new ArrayList<>();
    List<Field> fields = DataHelper.getFieldByPropertiesAnnotation(Historyquote.class,
        Set.of(PropertyAlwaysUpdatable.class, PropertyOnlyCreation.class));
    Elements headerRows = getHtmlTablePart(doc, "thead", "th");

    for (int i = 0; i < headerRows.size(); i++) {
      final int k = i;
      String propertyName = headerRows.get(i).attr("data-col-name");
      ColumnMapping cm = ColumnMapping.getColumnMapping(propertyName);
      if (cm != null) {
        fields.stream().filter(field -> field.getName().toUpperCase().equals(cm.name())).findFirst()
            .ifPresent(field -> fieldColumnMappings.add(new FieldColumnMapping(k, field)));
      } else {
        fieldColumnMappings.add(new FieldColumnMapping(k, null));
      }
    }
    return fieldColumnMappings;
  }

  private Elements getHtmlTablePart(Document doc, String headBodySelector, String rowSelector) {
    Element table = doc.select("table").get(0);
    Elements tbody = table.select(headBodySelector);
    return tbody.select(rowSelector);
  }

  /**
   * Mappging between histroy quote and html import data.
   * 
   * String literal is the property name of the "data-col-name"</br>
   * Enum is upper case of history quote property</br>
   * 
   * @author Hugo Graf
   *
   */
  enum ColumnMapping {
    DATE("date"), CLOSE("price"), OPEN("open"), VOLUME("vol"), HIGH("high"), LOW("low");

    private String property;

    ColumnMapping(String property) {
      this.property = property;
    }

    public String getProperty() {
      return property;
    }

    public static ColumnMapping getColumnMapping(String propertyName) {
      for (ColumnMapping property : ColumnMapping.values()) {
        if (property.getProperty().equals(propertyName)) {
          return property;
        }
      }
      return null;
    }

  }
}
