package grafioschtrader.connector.instrument.swissquote;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.apache.commons.lang3.math.NumberUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;

/**
 * Stock, Bond, ETF: https://www.swissquote.ch/mobile/public/wc/h.a?l=en
 * Swissquote connector supports only last prices for securities and currencies.
 *
 * Dividend: Not Supported Splits: Not Supported
 *
 */
@Component
public class SwissquoteFeedConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static String BASE_URL = "https://www.swissquote.ch/mobile/public/wc/mq.a?s=";
  private static String BASE_URL_SUFFIX = "&l=en";
  private static Locale SQ_LOCALE = new Locale("de", "CH");
  private static final String URL_EXTENDED_REGEX = "^(([A-Z]{2})([A-Z0-9]{9,10})([0-9]{1})(_[A-Za-z0-9]{1,3}_[A-Za-z]{3})?)|([A-Za-z0-9]{1,6})$";

  private final static int MAX_NOF_THREADS = 5;
  private final Semaphore sqMaxRunning = new Semaphore(MAX_NOF_THREADS);

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
  }

  public SwissquoteFeedConnector() {
    super(supportedFeed, "swissquote", "Swissquote", URL_EXTENDED_REGEX);
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return BASE_URL + security.getUrlIntraExtend() + BASE_URL_SUFFIX;
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    // USD_GBP
    // https://www.swissquote.ch/mobile/public/wc/mq.a?s=CCXGBPXUSDXX_M2_USD&l=en
    // USD_EUR
    // https://www.swissquote.ch/mobile/public/wc/mq.a?s=CCXUSDXEURXX_M2_EUR&l=en
    // USD_CHF
    // https://www.swissquote.ch/mobile/public/wc/mq.a?s=CCXUSDXCHFXX_M2_CHF&l=en

    return BASE_URL + "CCX" + currencypair.getFromCurrency() + "X" + currencypair.getToCurrency() + "XX_M2_"
        + currencypair.getToCurrency() + BASE_URL_SUFFIX;
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    try {
      sqMaxRunning.acquire();
      final Connection swissquoteConnection = Jsoup.connect(getSecurityIntradayDownloadLink(security));
      updateSecuritycurrency(swissquoteConnection, security);
    } finally {
      sqMaxRunning.release();
    }

  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws Exception {
    try {
      sqMaxRunning.acquire();
      final Connection swissquoteConnection = Jsoup.connect(getCurrencypairIntradayDownloadLink(currencypair));
      updateSecuritycurrency(swissquoteConnection, currencypair);
    } finally {
      sqMaxRunning.release();
    }

  }

  private <T extends Securitycurrency<T>> void updateSecuritycurrency(final Connection swissquoteConnection,
      final T securitycurrency) throws IOException, ParseException {
    final Document doc = swissquoteConnection.timeout(5000).get();

    final Elements rows = doc.select("table tr");

    for (int i = 2; i < rows.size(); i++) {
      final Element row = rows.get(i);
      final Elements cols = row.select("td");
      if (cols.size() >= 2) {
        final String numberString = cols.get(1).text().replaceAll("[^-?\\d+(.\\d+)?]", "");
        if (NumberUtils.isParsable(numberString)) {

          switch (cols.get(0).text()) {
          case "Last":
            securitycurrency.setSLast(NumberFormat.getNumberInstance(SQ_LOCALE).parse(numberString).doubleValue());
            securitycurrency.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
            break;
          case "Change":
            securitycurrency
                .setSChangePercentage(NumberFormat.getNumberInstance(SQ_LOCALE).parse(numberString).doubleValue());
            break;
          case "High":
            securitycurrency.setSHigh(NumberFormat.getNumberInstance(SQ_LOCALE).parse(numberString).doubleValue());
            break;
          case "Low":
            securitycurrency.setSLow(NumberFormat.getNumberInstance(SQ_LOCALE).parse(numberString).doubleValue());
            break;
          case "Close":
            securitycurrency.setSPrevClose(NumberFormat.getNumberInstance(SQ_LOCALE).parse(numberString).doubleValue());
            break;
          case "Volume":
            if (securitycurrency instanceof Security) {
              ((Security) securitycurrency)
                  .setSVolume(NumberFormat.getNumberInstance(SQ_LOCALE).parse(numberString).longValue());
            }
            break;
          }
        }
      }
    }
  }

}
