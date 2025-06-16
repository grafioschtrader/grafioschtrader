package grafioschtrader.reports.udfalluserfields;

import java.io.IOException;
import java.net.HttpURLConnection;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * Utility class for connecting to Yahoo Finance to retrieve financial data for user-defined fields. This class provides
 * functionality to evaluate Yahoo symbols for securities, extract earnings dates, and manage rate-limited access to
 * Yahoo Finance services.
 * 
 * The class implements rate limiting using the token bucket algorithm to ensure compliance with Yahoo Finance's usage
 * policies and prevent service overload. It also includes caching mechanisms to optimize performance and reduce
 * unnecessary API calls.
 * 
 * Key features include:</br>
 * - Yahoo symbol resolution through multiple strategies (existing connectors, UDF data, symbol search)</br>
 * - Earnings date extraction from Yahoo Finance calendar pages</br>
 * - Rate limiting with configurable bandwidth constraints</br>
 * - Symbol caching with expiration to reduce redundant lookups</br>
 * - Special handling for US stock exchanges (NASDAQ, NYSE)</br>
 * 
 * The class is designed to work within the UDF framework, storing and retrieving Yahoo symbols as user-defined field
 * values for securities, enabling automated financial data collection and analysis across the application.
 */
public class YahooUDFConnect {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private YahooSymbolSearch yahooSymbolSearch = new YahooSymbolSearch();

  private Bucket bucket;

  /**
   * We believe that the Yahoo Finance ticker symbol on the Nasdaq and NYSE corresponds to the general ticker symbol of
   * the security.
   */
  private static final List<String> US_STOCK_EXCHANGE = Collections
      .unmodifiableList(Arrays.asList(GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.STOCK_EX_MIC_NYSE));

  /**
   * The class may be inherited several times, so the symbol search may be repeated several times for the same security
   * if recreate is set to True. The symbol is read from the cache within 24 hours.
   */
  private static final Map<Integer, String> CACHE_SYMBOL = new PassiveExpiringMap<>(TimeUnit.DAYS.toMillis(1));

  /**
   * This is the date format that comes from Yahoo. The time may have to be adapted to the local user.
   */
  private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h a z", Locale.ENGLISH);

  private final SimpleDateFormat dateFormatEarnings = new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT);

  public YahooUDFConnect() {
    Bandwidth limit = Bandwidth.classic(2, Refill.intervally(1, Duration.ofSeconds(2)));
    this.bucket = Bucket.builder().addLimit(limit).build();
  }

  /**
   * Evaluates and determines the Yahoo Finance symbol for a given security using multiple resolution strategies. This
   * method attempts to find the Yahoo symbol through the following prioritized approaches:</br>
   * 1. Existing Yahoo connectors configured for the security (intra, history, split, dividend)</br>
   * 2. Previously stored UDF data for the security</br>
   * 3. Symbol cache (if available and not recreating)</br>
   * 4. Yahoo symbol search service (most resource-intensive option)</br>
   * 
   * The method optimizes performance by checking faster sources first and only falling back to the time-consuming Yahoo
   * search when necessary. Results are cached and persisted to UDF storage for future use.
   * 
   * @param uDFDataJpaRepository     repository for accessing UDF data storage
   * @param udfMDSYahooSymbol        metadata definition for the Yahoo symbol UDF field
   * @param security                 the security for which to determine the Yahoo symbol
   * @param micProviderMapRepository repository for market identifier code mappings
   * @param recreate                 if true, forces fresh lookup even if cached or stored data exists
   * @return the Yahoo Finance symbol for the security, or null if symbol starts with "^" or cannot be determined
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

  /**
   * Retrieves Yahoo symbol through symbol search service with special handling for US exchanges. For securities on
   * NASDAQ and NYSE exchanges, the method assumes the ticker symbol corresponds directly to the Yahoo symbol. For other
   * exchanges, it performs a comprehensive search using ISIN, ticker symbol, and security name.
   * 
   * @param security                 the security for which to search the Yahoo symbol
   * @param micProviderMapRepository repository for market identifier code mappings
   * @return the Yahoo symbol found through search, or null if not found
   */
  String getYahooSymbolThruSymbolSearch(Security security, MicProviderMapRepository micProviderMapRepository) {
    String yahooSymbol = null;
    String mic = security.getStockexchange().getMic();
    if (US_STOCK_EXCHANGE.contains(mic) && security.getTickerSymbol() != null) {
      yahooSymbol = security.getTickerSymbol();
    } else {
      waitForTokenOrGo();
      yahooSymbol = yahooSymbolSearch.getSymbolByISINOrSymbolOrName(micProviderMapRepository,
          security.getStockexchange().getMic(), security.getIsin(), security.getTickerSymbol(), security.getName());
    }
    return yahooSymbol;
  }

  /**
   * Extracts the next earning date from the specified URL for a given security.
   *
   * <p>
   * This method connects to the provided URL using Jsoup, parses the HTML content to find the earning dates table, and
   * extracts the next earning date. The method ensures that the extracted date is in the future relative to the current
   * date and time.
   *
   * @param url The URL to connect to and extract the earning date from.
   * @return The next earning date as a {@link LocalDateTime}, or {@code null} if no future earning date is found.
   * @throws IOException If an I/O error occurs while connecting to the URL or parsing the document.
   */
  LocalDateTime extractNextEarningDate(String url) throws IOException {
    waitForTokenOrGo();
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime nextEarningDate = null;

    final Connection conn = Jsoup.connect(url).userAgent(FeedConnectorHelper.getHttpAgentAsString(true));

    Connection.Response response = conn.execute();

    if (response.statusCode() == HttpURLConnection.HTTP_OK) {
      final Document doc = response.parse();
      Elements tables = doc.select("table.bd");
      if (!tables.isEmpty()) {
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
    } else {
      log.warn("Could not fetch URL: {} (Status: {})", url, response.statusCode());
    }
    return nextEarningDate;
  }

  String getEarningURL(String yahooSymbol) {
    return YahooHelper.YAHOO_CALENDAR + "earnings?day=" + dateFormatEarnings.format(new Date()) + "&symbol="
        + yahooSymbol;
  }

  /**
   * Implements rate limiting using token bucket algorithm to control access frequency. This method blocks execution
   * until a token is available from the bucket, ensuring that API calls to Yahoo Finance comply with usage policies and
   * prevent service overload. The method will wait and retry until a token becomes available.
   */
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
