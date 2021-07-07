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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;

/**
 *
 * This provider can only deliver historical rates for 4 years in one request.
 * Therefore 3 requests are needed for the period from 2000 to 2019. It is also
 * possible that the provider does not supply any data for one or all of these
 * requests due to its load.
 *
 *
 * @author Hugo Graf
 */

@Component
public class FxUbcFeedConnector extends BaseFeedConnector {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final int CONNECTION_TIMEOUT = 8000;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] { FeedIdentifier.CURRENCY });
  }

  public FxUbcFeedConnector() {
    super(supportedFeed, "fxubc", "Pacific Exchange Rate Service", null);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return "http://fx.sauder.ubc.ca/cgi/fxdata?c=" + currencypair.getFromCurrency() + "&b="
        + currencypair.getToCurrency();
  }

  @Override
  public synchronized List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from,
      final Date to) throws IOException, ParseException, InterruptedException {

    // http://fx.sauder.ubc.ca/cgi/fxdata?b=USD&c=CHF&rd=&fd=1&fm=1&fy=2008&ld=31&lm=12&ly=2009&y=daily&q=price&f=csv&o=
    final List<Historyquote> historyquotes = new ArrayList<>();
    final Calendar fromDate = Calendar.getInstance();
    Calendar toDate = Calendar.getInstance();

    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd"); //$NON-NLS-1$
    fromDate.setTime(from);
    toDate.setTime(to);

    Calendar startDate = (Calendar) toDate.clone();
    int againstLoseDataCounter = 0;
    do {
      if (againstLoseDataCounter > 0) {
        Thread.sleep(800);
      }

      startDate.add(Calendar.YEAR, -4);

      if (startDate.before(fromDate)) {
        startDate = fromDate;
      } else {
        toDate = (Calendar) startDate.clone();
        toDate.add(Calendar.YEAR, 4);
        toDate.add(Calendar.DATE, -1);
        startDate.add(Calendar.DATE, 1);
      }

      List<Historyquote> h = getQuotes(currencyPair, dateFormat, startDate, toDate);

      long days = DateHelper.getDateDiff(startDate.getTime(), toDate.getTime(), TimeUnit.DAYS);
      log.info("Currencyparir {}/{} Start date: {} End Date: {} Got Days: {} Days diff: {}",
          currencyPair.getFromCurrency(), currencyPair.getToCurrency(), startDate.getTime(), toDate.getTime(), h.size(),
          days);
      if (h.isEmpty() && days > 5) {
        historyquotes.clear();
        return historyquotes;
      } else {
        historyquotes.addAll(h);
      }

      toDate = (Calendar) startDate.clone();
      toDate.add(Calendar.DATE, -1);
      againstLoseDataCounter++;
    } while (startDate.after(fromDate));
    return historyquotes;
  }

  private List<Historyquote> getQuotes(final Currencypair currencyPair, SimpleDateFormat dateFormat, Calendar startDate,
      Calendar toDate) throws IOException, ParseException {
    final Document doc = getPreparedURL(currencyPair, startDate, toDate).get();

    doc.outputSettings().prettyPrint(false);
    final InputStream inputStream = new ByteArrayInputStream(doc.body().html().getBytes(StandardCharsets.UTF_8));
    final BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
    List<Historyquote> h = parseCurrencyLineInput(in, dateFormat);
    in.close();
    return h;
  }

  private Connection getPreparedURL(final Currencypair currencyPair, final Calendar startDate, final Calendar toDate) {

    final Connection csvTableURL = Jsoup.connect("http://fx.sauder.ubc.ca/cgi/fxdata").ignoreContentType(true)
        .timeout(CONNECTION_TIMEOUT);

    csvTableURL.data("c", currencyPair.getFromCurrency());
    csvTableURL.data("b", currencyPair.getToCurrency());
    csvTableURL.data("rd", "");

    csvTableURL.data("fd", String.valueOf(startDate.get(Calendar.DAY_OF_MONTH)));
    csvTableURL.data("fm", String.valueOf(startDate.get(Calendar.MONTH) + 1));
    csvTableURL.data("fy", String.valueOf(startDate.get(Calendar.YEAR)));

    csvTableURL.data("ld", String.valueOf(toDate.get(Calendar.DAY_OF_MONTH)));
    csvTableURL.data("lm", String.valueOf(toDate.get(Calendar.MONTH) + 1));
    csvTableURL.data("ly", String.valueOf(toDate.get(Calendar.YEAR)));
    csvTableURL.data("y", "daily");
    csvTableURL.data("q", "price");
    csvTableURL.data("f", "csv");
    csvTableURL.data("o", "");
    return csvTableURL;
  }

  private List<Historyquote> parseCurrencyLineInput(final BufferedReader in, final SimpleDateFormat dateFormat)
      throws IOException, ParseException {

    final List<Historyquote> historyquotes = new ArrayList<>();

    String inputLine = in.readLine();

    while ((inputLine = in.readLine()) != null) {
      if (inputLine.startsWith("&quot;")) {
        continue;
      }

      final String[] item = inputLine.split(","); //$NON-NLS-1$

      if (item.length >= 3 && Character.isDigit(item[0].charAt(0))) {
        final Calendar day = Calendar.getInstance();
        day.setTime(dateFormat.parse(item[1].replaceAll("\"", "")));

        // Set time to zero
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.HOUR_OF_DAY, 0);

        final Historyquote historyQuote = new Historyquote();
        historyquotes.add(historyQuote);
        historyQuote.setDate(day.getTime());
        historyQuote.setClose(Double.parseDouble(item[3].trim()));
      }
    }
    return historyquotes;
  }

}
