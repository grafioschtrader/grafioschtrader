package grafioschtrader.connector.calendar.investing;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.fraction.Fraction;
import org.apache.commons.math3.fraction.FractionFormat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.IConnectorNames;
import grafioschtrader.connector.calendar.ICalendarFeedConnector;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.types.CreateType;

@Component
public class InvestingCalendar implements ICalendarFeedConnector {

  @Override
  public Map<String, TickerSecuritysplit> getCalendarSplitForSingleDay(LocalDate forDate, String[] countryCodes)
      throws Exception {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    List<ValueKeyHtmlSelectOptions> countries = firstReqeustForCountries(httpClient, countryCodes);
    return secondRequestForSplits(httpClient, forDate, countries);
  }

  @Override
  public int getPriority() {
    return 20;
  }

  private List<ValueKeyHtmlSelectOptions> firstReqeustForCountries(HttpClient httpClient, String[] countryCodes)
      throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(IConnectorNames.DOMAIN_INVESTING + "stock-split-calendar/"))
        .headers("Content-Type", "application/x-www-form-urlencoded", "X-Requested-With", "XMLHttpRequest",
            "User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT)
        .GET().build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return parseAndGetCountries(Jsoup.parse(response.body()), countryCodes);

  }

  private List<ValueKeyHtmlSelectOptions> parseAndGetCountries(Document doc, String[] countryCodes) {
    Elements ul = doc.select("#calendarFilterBox_country ul");
    Elements liElements = ul.select("li");
    List<ValueKeyHtmlSelectOptions> countries = new ArrayList<>();
    List<String> countyNames = getCountryNameInEnglishFromCountryCodes(countryCodes);

    for (Element li : liElements) {
      if (Collections.binarySearch(countyNames, li.text()) >= 0) {
        countries.add(new ValueKeyHtmlSelectOptions(li.select("input").get(0).attr("value"), li.text()));
      }
    }
    return countries;
  }

  private List<String> getCountryNameInEnglishFromCountryCodes(String[] countryCodes) {
    String[] countryNames = new String[countryCodes.length];
    Locale english = Locale.ENGLISH;
    for (int i = 0; i < countryNames.length; i++) {
      Locale l = new Locale("", countryCodes[i]);
      countryNames[i] = l.getDisplayCountry(english);
    }
    Arrays.sort(countryNames);
    return Arrays.asList(countryNames);
  }

  private Map<String, TickerSecuritysplit> secondRequestForSplits(HttpClient httpClient, LocalDate forDate,
      List<ValueKeyHtmlSelectOptions> countries) throws IOException, InterruptedException {

    List<FormData> formData = new ArrayList<>();
    formData.add(new FormData("dateFrom", forDate.toString()));
    formData.add(new FormData("dateTo", forDate.toString()));
    formData.add(new FormData("currentTab:", "custom"));
    formData.add(new FormData("limit_from", "0"));
    countries.stream().forEach(c -> formData.add(new FormData("country[]", c.key)));

    StringJoiner sj = new StringJoiner("&");
    for (FormData fd : formData) {
      sj.add(URLEncoder.encode(fd.key, "UTF-8") + "=" + URLEncoder.encode(fd.value, "UTF-8"));
    }

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(IConnectorNames.DOMAIN_INVESTING + "stock-split-calendar/Service/getCalendarFilteredData"))
        .headers("Content-Type", "application/x-www-form-urlencoded", "X-Requested-With", "XMLHttpRequest",
            "User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT)
        .POST(HttpRequest.BodyPublishers.ofString(sj.toString())).build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    ObjectMapper objectMapper = new ObjectMapper();
    SplitCalendarResponse splitCalendarResponse = objectMapper.readValue(response.body(), SplitCalendarResponse.class);

    return readSplitTable(Jsoup.parseBodyFragment("<table>" + splitCalendarResponse.data + "</table>"), forDate);
  }

  private Map<String, TickerSecuritysplit> readSplitTable(Document doc, LocalDate forDate) {
    Map<String, TickerSecuritysplit> splitTickerMap = new HashMap<>();
    Pattern namePattern = Pattern.compile("(.*)\\((.*)\\)$");
    FractionFormat fractionFormat = new FractionFormat(NumberFormat.getInstance(Locale.US));
    Elements rows = doc.select("tr");
    for (Element row : rows) {
      Elements cols = row.select("td");

      if (cols.size() == 3) {
        setRowDataToSplitMap(cols, forDate, fractionFormat, splitTickerMap, namePattern);
      }
    }

    return splitTickerMap;
  }

  private void setRowDataToSplitMap(Elements cols, LocalDate forDate, FractionFormat fractionFormat,
      Map<String, TickerSecuritysplit> splitTickerMap, Pattern namePattern) {
    Matcher m = namePattern.matcher(cols.get(1).text());
    if (m.find()) {
      String[] countrySplitString = cols.get(1).select("span").attr("class").split("\\s+");
      String countryCode = countrySplitString.length >= 2 ? countrySplitString[1] : null;
      String ticker = tickerTransformer(m.group(2), countryCode);
      splitTickerMap.putIfAbsent(ticker,
          new TickerSecuritysplit(m.group(1).trim(), getSecuritySplit(cols.get(2).text(), forDate, fractionFormat)));
    }
  }

  private String tickerTransformer(String investingTicker, String investingCountryCode) {
    return investingTicker;
  }

  private Securitysplit getSecuritySplit(String splitText, LocalDate forDate, FractionFormat fractionFormat) {
    String fractionStr = splitText.replace(":", "/").replaceAll("\\s+", "");
    Fraction fraction = fractionFormat.parse(fractionStr);
    return new Securitysplit(null, DateHelper.getDateFromLocalDate(forDate), fraction.getDenominator(),
        fraction.getNumerator(), CreateType.CONNECTOR_CREATED);
  }

  static class FormData {
    public String key;
    public String value;

    public FormData(String key, String value) {
      this.key = key;
      this.value = value;
    }
  }

  static class SplitCalendarResponse {
    public String timeframe;
    public Date dateFrom;
    public Date dateTo;
    public String data;
    public int rows_num;
    public int last_time_scope;
    public boolean bind_scroll_handler;

  }

}
