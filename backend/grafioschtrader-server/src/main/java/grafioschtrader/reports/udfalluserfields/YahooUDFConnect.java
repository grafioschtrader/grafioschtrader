package grafioschtrader.reports.udfalluserfields;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFData;
import grafiosch.entities.UDFData.UDFDataKey;
import grafiosch.repository.UDFDataJpaRepository;
import grafiosch.udfalluserfields.UDFFieldsHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.connector.yahoo.YahooHelper;
import grafioschtrader.connector.yahoo.YahooSymbolSearch;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.repository.MicProviderMapRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;

public class YahooUDFConnect {

  private YahooSymbolSearch yahooSymbolSearch = new YahooSymbolSearch();

  private Bucket bucket;

  /**
   * We believe that the Yahoo Finance ticker symbol on the Nasdaq and NYSE
   * corresponds to the general ticker symbol of the security.
   */
  private static final List<String> US_STOCK_EXCHANGE = Collections
      .unmodifiableList(Arrays.asList(GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.STOCK_EX_MIC_NYSE));

  /**
   * The class may be inherited several times, so the symbol search may be
   * repeated several times for the same security if recreate is set to True. The
   * symbol is read from the cache within 24 hours.
   */
  private static final Map<Integer, String> CACHE_SYMBOL = new PassiveExpiringMap<>(TimeUnit.DAYS.toMillis(1));

  /**
   * This is the date format that comes from Yahoo. The time may have to be
   * adapted to the local user.
   */
  private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h a z", Locale.ENGLISH);

  private final SimpleDateFormat dateFormatEarnings = new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT);
  
  public YahooUDFConnect() {
    Bandwidth limit = Bandwidth.classic(2, Refill.intervally(1, Duration.ofSeconds(2)));
    this.bucket = Bucket.builder().addLimit(limit).build();
  }

  /**
   * Evaluates the Yahoo symbol for the given security. It attempts to determine
   * the symbol using the different connectors. If none is a Yahoo connector, the
   * symbol is searched for in the JSON of user 0. If this fails, the
   * time-consuming Yahoo search is used.
   */
  public String evaluateYahooSymbol(UDFDataJpaRepository uDFDataJpaRepository, UDFMetadataSecurity udfMDSYahooSymbol,
      Security security, MicProviderMapRepository micProviderMapRepository, boolean recreate) {
    String yahooCon = BaseFeedConnector.ID_PREFIX + YahooHelper.YAHOO;
    String yahooSymbol = yahooCon.equals(security.getIdConnectorIntra()) ? security.getUrlIntraExtend()
        : yahooCon.equals(security.getIdConnectorHistory()) ? security.getUrlHistoryExtend()
            : yahooCon.equals(security.getIdConnectorSplit()) ? security.getUrlSplitExtend()
                : yahooCon.equals(security.getIdConnectorDividend()) ? security.getUrlDividendExtend() : null;
    if (yahooSymbol == null) {
      Optional<UDFData> udfDataSymbolOpt = uDFDataJpaRepository.findById(
          new UDFDataKey(BaseConstants.UDF_ID_USER, Security.class.getSimpleName(), security.getIdSecuritycurrency()));
      if (udfDataSymbolOpt.isPresent()) {
        yahooSymbol = (String) UDFFieldsHelper.readValueFromUser0(udfMDSYahooSymbol, uDFDataJpaRepository,
            Security.class, security.getIdSecuritycurrency());
      }
      if (yahooSymbol == null || recreate) {
        yahooSymbol = CACHE_SYMBOL.get(security.getIdSecuritycurrency());
        if (yahooSymbol == null) {
          yahooSymbol = getYahooSymbolThruSymbolSearch(security, micProviderMapRepository);
          UDFFieldsHelper.writeValueToUser0(udfMDSYahooSymbol, uDFDataJpaRepository, security.getClass(),
              security.getIdSecuritycurrency(), yahooSymbol);
          CACHE_SYMBOL.put(security.getIdSecuritycurrency(), yahooSymbol);
        }
      }
    }
    return yahooSymbol == null || yahooSymbol.startsWith("^") ? null : yahooSymbol;
  }
  
  String getYahooSymbolThruSymbolSearch(Security security, MicProviderMapRepository micProviderMapRepository) {
    String yahooSymbol = null;
    String mic = security.getStockexchange().getMic();
    if (US_STOCK_EXCHANGE.contains(mic) && security.getTickerSymbol() != null) {
      yahooSymbol = security.getTickerSymbol();
    } else {
      waitForTokenOrGo();
      yahooSymbol = yahooSymbolSearch.getSymbolByISINOrSymbolOrName(micProviderMapRepository,
          security.getStockexchange().getMic(), security.getIsin(), security.getTickerSymbol(),
          security.getName());
    }
    return yahooSymbol;
  }

  /**
   * Extracts the next earning date from the specified URL for a given security.
   *
   * <p>
   * This method connects to the provided URL using Jsoup, parses the HTML content
   * to find the earning dates table, and extracts the next earning date. The
   * method ensures that the extracted date is in the future relative to the
   * current date and time.
   *
   * @param url The URL to connect to and extract the earning date from.
   * @return The next earning date as a {@link LocalDateTime}, or {@code null} if
   *         no future earning date is found.
   * @throws IOException If an I/O error occurs while connecting to the URL or
   *                     parsing the document.
   */
  public LocalDateTime extractNextEarningDate(String url) throws IOException {
    waitForTokenOrGo();
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime nextEarningDate = null;

    final Connection conn = Jsoup.connect(url)
        .userAgent(FeedConnectorHelper.getHttpAgentAsString(true));
    final Document doc = conn.get();
    Elements tables = doc.select("table.bd");
    if (tables.size() > 0) {
      Elements rows = tables.get(0).select("tr");
      for (Element row : rows) {
        Elements cols = row.select("td");
        if (cols.size() >= 6) {
          LocalDateTime localDateTime = LocalDateTime.parse(cols.get(2).text(), formatter);
          if (now.isAfter(localDateTime)) {
            break;
          }
          nextEarningDate = localDateTime;
        }
      }
    }
    return nextEarningDate;
  }
  
  public String getEarningURL(String yahooSymbol) {
    return YahooHelper.YAHOO_CALENDAR + "earnings?day=" + dateFormatEarnings.format(new Date()) + "&symbol="
        + yahooSymbol;
  }

  private void waitForTokenOrGo() {
    do {
      ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
      if (probe.isConsumed()) {
        return;
      } else {
        long waitForRefill = TimeUnit.MILLISECONDS.convert(probe.getNanosToWaitForRefill(), TimeUnit.NANOSECONDS);
        try {
          Thread.sleep(waitForRefill);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    } while (true);
  }

}
