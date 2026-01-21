package grafioschtrader.gtnet.handler.impl.historyquote;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.GTNetHistoryquote;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.gtnet.m2m.model.HistoryquoteRecordDTO;
import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import grafioschtrader.repository.HistoryquoteJpaRepository;

/**
 * Abstract base class providing common functionality for historyquote query strategies.
 *
 * Shared functionality includes:
 * <ul>
 *   <li>Tuple building for batch queries (ISIN+currency or fromCurrency+toCurrency)</li>
 *   <li>Request map creation for O(1) lookup by instrument key</li>
 *   <li>Conversion of Historyquote and GTNetHistoryquote to HistoryquoteRecordDTO</li>
 *   <li>Response DTO building with instrument identification and records</li>
 *   <li>Date utility methods including 10-day threshold optimization</li>
 * </ul>
 *
 * @see OpenHistoryquoteQueryStrategy for AC_OPEN mode implementation
 * @see PushOpenHistoryquoteQueryStrategy for AC_PUSH_OPEN mode implementation
 */
public abstract class BaseHistoryquoteQueryStrategy implements HistoryquoteQueryStrategy {

  /**
   * Threshold in days for batch query optimization.
   * Requests older than this threshold use the threshold date to avoid excessive data fetching.
   */
  protected static final int THRESHOLD_DAYS = 10;

  @Autowired
  protected HistoryquoteJpaRepository historyquoteJpaRepository;

  /**
   * Container for tuples and request map, used for batch processing.
   *
   * @param tuples list of [isin, currency] or [fromCurrency, toCurrency] pairs
   * @param requestMap map from composite key (e.g., "ISIN:currency") to the original request DTO
   */
  protected record TuplesAndRequestMap(List<String[]> tuples, Map<String, InstrumentHistoryquoteDTO> requestMap) {
  }

  /**
   * Builds tuples and request map for securities (ISIN + currency).
   *
   * @param requested list of requested securities
   * @return TuplesAndRequestMap with valid tuples and key-to-request mapping
   */
  protected TuplesAndRequestMap buildSecurityTuplesAndRequestMap(List<InstrumentHistoryquoteDTO> requested) {
    List<String[]> tuples = new ArrayList<>();
    Map<String, InstrumentHistoryquoteDTO> requestMap = new HashMap<>();

    for (InstrumentHistoryquoteDTO req : requested) {
      if (req.getIsin() != null && req.getCurrency() != null) {
        tuples.add(new String[] { req.getIsin(), req.getCurrency() });
        requestMap.put(req.getIsin() + ":" + req.getCurrency(), req);
      }
    }

    return new TuplesAndRequestMap(tuples, requestMap);
  }

  /**
   * Builds tuples and request map for currency pairs (fromCurrency + toCurrency).
   *
   * @param requested list of requested currency pairs
   * @return TuplesAndRequestMap with valid tuples and key-to-request mapping
   */
  protected TuplesAndRequestMap buildCurrencypairTuplesAndRequestMap(List<InstrumentHistoryquoteDTO> requested) {
    List<String[]> tuples = new ArrayList<>();
    Map<String, InstrumentHistoryquoteDTO> requestMap = new HashMap<>();

    for (InstrumentHistoryquoteDTO req : requested) {
      if (req.getCurrency() != null && req.getToCurrency() != null) {
        tuples.add(new String[] { req.getCurrency(), req.getToCurrency() });
        requestMap.put(req.getCurrency() + ":" + req.getToCurrency(), req);
      }
    }

    return new TuplesAndRequestMap(tuples, requestMap);
  }

  /**
   * Converts local Historyquote entities to DTOs.
   *
   * @param quotes list of Historyquote entities
   * @return list of HistoryquoteRecordDTO
   */
  protected List<HistoryquoteRecordDTO> convertHistoryquotes(List<Historyquote> quotes) {
    List<HistoryquoteRecordDTO> records = new ArrayList<>();
    for (Historyquote hq : quotes) {
      records.add(new HistoryquoteRecordDTO(
          hq.getDate(),
          hq.getOpen(),
          hq.getHigh(),
          hq.getLow(),
          hq.getClose(),
          hq.getVolume()));
    }
    return records;
  }

  /**
   * Converts GTNetHistoryquote entities to DTOs.
   *
   * @param quotes list of GTNetHistoryquote entities
   * @return list of HistoryquoteRecordDTO
   */
  protected List<HistoryquoteRecordDTO> convertGtNetHistoryquotes(List<GTNetHistoryquote> quotes) {
    List<HistoryquoteRecordDTO> records = new ArrayList<>();
    for (GTNetHistoryquote hq : quotes) {
      records.add(new HistoryquoteRecordDTO(
          hq.getDate(),
          hq.getOpen(),
          hq.getHigh(),
          hq.getLow(),
          hq.getClose(),
          hq.getVolume()));
    }
    return records;
  }

  /**
   * Builds a response DTO for a security.
   *
   * @param isin the ISIN code
   * @param currency the currency
   * @param fromDate the start date of the range
   * @param toDate the end date of the range
   * @param records the historyquote records
   * @return populated InstrumentHistoryquoteDTO or null if records is empty
   */
  protected InstrumentHistoryquoteDTO buildSecurityResponse(String isin, String currency, Date fromDate, Date toDate,
      List<HistoryquoteRecordDTO> records) {
    if (records.isEmpty()) {
      return null;
    }

    InstrumentHistoryquoteDTO response = new InstrumentHistoryquoteDTO();
    response.setIsin(isin);
    response.setCurrency(currency);
    response.setFromDate(fromDate);
    response.setToDate(toDate);
    response.setRecords(records);
    return response;
  }

  /**
   * Builds a response DTO for a currency pair.
   *
   * @param fromCurrency the source currency
   * @param toCurrency the target currency
   * @param fromDate the start date of the range
   * @param toDate the end date of the range
   * @param records the historyquote records
   * @return populated InstrumentHistoryquoteDTO or null if records is empty
   */
  protected InstrumentHistoryquoteDTO buildCurrencypairResponse(String fromCurrency, String toCurrency, Date fromDate,
      Date toDate, List<HistoryquoteRecordDTO> records) {
    if (records.isEmpty()) {
      return null;
    }

    InstrumentHistoryquoteDTO response = new InstrumentHistoryquoteDTO();
    response.setCurrency(fromCurrency);
    response.setToCurrency(toCurrency);
    response.setFromDate(fromDate);
    response.setToDate(toDate);
    response.setRecords(records);
    return response;
  }

  /**
   * Adds specified number of days to a date.
   *
   * @param date the base date
   * @param days number of days to add (can be negative)
   * @return new Date with days added
   */
  protected Date addDays(Date date, int days) {
    Calendar cal = Calendar.getInstance();
    cal.setTime(date);
    cal.add(Calendar.DAY_OF_MONTH, days);
    return cal.getTime();
  }

  /**
   * Filters historyquote records to keep only those within the specified date range.
   *
   * @param records list of records to filter
   * @param fromDate start date (inclusive)
   * @param toDate end date (inclusive)
   * @return filtered list containing only records within the date range
   */
  protected List<HistoryquoteRecordDTO> filterRecordsByDateRange(List<HistoryquoteRecordDTO> records, Date fromDate,
      Date toDate) {
    List<HistoryquoteRecordDTO> filtered = new ArrayList<>();
    for (HistoryquoteRecordDTO record : records) {
      Date recordDate = record.getDate();
      if (recordDate != null && !recordDate.before(fromDate) && !recordDate.after(toDate)) {
        filtered.add(record);
      }
    }
    return filtered;
  }

  /**
   * Determines the optimal batch fromDate using the 10-day threshold optimization.
   *
   * If all requested fromDates are within the threshold, uses the oldest fromDate.
   * Otherwise, uses the threshold date to avoid fetching excessive data for all instruments.
   *
   * @param <T> the instrument type
   * @param instruments list of instruments
   * @param requestMap map of requests
   * @param thresholdDate the threshold date (typically THRESHOLD_DAYS ago)
   * @param keyExtractor function to extract the lookup key from an instrument
   * @return the optimal batch fromDate
   */
  protected <T> Date determineBatchFromDate(List<T> instruments, Map<String, InstrumentHistoryquoteDTO> requestMap,
      Date thresholdDate, java.util.function.Function<T, String> keyExtractor) {

    Date oldestFromDate = null;
    boolean hasOlderThanThreshold = false;

    for (T instrument : instruments) {
      String key = keyExtractor.apply(instrument);
      InstrumentHistoryquoteDTO req = requestMap.get(key);
      if (req != null && req.getFromDate() != null) {
        if (oldestFromDate == null || req.getFromDate().before(oldestFromDate)) {
          oldestFromDate = req.getFromDate();
        }
        if (req.getFromDate().before(thresholdDate)) {
          hasOlderThanThreshold = true;
        }
      }
    }

    // If any request needs data older than threshold, use threshold as batch fromDate
    // This ensures we don't fetch excessive data for all instruments
    if (hasOlderThanThreshold) {
      return thresholdDate;
    }

    // All requests within threshold, use oldest fromDate
    return oldestFromDate != null ? oldestFromDate : thresholdDate;
  }

}
