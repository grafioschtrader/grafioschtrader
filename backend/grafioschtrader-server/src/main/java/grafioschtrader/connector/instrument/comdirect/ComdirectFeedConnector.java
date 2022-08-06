package grafioschtrader.connector.instrument.comdirect;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import grafioschtrader.common.DataHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;

@Component
public class ComdirectFeedConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static String BASE_URL = "https://www.comdirect.de/inf/";

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.INTRA,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
  }

  public ComdirectFeedConnector() {
    super(supportedFeed, "comdirect", "comdirect", null);
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return BASE_URL + security.getUrlIntraExtend();
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    updateSecuritycurrency(security, getSecurityIntradayDownloadLink(security));
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return BASE_URL + currencypair.getUrlIntraExtend();
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws Exception {
    updateSecuritycurrency(currencypair, getCurrencypairIntradayDownloadLink(currencypair));
  }

  private <T extends Securitycurrency<T>> void updateSecuritycurrency(T securitycurrency, String url)
      throws IOException {

    final Connection comdirectConnection = Jsoup.connect(url);
    final Document doc = comdirectConnection.timeout(10000).get();

    final Element div = doc.select("#keyelement_kurs_update").first();
    String[] numbers = StringUtils.normalizeSpace(div.text().replace("%", "")).split(" ");
    securitycurrency.setSLast(FeedConnectorHelper.parseDoubleGE(numbers[0].replaceAll("[A-Z]*$", "")));
    var offset = FeedConnectorHelper.isCreatableGE(numbers[1])? 0: 1;
    securitycurrency.setSChangePercentage(FeedConnectorHelper.parseDoubleGE(numbers[1 + offset]));
    securitycurrency
        .setSOpen(DataHelper.round(securitycurrency.getSLast() - FeedConnectorHelper.parseDoubleGE(numbers[2 + offset])));
    securitycurrency.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
  }
}
