/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.connector.instrument.fxubc;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;

/**
 *
 * This provider can only deliver historical rates for 4 years in one request. Therefore 4 requests are needed for the
 * period from 2000 to 2023. It is also possible that the provider does not supply any data for one or all of these
 * requests due to its load.
 *
 * This provider only wants to answer a few requests in a certain time period. Unfortunately, the attempt with Bucket
 * did not bring any improvement, so these are commented out.
 *
 * No regex pattern is used, as the user cannot make an entry regarding the URL extension. However, the presence of the
 * currency pair is checked.
 */

@Component
public class FxUbcFeedConnector extends BaseFeedConnector {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
  private static final int CONNECTION_TIMEOUT = 8000;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.CURRENCY });
  }

  // private final Bucket bucket;

  public FxUbcFeedConnector() {
    super(supportedFeed, "fxubc", "Pacific Exchange Rate Service", null, EnumSet.of(UrlCheck.HISTORY));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.CURRENCY_PAIR);
    // Bandwidth limit = Bandwidth.builder().capacity(1).refillIntervally(1, Duration.ofSeconds(4)).build();
    // this.bucket = Bucket.builder().addLimit(limit).build();
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return "https://fx.sauder.ubc.ca/cgi/fxdata?c=" + currencypair.getFromCurrency() + "&b="
        + currencypair.getToCurrency();
  }

  @Override
  public synchronized List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final LocalDate from,
      final LocalDate to) throws IOException, InterruptedException {

    final List<Historyquote> historyquotes = new ArrayList<>();

    LocalDate startDate = to;
    int againstLoseDataCounter = 0;
    LocalDate toDate = to;
    do {
      if (againstLoseDataCounter > 0) {
        Thread.sleep(800);
      }

      startDate = startDate.minusYears(4);
      if (startDate.isBefore(from)) {
        startDate = from;
      } else {
        toDate = startDate.plusYears(4);
        if (!toDate.isEqual(to)) {
          toDate = toDate.minusDays(1);
        }
        startDate = startDate.plusDays(1);
      }

      List<Historyquote> h = getQuotes(currencyPair, startDate, toDate);
      long days = ChronoUnit.DAYS.between(startDate, toDate);
      log.info("Currencyparir {}/{} Start date: {} End Date: {} Got Days: {} Days diff: {}",
          currencyPair.getFromCurrency(), currencyPair.getToCurrency(), startDate, toDate, h.size(), days);
      if (h.isEmpty() && days > 5) {
        historyquotes.clear();
        return historyquotes;
      } else {
        historyquotes.addAll(h);
      }

      toDate = startDate.minusDays(1);

      againstLoseDataCounter++;
    } while (startDate.isAfter(from));
    return historyquotes;
  }

  private List<Historyquote> getQuotes(final Currencypair currencyPair, LocalDate startDate, LocalDate toDate)
      throws IOException {
    final Document doc = getPreparedURL(currencyPair, startDate, toDate).get();
    doc.outputSettings().prettyPrint(false);
    final InputStream inputStream = new ByteArrayInputStream(doc.body().html().getBytes(StandardCharsets.UTF_8));
    final BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
    List<Historyquote> h = parseCurrencyLineInput(in);
    in.close();
    return h;
  }

  private Connection getPreparedURL(final Currencypair currencyPair, final LocalDate startDate,
      final LocalDate toDate) {

    final Connection csvTableURL = Jsoup.connect("https://fx.sauder.ubc.ca/cgi/fxdata")
        .userAgent(GlobalConstants.USER_AGENT_HTTPCLIENT).ignoreContentType(true).timeout(CONNECTION_TIMEOUT);

    csvTableURL.data("c", currencyPair.getFromCurrency());
    csvTableURL.data("b", currencyPair.getToCurrency());
    csvTableURL.data("rd", "");

    csvTableURL.data("fd", String.valueOf(startDate.getDayOfMonth()));
    csvTableURL.data("fm", String.valueOf(startDate.getMonthValue()));
    csvTableURL.data("fy", String.valueOf(startDate.getYear()));

    csvTableURL.data("ld", String.valueOf(toDate.getDayOfMonth()));
    csvTableURL.data("lm", String.valueOf(toDate.getMonthValue()));
    csvTableURL.data("ly", String.valueOf(toDate.getYear()));
    csvTableURL.data("y", "daily");
    csvTableURL.data("q", "price");
    csvTableURL.data("f", "csv");
    csvTableURL.data("o", "");
    return csvTableURL;
  }

  private List<Historyquote> parseCurrencyLineInput(final BufferedReader in) throws IOException {

    final List<Historyquote> historyquotes = new ArrayList<>();
    int i = 0;
    String inputLine = in.readLine();

    while ((inputLine = in.readLine()) != null) {
      i++;
      if (i == 4 && inputLine.contains("Server is too busy.")) {
        throw new IOException("Server is too busy");
      }

      if (inputLine.startsWith("&quot;")) {
        continue;
      }

      final String[] item = inputLine.split(",");

      if (item.length >= 3 && Character.isDigit(item[0].charAt(0))) {
        LocalDate day = LocalDate.parse(item[1].replaceAll("\"", ""), DATE_FORMAT);
        final Historyquote historyQuote = new Historyquote();
        historyquotes.add(historyQuote);
        historyQuote.setDate(day);
        historyQuote.setClose(Double.parseDouble(item[3].trim()));
      }
    }
    return historyquotes;
  }

}
