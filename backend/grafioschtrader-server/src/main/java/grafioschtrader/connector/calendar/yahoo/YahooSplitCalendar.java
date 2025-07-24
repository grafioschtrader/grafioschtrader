package grafioschtrader.connector.calendar.yahoo;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import grafiosch.common.DateHelper;
import grafioschtrader.connector.calendar.ISplitCalendarFeedConnector;
import grafioschtrader.connector.yahoo.AbstractYahooFinanceConnector;
import grafioschtrader.connector.yahoo.YahooFinanceDTO;
import grafioschtrader.connector.yahoo.YahooFinanceDTO.*;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.types.CreateType;

/**
 * Yahoo Split Calendar implementation using the new POST API and base connector class. Extends
 * AbstractYahooFinanceConnector to leverage common Yahoo Finance API functionality.
 */
@Component
public class YahooSplitCalendar extends AbstractYahooFinanceConnector implements ISplitCalendarFeedConnector {

  public YahooSplitCalendar() {
    super(10); // 10 seconds connection timeout
  }

  @Override
  public Map<String, TickerSecuritysplit> getCalendarSplitForSingleDay(LocalDate forDate, String[] countryCodes)
      throws Exception {
    return executeYahooRequest(forDate);
  }

  @Override
  public int getPriority() {
    return 20;
  }

  @Override
  protected YahooFinanceDTO createRequest(Object requestParams) {
    LocalDate forDate = (LocalDate) requestParams;

    // Create base request with split-specific configuration
    YahooFinanceDTO request = createBaseRequest("DESC", "splits", "startdatetime",
        Arrays.asList("ticker", "companyshortname", "startdatetime", "optionable", "old_share_worth", "share_worth"),
        100);

    // Build date range query for the specific day
    QueryOperand gteOperand = createGteOperand("startdatetime", forDate.toString());
    QueryOperand ltOperand = createLtOperand("startdatetime", forDate.plusDays(1).toString());

    QueryOperand mainQuery = new QueryOperand();
    mainQuery.operator = "and";
    mainQuery.operands = Arrays.asList(gteOperand, ltOperand);

    request.query = mainQuery;

    return request;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T parseResponse(String responseBody, Object requestParams) throws IOException {
    LocalDate forDate = (LocalDate) requestParams;
    Map<String, TickerSecuritysplit> splitTickerMap = new HashMap<>();

    try {
      VisualizationResponse response = objectMapper.readValue(responseBody, VisualizationResponse.class);

      if (response.finance != null && response.finance.result != null && !response.finance.result.isEmpty()) {

        VisualizationResult result = response.finance.result.get(0);

        if (result.documents != null && !result.documents.isEmpty()) {
          VisualizationDocument document = result.documents.get(0);

          // Process each row to extract split information
          for (List<Object> row : document.rows) {
            if (row.size() >= 6) {
              String ticker = extractTicker(row.get(0));
              String companyName = row.get(1) != null ? row.get(1).toString() : "";

              // Parse split date
              String dateTimeStr = row.get(2) != null ? row.get(2).toString() : "";
              LocalDate splitDate = parseSplitDate(dateTimeStr, forDate);

              // Extract split factors
              Integer oldShareWorth = parseInteger(row.get(4));
              Integer shareWorth = parseInteger(row.get(5));

              if (ticker != null && !ticker.isEmpty() && oldShareWorth != null && shareWorth != null
                  && oldShareWorth > 0 && shareWorth > 0) {

                // Create Securitysplit object
                Securitysplit securitySplit = new Securitysplit(null, // idSecuritycurrency will be set later
                    DateHelper.getDateFromLocalDate(splitDate), oldShareWorth, // fromFactor
                    shareWorth, // toFactor
                    CreateType.CONNECTOR_CREATED);

                // Add to map (putIfAbsent ensures ticker uniqueness)
                splitTickerMap.putIfAbsent(ticker, new TickerSecuritysplit(companyName, securitySplit));
              }
            }
          }
        }
      }

    } catch (Exception e) {
      throw new IOException("Error parsing split response", e);
    }

    return (T) splitTickerMap;
  }

  /**
   * Extracts clean ticker symbol from raw ticker string Removes exchange suffixes (everything after the first dot)
   */
  private String extractTicker(Object tickerObj) {
    if (tickerObj == null)
      return null;

    String ticker = tickerObj.toString().trim();
    int dotPos = ticker.indexOf(".");
    return dotPos > 0 ? ticker.substring(0, dotPos) : ticker;
  }

  /**
   * Parses split date from ISO datetime string Falls back to provided forDate if parsing fails
   */
  private LocalDate parseSplitDate(String dateTimeStr, LocalDate fallbackDate) {
    try {
      if (dateTimeStr != null && !dateTimeStr.trim().isEmpty()) {
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateTimeStr);
        return zonedDateTime.toLocalDate();
      }
    } catch (Exception e) {
      log.warn("Error parsing split date: {}", dateTimeStr, e);
    }
    return fallbackDate;
  }

  /**
   * Safely parses Object to Integer Handles both direct Integer objects and numeric strings
   */
  private Integer parseInteger(Object obj) {
    if (obj == null)
      return null;

    try {
      if (obj instanceof Integer) {
        return (Integer) obj;
      } else if (obj instanceof Number) {
        return ((Number) obj).intValue();
      } else {
        String str = obj.toString().trim();
        if (str.isEmpty())
          return null;
        return Integer.parseInt(str);
      }
    } catch (Exception e) {
      log.warn("Error parsing integer value: {}", obj, e);
      return null;
    }
  }
}