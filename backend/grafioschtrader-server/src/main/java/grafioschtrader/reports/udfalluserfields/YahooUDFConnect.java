package grafioschtrader.reports.udfalluserfields;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.map.PassiveExpiringMap;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFData;
import grafiosch.entities.UDFData.UDFDataKey;
import grafiosch.repository.UDFDataJpaRepository;
import grafiosch.udfalluserfields.UDFFieldsHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.yahoo.AbstractYahooFinanceConnector;
import grafioschtrader.connector.yahoo.YahooFinanceDTO;
import grafioschtrader.connector.yahoo.YahooFinanceDTO.QueryOperand;
import grafioschtrader.connector.yahoo.YahooFinanceDTO.VisualizationDocument;
import grafioschtrader.connector.yahoo.YahooFinanceDTO.VisualizationResponse;
import grafioschtrader.connector.yahoo.YahooFinanceDTO.VisualizationResult;
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
 * Key features include:<br>
 * - Yahoo symbol resolution through multiple strategies (existing connectors, UDF data, symbol search)<br>
 * - Earnings date extraction from Yahoo Finance calendar pages<br>
 * - Rate limiting with configurable bandwidth constraints<br>
 * - Symbol caching with expiration to reduce redundant lookups<br>
 * - Special handling for US stock exchanges (NASDAQ, NYSE)<br>
 * 
 * The class is designed to work within the UDF framework, storing and retrieving Yahoo symbols as user-defined field
 * values for securities, enabling automated financial data collection and analysis across the application.
 */
public class YahooUDFConnect extends AbstractYahooFinanceConnector {

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

  
  private final SimpleDateFormat dateFormatEarnings = new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT);

  public YahooUDFConnect() {
    super(10); // 10 seconds connection timeout

    // Initialize rate limiting
    Bandwidth limit = Bandwidth.classic(2, Refill.intervally(1, Duration.ofSeconds(2)));
    this.bucket = Bucket.builder().addLimit(limit).build();
  }

  /**
   * Evaluates and determines the Yahoo Finance symbol for a given security using multiple resolution strategies.
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
   * Retrieves Yahoo symbol through symbol search service with special handling for US exchanges.
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

  String getEarningURL(String yahooSymbol) {
    return YahooHelper.YAHOO_CALENDAR + "earnings?day=" + dateFormatEarnings.format(new Date()) + "&symbol="
        + yahooSymbol;
  }

  /**
   * Extracts the next earnings announcement date for a given ticker symbol Uses the base class template method for
   * standardized request handling
   */
  public LocalDateTime extractNextEarningDate(String ticker) throws IOException {
    try {
      EarningsRequestParams params = new EarningsRequestParams(ticker, LocalDateTime.now());
      return executeYahooRequest(params);
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      }
      throw new IOException("Error extracting next earning date for ticker " + ticker, e);
    }
  }

  @Override
  protected YahooFinanceDTO createRequest(Object requestParams) {
    EarningsRequestParams params = (EarningsRequestParams) requestParams;

    // Create base request with earnings-specific configuration
    YahooFinanceDTO request = createBaseRequest("DESC", "sp_earnings", "startdatetime",
        Arrays.asList("ticker", "companyshortname", "eventname", "startdatetime", "startdatetimetype", "epsestimate",
            "epsactual", "epssurprisepct", "timeZoneShortName", "gmtOffsetMilliSeconds", "intradaymarketcap"),
        25);
    // Build query structure
    QueryOperand tickerOperand = new QueryOperand();
    tickerOperand.operator = "or";
    tickerOperand.operands = Arrays.asList(createEqualsOperand("ticker", params.ticker));
    QueryOperand eventTypeOperand = new QueryOperand();
    eventTypeOperand.operator = "or";
    eventTypeOperand.operands = Arrays.asList(createEqualsOperand("eventtype", "EAD"),
        createEqualsOperand("eventtype", "ERA"));

    QueryOperand mainQuery = new QueryOperand();
    mainQuery.operator = "and";
    mainQuery.operands = Arrays.asList(tickerOperand, eventTypeOperand);
    request.query = mainQuery;
    return request;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T parseResponse(String responseBody, Object requestParams) throws IOException {
    EarningsRequestParams params = (EarningsRequestParams) requestParams;
    LocalDateTime now = params.now;

    try {
      VisualizationResponse response = objectMapper.readValue(responseBody, VisualizationResponse.class);

      if (response.finance != null && response.finance.result != null && !response.finance.result.isEmpty()) {
        VisualizationResult result = response.finance.result.get(0);

        if (result.documents != null && !result.documents.isEmpty()) {
          VisualizationDocument document = result.documents.get(0);
          LocalDateTime nextEarningDate = null;

          // Iterate through all rows to find the next future earnings date
          for (List<Object> row : document.rows) {
            if (row.size() > 10 && row.get(3) != null && row.get(9) != null) {
              String dateTimeStr = row.get(3).toString();
              Object gmtOffsetObj = row.get(9); // gmtOffsetMilliSeconds

              try {
                ZonedDateTime utcDateTime = ZonedDateTime.parse(dateTimeStr);

                // Apply GMT offset to get local time
                LocalDateTime localDateTime = applyGmtOffset(utcDateTime, gmtOffsetObj);

                if (localDateTime != null) {
                  // Similar logic to original: if current time is after earnings time, break
                  if (now.isAfter(localDateTime)) {
                    break;
                  }

                  // Set the next earnings date (data is sorted DESC, so first future date is the next one)
                  if (nextEarningDate == null) {
                    nextEarningDate = localDateTime;
                  }
                }
              } catch (Exception e) {
                log.warn("Error parsing date: {}", dateTimeStr, e);
              }
            }
          }

          return (T) nextEarningDate;
        }
      }
    } catch (Exception e) {
      throw new IOException("Error parsing earnings response", e);
    }
    return null;
  }

  protected LocalDateTime applyGmtOffset(ZonedDateTime utcDateTime, Object gmtOffsetObj) {
    long gmtOffsetMillis = ((Number) gmtOffsetObj).longValue();
    return utcDateTime.toInstant().plusMillis(gmtOffsetMillis) 
        .atZone(ZoneOffset.UTC).toLocalDateTime();
  }

  /**
   * Implements rate limiting using token bucket algorithm to control access frequency.
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

  /**
   * Parameter class to pass multiple values to the template method
   */
  private static class EarningsRequestParams {
    final String ticker;
    final LocalDateTime now;

    EarningsRequestParams(String ticker, LocalDateTime now) {
      this.ticker = ticker;
      this.now = now;
    }
  }
}
