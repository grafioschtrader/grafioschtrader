package grafioschtrader.connector.instrument.investing;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.IConnectorNames;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

@Component
public class InvestingConnector extends BaseFeedConnector {

  private static final int MAX_ROWS_DELIVERD = 5000;
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final String INVESTING = "investing";
  private static final String INVESTING_DOMAIN_HISTORYICAL = "https://tvc4.investing.com/";
  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String URL_HISTORICAL_REGEX = "^\\d+$";
  private static final String URL_INTRA_REGEX = "^[A-Za-z\\-]+\\/[A-Za-z0-9_\\(\\)\\-\\.]+$";

  Map<String, String> cryptoCurrencyMap = Map.of("BTC", "bitcoin", "BNB", "binance-coin", "ETH", "ethereum", "ETC",
      "ethereum-classic", "LTC", "litecoin", "XPR", "xrp");

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
  }

  public InvestingConnector() {
    super(supportedFeed, INVESTING, "Investing.com", null);
  }

  @Override
  protected <S extends Securitycurrency<S>> boolean checkAndClearSecuritycurrencyConnector(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport, String urlExtend, String errorMsgKey,
      FeedIdentifier feedIdentifier, SpecialInvestmentInstruments specialInvestmentInstruments,
      AssetclassType assetclassType) {

    boolean clear = super.checkAndClearSecuritycurrencyConnector(securitycurrency, feedSupport, urlExtend, errorMsgKey,
        feedIdentifier, specialInvestmentInstruments, assetclassType);
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
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return IConnectorNames.DOMAIN_INVESTING
        + (currencypair.isFromCryptocurrency() ? getCryptoMapping(currencypair) : "currencies") + "/"
        + currencypair.getFromCurrency().toLowerCase() + "-" + currencypair.getToCurrency().toLowerCase();
  }

  private String getCryptoMapping(final Currencypair currencypair) {
    return "crypto/" + cryptoCurrencyMap.get(currencypair.getFromCurrency());
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws Exception {
    final Connection investingConnection = Jsoup.connect(getCurrencypairIntradayDownloadLink(currencypair));
    updateSecuritycurrency(currencypair, investingConnection);
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return IConnectorNames.DOMAIN_INVESTING + security.getUrlIntraExtend();
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    final Connection investingConnection = Jsoup.connect(getSecurityIntradayDownloadLink(security));
    updateSecuritycurrency(security, investingConnection);
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 1;
  }

  private <T extends Securitycurrency<T>> void updateSecuritycurrency(T securitycurrency,
      final Connection investingConnection) throws IOException {
    final Document doc = investingConnection.timeout(10000).get();
    Element div = doc.select("#last_last").parents().first();

    if (div == null) {
      div = doc.select("div[class^=instrument-price_instrument-price]").first();
    }
    String[] numbers = StringUtils.normalizeSpace(div.text().replace("%", "").replace("(", " ").replace(")", ""))
        .split(" ");
    var offset = NumberUtils.isCreatable(numbers[0].replaceAll(",", "")) ? 0 : 1;
    securitycurrency.setSLast(FeedConnectorHelper.parseDoubleUS(numbers[0 + offset]));
    securitycurrency.setSOpen(
        DataHelper.round(securitycurrency.getSLast() - FeedConnectorHelper.parseDoubleUS(numbers[1 + offset])));
    securitycurrency.setSChangePercentage(FeedConnectorHelper.parseDoubleUS(numbers[2 + offset]));
    securitycurrency.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return getSecurityCurrencyHistoricalDownloadLink(security);
  }

  private <T extends Securitycurrency<T>> String getHistoricalLink(final Securitycurrency<T> securitycurreny, Date from,
      Date to, String guid) {
    return INVESTING_DOMAIN_HISTORYICAL + guid + "/0/0/0/0/history?symbol=" + securitycurreny.getUrlHistoryExtend()
        + "&resolution=D&from=" + (new Timestamp(from.getTime()).getTime() / 1000) + "&to="
        + (new Timestamp(to.getTime()).getTime() / 1000);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(Currencypair currencypair) {
    return getSecurityCurrencyHistoricalDownloadLink(currencypair);
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws Exception {
    return getEodHistory(currencyPair, from, to);
  }

  private <T extends Securitycurrency<T>> String getSecurityCurrencyHistoricalDownloadLink(
      final Securitycurrency<T> securitycurreny) {
    Date to = new Date();
    Date from = DateHelper.setTimeToZeroAndAddDay(to, -10);
    return getHistoricalLink(securitycurreny, from, to, DataHelper.generateGUID());
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    return getEodHistory(security, from, to);
  }

  private <T extends Securitycurrency<T>> List<Historyquote> getEodHistory(final Securitycurrency<T> securitycurreny,
      final Date from, final Date to) throws Exception {
    String guid = DataHelper.generateGUID();
    boolean expectVolume = securitycurreny.exspectVolume();
    List<Historyquote> historyquotes = getEodHistoryLimitedRows(securitycurreny, from, to, guid, expectVolume);

    if (historyquotes.size() >= MAX_ROWS_DELIVERD - 1
        && DateHelper.getDateDiff(historyquotes.get(0).getDate(), to, TimeUnit.DAYS) > 2) {
      historyquotes.addAll(getEodHistoryLimitedRows(securitycurreny,
          DateHelper.setTimeToZeroAndAddDay(historyquotes.get(0).getDate(), 1), to, guid, expectVolume));
    }
    return historyquotes;
  }

  private <T extends Securitycurrency<T>> List<Historyquote> getEodHistoryLimitedRows(
      final Securitycurrency<T> securitycurreny, final Date from, final Date to, String guid, boolean expectVolume)
      throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder().GET().header("accept", "application/json")
        .header("Referer", "https://tvc-invdn-com.investing.com/")
        .uri(URI.create(getHistoricalLink(securitycurreny, from, to, guid))).build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    final Quotes quotes = objectMapper.readValue(response.body(), Quotes.class);
    final List<Historyquote> historyquotes = new ArrayList<>();
    for (int i = 0; i < quotes.t.length; i++) {
      final Historyquote historyquote = new Historyquote();
      historyquotes.add(historyquote);
      historyquote.setDate(new Date(quotes.t[i].getTime() * 1000));
      historyquote.setClose(quotes.c[i]);
      historyquote.setOpen(quotes.o[i]);
      historyquote.setHigh(quotes.h[i]);
      historyquote.setLow(quotes.l[i]);
      if (expectVolume) {
        historyquote.setVolume(Long.parseLong(quotes.v[i]));
      }
    }
    return historyquotes;
  }

  @SuppressWarnings("unused")
  private static class Quotes {
    public String s;
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "s")
    public Timestamp[] t;
    public double[] c;
    public double[] o;
    public double[] h;
    public double[] l;
    public String[] v;
  }

}
