package grafioschtrader.reports.udfalluserfields;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFData;
import grafiosch.entities.UDFData.UDFDataKey;
import grafiosch.repository.UDFDataJpaRepository;
import grafiosch.udfalluserfields.UDFFieldsHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.securitysearch.SecuritySearchQuery;
import grafioschtrader.connector.securitysearch.SecuritySearchResult;
import grafioschtrader.connector.yahoo.AbstractYahooFinanceConnector;
import grafioschtrader.connector.yahoo.YahooFinanceDTO;
import grafioschtrader.connector.yahoo.YahooFinanceDTO.QueryOperand;
import grafioschtrader.connector.yahoo.YahooFinanceDTO.VisualizationDocument;
import grafioschtrader.connector.yahoo.YahooFinanceDTO.VisualizationResponse;
import grafioschtrader.connector.yahoo.YahooFinanceDTO.VisualizationResult;
import grafioschtrader.connector.yahoo.YahooHelper;
import grafioschtrader.connector.yahoo.YahooSymbolSearch;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.types.SecuritySearchStatus;

/**
 * Utility class for connecting to Yahoo Finance to retrieve financial data for user-defined fields. This class provides
 * functionality to evaluate Yahoo symbols for securities, extract earnings dates, and manage rate-limited access to
 * Yahoo Finance services.
 *
 * The symbol search is rate limited by the {@link YahooSymbolSearch} bean, whose request budget is shared by every
 * instance of this class. Caching reduces unnecessary API calls.
 *
 * Key features include:<br>
 * - Yahoo symbol resolution through multiple strategies (existing connectors, UDF data, symbol search)<br>
 * - Earnings date extraction from Yahoo Finance calendar pages<br>
 * - Rate limiting through the request budget shared by all Yahoo searches ({@link YahooSymbolSearch})<br>
 * - Symbol caching with expiration to reduce redundant lookups<br>
 * - Special handling for US stock exchanges (NASDAQ, NYSE)<br>
 *
 * The class is designed to work within the UDF framework, storing and retrieving Yahoo symbols as user-defined field
 * values for securities, enabling automated financial data collection and analysis across the application.
 */
public class YahooUDFConnect extends AbstractYahooFinanceConnector {

  private static final Logger log = LoggerFactory.getLogger(YahooUDFConnect.class);

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

  private static final DateTimeFormatter DATE_FORMAT_EARNINGS = DateTimeFormatter
      .ofPattern(BaseConstants.STANDARD_DATE_FORMAT);

  public YahooUDFConnect() {
    super(10); // 10 seconds connection timeout
  }

  /**
   * Evaluates and determines the Yahoo Finance symbol for a given security using multiple resolution strategies: the
   * URL extension of a Yahoo connector, the stored user-defined field, the cache and finally the symbol search. A
   * search that could not be carried out (rate limit, timeout, provider error) is neither stored nor cached, so a
   * previously found symbol is not overwritten by a temporary failure.
   *
   * @param uDFDataJpaRepository repository of the user-defined field values
   * @param udfMDSYahooSymbol    metadata of the hidden field holding the Yahoo symbol
   * @param security             the security
   * @param yahooSymbolSearch    the Yahoo symbol search
   * @param recreate             true to search again although a symbol is stored
   * @return the Yahoo symbol, or null when none is known or it is an index symbol
   */
  public String evaluateYahooSymbol(UDFDataJpaRepository uDFDataJpaRepository, UDFMetadataSecurity udfMDSYahooSymbol,
      Security security, YahooSymbolSearch yahooSymbolSearch, boolean recreate) {
    return evaluateYahooSymbol(uDFDataJpaRepository, udfMDSYahooSymbol, security, yahooSymbolSearch, recreate, null);
  }

  /**
   * As {@link #evaluateYahooSymbol(UDFDataJpaRepository, UDFMetadataSecurity, Security, YahooSymbolSearch, boolean)},
   * and tells when a stored symbol was replaced by another one, so that data derived from the old symbol, such as the
   * fundamental values of Yahoo, can be dropped.
   *
   * @param storedSymbolChanged receives the security ID when a stored symbol was overwritten with a different one; may
   *                            be null
   */
  public String evaluateYahooSymbol(UDFDataJpaRepository uDFDataJpaRepository, UDFMetadataSecurity udfMDSYahooSymbol,
      Security security, YahooSymbolSearch yahooSymbolSearch, boolean recreate, IntConsumer storedSymbolChanged) {
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
      String storedSymbol = yahooSymbol;
      if (yahooSymbol == null || recreate) {
        yahooSymbol = CACHE_SYMBOL.get(security.getIdSecuritycurrency());
        if (yahooSymbol == null) {
          SymbolLookup lookup = getYahooSymbolThruSymbolSearch(security, yahooSymbolSearch);
          yahooSymbol = lookup.symbol();
          if (!lookup.status().isTransient()) {
            UDFFieldsHelper.writeValueToUser0(udfMDSYahooSymbol, uDFDataJpaRepository, security.getClass(),
                security.getIdSecuritycurrency(), yahooSymbol);
            CACHE_SYMBOL.put(security.getIdSecuritycurrency(), yahooSymbol);
            if (storedSymbolChanged != null && storedSymbol != null && !storedSymbol.equals(yahooSymbol)) {
              storedSymbolChanged.accept(security.getIdSecuritycurrency());
            }
          }
        }
      }
    }
    return yahooSymbol == null || yahooSymbol.startsWith("^") ? null : yahooSymbol;
  }

  /**
   * Retrieves the Yahoo symbol through the symbol search, with special handling for US exchanges. The symbol is
   * accepted only when exactly one candidate is placed on the exchange of the security; an ambiguous result yields no
   * symbol rather than the first one.
   *
   * @param security          the security, with ISIN, ticker and stock exchange
   * @param yahooSymbolSearch the Yahoo symbol search
   * @return the symbol, or null, together with the status of the search
   */
  SymbolLookup getYahooSymbolThruSymbolSearch(Security security, YahooSymbolSearch yahooSymbolSearch) {
    String mic = security.getStockexchange().getMic();
    if (US_STOCK_EXCHANGE.contains(mic) && security.getTickerSymbol() != null) {
      return new SymbolLookup(security.getTickerSymbol(), SecuritySearchStatus.OK);
    }
    SecuritySearchResult result = yahooSymbolSearch
        .search(SecuritySearchQuery.of(security.getIsin(), security.getCurrency(), mic, security.getTickerSymbol()));
    String yahooSymbol = result.uniqueCandidateOnMic(mic, security.getTickerSymbol())
        .map(SecuritySearchCandidateDto::providerSymbol).orElse(null);
    if (yahooSymbol != null) {
      log.info("Got Yahoo symbol {} for ISIN {} on {}", yahooSymbol, security.getIsin(), mic);
    } else if (result.status().isTransient()) {
      log.warn("Yahoo symbol search for ISIN {} on {} failed: {} {}", security.getIsin(), mic, result.status(),
          result.detail());
    }
    return new SymbolLookup(yahooSymbol, result.status());
  }

  /**
   * Outcome of a symbol resolution.
   *
   * @param symbol the resolved Yahoo symbol, null when none was found unambiguously
   * @param status status of the underlying search; a transient status means the absence of a symbol says nothing
   */
  record SymbolLookup(String symbol, SecuritySearchStatus status) {
  }

  String getEarningURL(String yahooSymbol) {
    return YahooHelper.YAHOO_CALENDAR + "earnings?day=" + LocalDate.now().format(DATE_FORMAT_EARNINGS) + "&symbol="
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
    return utcDateTime.toInstant().plusMillis(gmtOffsetMillis).atZone(ZoneOffset.UTC).toLocalDateTime();
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
