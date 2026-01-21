package grafioschtrader.gtnet.handler.impl.historyquote;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.GTNetHistoryquote;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.gtnet.m2m.model.HistoryquoteRecordDTO;
import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import grafioschtrader.repository.GTNetHistoryquoteJpaRepository;
import grafioschtrader.repository.GTNetInstrumentCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetInstrumentSecurityJpaRepository;

/**
 * Strategy for AC_PUSH_OPEN mode: queries GTNetHistoryquote (for foreign instruments)
 * or local historyquote table (for local instruments) using optimized batch queries.
 *
 * Behavior:
 * <ul>
 *   <li>Queries GTNetInstrumentSecurity/GTNetInstrumentCurrencypair to find instruments in the pool</li>
 *   <li>Uses JOIN-based locality lookup to determine which instruments exist locally</li>
 *   <li>For local instruments: batch queries local historyquote table FIRST</li>
 *   <li>For foreign instruments: batch queries gt_net_historyquote table</li>
 *   <li>Applies 10-day threshold optimization to minimize query scope</li>
 *   <li>For instruments NOT found in the pool with historical data to share: creates new entries</li>
 *   <li>Returns historical data for all matching instruments within the requested date range</li>
 * </ul>
 *
 * <h3>10-Day Threshold Optimization</h3>
 * <p>
 * To minimize the amount of data queried, the strategy uses a 10-day threshold. If all requested
 * fromDates are within the last 10 days, a single batch query is executed with the oldest fromDate.
 * For instruments requesting data older than 10 days, those are handled with individual queries
 * to avoid fetching excessive historical data for the entire batch.
 * </p>
 */
@Component
public class PushOpenHistoryquoteQueryStrategy extends BaseHistoryquoteQueryStrategy {

  @Autowired
  private GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository;

  @Autowired
  private GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;

  @Autowired
  private GTNetHistoryquoteJpaRepository gtNetHistoryquoteJpaRepository;

  @Override
  @Transactional
  public List<InstrumentHistoryquoteDTO> querySecurities(List<InstrumentHistoryquoteDTO> requested,
      Set<Integer> sendableIds) {
    List<InstrumentHistoryquoteDTO> result = new ArrayList<>();
    if (requested == null || requested.isEmpty()) {
      return result;
    }

    // Build tuples and request map using base class
    TuplesAndRequestMap tuplesAndMap = buildSecurityTuplesAndRequestMap(requested);
    if (tuplesAndMap.tuples().isEmpty()) {
      return result;
    }

    // Single batch query for all security instruments from pool
    List<GTNetInstrumentSecurity> instruments = gtNetInstrumentSecurityJpaRepository
        .findByIsinCurrencyTuples(tuplesAndMap.tuples());

    // Track which instruments were found in the pool
    Set<String> foundKeys = new HashSet<>();

    if (!instruments.isEmpty()) {
      // Get instrument IDs for locality lookup
      List<Integer> instrumentIds = instruments.stream()
          .map(GTNetInstrumentSecurity::getIdGtNetInstrument)
          .collect(Collectors.toList());

      // Determine locality via JOIN - returns mapping of gtNetInstrumentId -> localSecuritycurrencyId
      Map<Integer, Integer> localityMap = buildLocalityMapFromSecurityMappings(
          gtNetInstrumentSecurityJpaRepository.findLocalSecurityMappings(instrumentIds));

      // Separate local and foreign instruments
      List<GTNetInstrumentSecurity> localInstruments = new ArrayList<>();
      List<GTNetInstrumentSecurity> foreignInstruments = new ArrayList<>();

      for (GTNetInstrumentSecurity instrument : instruments) {
        if (localityMap.containsKey(instrument.getIdGtNetInstrument())) {
          localInstruments.add(instrument);
        } else {
          foreignInstruments.add(instrument);
        }
      }

      // Calculate threshold date (10 days ago)
      Date thresholdDate = addDays(new Date(), -THRESHOLD_DAYS);

      // Process local instruments with batch query
      processLocalSecurities(localInstruments, localityMap, tuplesAndMap.requestMap(), thresholdDate, foundKeys,
          result);

      // Process foreign instruments with batch query
      processForeignSecurities(foreignInstruments, tuplesAndMap.requestMap(), thresholdDate, foundKeys, result);
    }

    // For instruments not found in pool, create entries if request has records to store
    createNewSecurityInstruments(tuplesAndMap.requestMap(), foundKeys);

    return result;
  }

  @Override
  @Transactional
  public List<InstrumentHistoryquoteDTO> queryCurrencypairs(List<InstrumentHistoryquoteDTO> requested,
      Set<Integer> sendableIds) {
    List<InstrumentHistoryquoteDTO> result = new ArrayList<>();
    if (requested == null || requested.isEmpty()) {
      return result;
    }

    // Build tuples and request map using base class
    TuplesAndRequestMap tuplesAndMap = buildCurrencypairTuplesAndRequestMap(requested);
    if (tuplesAndMap.tuples().isEmpty()) {
      return result;
    }

    // Single batch query for all currency pair instruments from pool
    List<GTNetInstrumentCurrencypair> instruments = gtNetInstrumentCurrencypairJpaRepository
        .findByCurrencyTuples(tuplesAndMap.tuples());

    // Track which instruments were found in the pool
    Set<String> foundKeys = new HashSet<>();

    if (!instruments.isEmpty()) {
      // Get instrument IDs for locality lookup
      List<Integer> instrumentIds = instruments.stream()
          .map(GTNetInstrumentCurrencypair::getIdGtNetInstrument)
          .collect(Collectors.toList());

      // Determine locality via JOIN
      Map<Integer, Integer> localityMap = buildLocalityMapFromCurrencypairMappings(
          gtNetInstrumentCurrencypairJpaRepository.findLocalCurrencypairMappings(instrumentIds));

      // Separate local and foreign instruments
      List<GTNetInstrumentCurrencypair> localInstruments = new ArrayList<>();
      List<GTNetInstrumentCurrencypair> foreignInstruments = new ArrayList<>();

      for (GTNetInstrumentCurrencypair instrument : instruments) {
        if (localityMap.containsKey(instrument.getIdGtNetInstrument())) {
          localInstruments.add(instrument);
        } else {
          foreignInstruments.add(instrument);
        }
      }

      // Calculate threshold date (10 days ago)
      Date thresholdDate = addDays(new Date(), -THRESHOLD_DAYS);

      // Process local instruments with batch query
      processLocalCurrencypairs(localInstruments, localityMap, tuplesAndMap.requestMap(), thresholdDate, foundKeys,
          result);

      // Process foreign instruments with batch query
      processForeignCurrencypairs(foreignInstruments, tuplesAndMap.requestMap(), thresholdDate, foundKeys, result);
    }

    // For instruments not found in pool, create entries if request has records to store
    createNewCurrencypairInstruments(tuplesAndMap.requestMap(), foundKeys);

    return result;
  }

  /**
   * Processes local securities using batch query with 10-day optimization.
   */
  private void processLocalSecurities(List<GTNetInstrumentSecurity> instruments, Map<Integer, Integer> localityMap,
      Map<String, InstrumentHistoryquoteDTO> requestMap, Date thresholdDate, Set<String> foundKeys,
      List<InstrumentHistoryquoteDTO> result) {

    if (instruments.isEmpty()) {
      return;
    }

    // Determine batch fromDate based on requests
    Date batchFromDate = determineBatchFromDate(instruments, requestMap, thresholdDate,
        i -> i.getIsin() + ":" + i.getCurrency());

    // Collect local security IDs for batch query
    List<Integer> localIds = instruments.stream()
        .map(i -> localityMap.get(i.getIdGtNetInstrument()))
        .collect(Collectors.toList());

    // Batch query local historyquotes
    List<Historyquote> allQuotes = historyquoteJpaRepository
        .findByIdSecuritycurrencyInAndDateGreaterThanEqual(localIds, batchFromDate);

    // Group quotes by idSecuritycurrency for efficient lookup
    Map<Integer, List<Historyquote>> quotesBySecurityId = allQuotes.stream()
        .collect(Collectors.groupingBy(Historyquote::getIdSecuritycurrency));

    // Build results for each instrument
    for (GTNetInstrumentSecurity instrument : instruments) {
      String key = instrument.getIsin() + ":" + instrument.getCurrency();
      foundKeys.add(key);
      InstrumentHistoryquoteDTO req = requestMap.get(key);

      if (req != null && req.getFromDate() != null && req.getToDate() != null) {
        Integer localId = localityMap.get(instrument.getIdGtNetInstrument());
        List<Historyquote> quotes = quotesBySecurityId.getOrDefault(localId, List.of());

        // Filter to requested date range and convert
        List<HistoryquoteRecordDTO> records = convertHistoryquotes(quotes);
        records = filterRecordsByDateRange(records, req.getFromDate(), req.getToDate());

        if (!records.isEmpty()) {
          InstrumentHistoryquoteDTO response = buildSecurityResponse(
              instrument.getIsin(), instrument.getCurrency(), req.getFromDate(), req.getToDate(), records);
          if (response != null) {
            result.add(response);
          }
        }
      }
    }
  }

  /**
   * Processes foreign securities using batch query with 10-day optimization.
   */
  private void processForeignSecurities(List<GTNetInstrumentSecurity> instruments,
      Map<String, InstrumentHistoryquoteDTO> requestMap, Date thresholdDate, Set<String> foundKeys,
      List<InstrumentHistoryquoteDTO> result) {

    if (instruments.isEmpty()) {
      return;
    }

    // Determine batch fromDate based on requests
    Date batchFromDate = determineBatchFromDate(instruments, requestMap, thresholdDate,
        i -> i.getIsin() + ":" + i.getCurrency());

    // Collect GTNet instrument IDs for batch query
    List<Integer> instrumentIds = instruments.stream()
        .map(GTNetInstrumentSecurity::getIdGtNetInstrument)
        .collect(Collectors.toList());

    // Batch query GTNetHistoryquotes
    List<GTNetHistoryquote> allQuotes = gtNetHistoryquoteJpaRepository
        .findByInstrumentIdsAndDateGreaterThanEqual(instrumentIds, batchFromDate);

    // Group quotes by instrument ID for efficient lookup
    Map<Integer, List<GTNetHistoryquote>> quotesByInstrumentId = allQuotes.stream()
        .collect(Collectors.groupingBy(hq -> hq.getGtNetInstrument().getIdGtNetInstrument()));

    // Build results for each instrument
    for (GTNetInstrumentSecurity instrument : instruments) {
      String key = instrument.getIsin() + ":" + instrument.getCurrency();
      foundKeys.add(key);
      InstrumentHistoryquoteDTO req = requestMap.get(key);

      if (req != null && req.getFromDate() != null && req.getToDate() != null) {
        List<GTNetHistoryquote> quotes = quotesByInstrumentId.getOrDefault(instrument.getIdGtNetInstrument(), List.of());

        // Filter to requested date range and convert
        List<HistoryquoteRecordDTO> records = convertGtNetHistoryquotes(quotes);
        records = filterRecordsByDateRange(records, req.getFromDate(), req.getToDate());

        if (!records.isEmpty()) {
          InstrumentHistoryquoteDTO response = buildSecurityResponse(
              instrument.getIsin(), instrument.getCurrency(), req.getFromDate(), req.getToDate(), records);
          if (response != null) {
            result.add(response);
          }
        }
      }
    }
  }

  /**
   * Processes local currency pairs using batch query with 10-day optimization.
   */
  private void processLocalCurrencypairs(List<GTNetInstrumentCurrencypair> instruments, Map<Integer, Integer> localityMap,
      Map<String, InstrumentHistoryquoteDTO> requestMap, Date thresholdDate, Set<String> foundKeys,
      List<InstrumentHistoryquoteDTO> result) {

    if (instruments.isEmpty()) {
      return;
    }

    // Determine batch fromDate based on requests
    Date batchFromDate = determineBatchFromDate(instruments, requestMap, thresholdDate,
        i -> i.getFromCurrency() + ":" + i.getToCurrency());

    // Collect local currency pair IDs for batch query
    List<Integer> localIds = instruments.stream()
        .map(i -> localityMap.get(i.getIdGtNetInstrument()))
        .collect(Collectors.toList());

    // Batch query local historyquotes
    List<Historyquote> allQuotes = historyquoteJpaRepository
        .findByIdSecuritycurrencyInAndDateGreaterThanEqual(localIds, batchFromDate);

    // Group quotes by idSecuritycurrency for efficient lookup
    Map<Integer, List<Historyquote>> quotesBySecurityId = allQuotes.stream()
        .collect(Collectors.groupingBy(Historyquote::getIdSecuritycurrency));

    // Build results for each instrument
    for (GTNetInstrumentCurrencypair instrument : instruments) {
      String key = instrument.getFromCurrency() + ":" + instrument.getToCurrency();
      foundKeys.add(key);
      InstrumentHistoryquoteDTO req = requestMap.get(key);

      if (req != null && req.getFromDate() != null && req.getToDate() != null) {
        Integer localId = localityMap.get(instrument.getIdGtNetInstrument());
        List<Historyquote> quotes = quotesBySecurityId.getOrDefault(localId, List.of());

        // Filter to requested date range and convert
        List<HistoryquoteRecordDTO> records = convertHistoryquotes(quotes);
        records = filterRecordsByDateRange(records, req.getFromDate(), req.getToDate());

        if (!records.isEmpty()) {
          InstrumentHistoryquoteDTO response = buildCurrencypairResponse(
              instrument.getFromCurrency(), instrument.getToCurrency(), req.getFromDate(), req.getToDate(), records);
          if (response != null) {
            result.add(response);
          }
        }
      }
    }
  }

  /**
   * Processes foreign currency pairs using batch query with 10-day optimization.
   */
  private void processForeignCurrencypairs(List<GTNetInstrumentCurrencypair> instruments,
      Map<String, InstrumentHistoryquoteDTO> requestMap, Date thresholdDate, Set<String> foundKeys,
      List<InstrumentHistoryquoteDTO> result) {

    if (instruments.isEmpty()) {
      return;
    }

    // Determine batch fromDate based on requests
    Date batchFromDate = determineBatchFromDate(instruments, requestMap, thresholdDate,
        i -> i.getFromCurrency() + ":" + i.getToCurrency());

    // Collect GTNet instrument IDs for batch query
    List<Integer> instrumentIds = instruments.stream()
        .map(GTNetInstrumentCurrencypair::getIdGtNetInstrument)
        .collect(Collectors.toList());

    // Batch query GTNetHistoryquotes
    List<GTNetHistoryquote> allQuotes = gtNetHistoryquoteJpaRepository
        .findByInstrumentIdsAndDateGreaterThanEqual(instrumentIds, batchFromDate);

    // Group quotes by instrument ID for efficient lookup
    Map<Integer, List<GTNetHistoryquote>> quotesByInstrumentId = allQuotes.stream()
        .collect(Collectors.groupingBy(hq -> hq.getGtNetInstrument().getIdGtNetInstrument()));

    // Build results for each instrument
    for (GTNetInstrumentCurrencypair instrument : instruments) {
      String key = instrument.getFromCurrency() + ":" + instrument.getToCurrency();
      foundKeys.add(key);
      InstrumentHistoryquoteDTO req = requestMap.get(key);

      if (req != null && req.getFromDate() != null && req.getToDate() != null) {
        List<GTNetHistoryquote> quotes = quotesByInstrumentId.getOrDefault(instrument.getIdGtNetInstrument(), List.of());

        // Filter to requested date range and convert
        List<HistoryquoteRecordDTO> records = convertGtNetHistoryquotes(quotes);
        records = filterRecordsByDateRange(records, req.getFromDate(), req.getToDate());

        if (!records.isEmpty()) {
          InstrumentHistoryquoteDTO response = buildCurrencypairResponse(
              instrument.getFromCurrency(), instrument.getToCurrency(), req.getFromDate(), req.getToDate(), records);
          if (response != null) {
            result.add(response);
          }
        }
      }
    }
  }

  /**
   * Builds a locality map from the JOIN query results for securities.
   */
  private Map<Integer, Integer> buildLocalityMapFromSecurityMappings(List<Object[]> mappings) {
    Map<Integer, Integer> localityMap = new HashMap<>();
    for (Object[] mapping : mappings) {
      Integer gtNetInstrumentId = ((Number) mapping[0]).intValue();
      Integer localSecurityId = ((Number) mapping[1]).intValue();
      localityMap.put(gtNetInstrumentId, localSecurityId);
    }
    return localityMap;
  }

  /**
   * Builds a locality map from the JOIN query results for currency pairs.
   */
  private Map<Integer, Integer> buildLocalityMapFromCurrencypairMappings(List<Object[]> mappings) {
    Map<Integer, Integer> localityMap = new HashMap<>();
    for (Object[] mapping : mappings) {
      Integer gtNetInstrumentId = ((Number) mapping[0]).intValue();
      Integer localCurrencypairId = ((Number) mapping[1]).intValue();
      localityMap.put(gtNetInstrumentId, localCurrencypairId);
    }
    return localityMap;
  }

  /**
   * Creates new GTNet instrument entries for securities not found in the pool.
   * Only creates if the request has records to store.
   */
  private void createNewSecurityInstruments(Map<String, InstrumentHistoryquoteDTO> requestMap, Set<String> foundKeys) {
    for (Map.Entry<String, InstrumentHistoryquoteDTO> entry : requestMap.entrySet()) {
      if (!foundKeys.contains(entry.getKey())) {
        InstrumentHistoryquoteDTO req = entry.getValue();
        if (req.getRecords() != null && !req.getRecords().isEmpty()) {
          // Create instrument entry in the pool
          GTNetInstrumentSecurity newInstrument = gtNetInstrumentSecurityJpaRepository
              .findOrCreateInstrument(req.getIsin(), req.getCurrency());

          // Store the received historical data
          storeGtNetHistoryquotes(newInstrument.getIdGtNetInstrument(), req.getRecords());
        }
      }
    }
  }

  /**
   * Creates new GTNet instrument entries for currency pairs not found in the pool.
   * Only creates if the request has records to store.
   */
  private void createNewCurrencypairInstruments(Map<String, InstrumentHistoryquoteDTO> requestMap,
      Set<String> foundKeys) {
    for (Map.Entry<String, InstrumentHistoryquoteDTO> entry : requestMap.entrySet()) {
      if (!foundKeys.contains(entry.getKey())) {
        InstrumentHistoryquoteDTO req = entry.getValue();
        if (req.getRecords() != null && !req.getRecords().isEmpty()) {
          // Create instrument entry in the pool
          GTNetInstrumentCurrencypair newInstrument = gtNetInstrumentCurrencypairJpaRepository
              .findOrCreateInstrument(req.getCurrency(), req.getToCurrency());

          // Store the received historical data
          storeGtNetHistoryquotes(newInstrument.getIdGtNetInstrument(), req.getRecords());
        }
      }
    }
  }

  /**
   * Stores historyquotes in the GTNetHistoryquote table.
   */
  private void storeGtNetHistoryquotes(Integer idGtNetInstrument, List<HistoryquoteRecordDTO> records) {
    for (HistoryquoteRecordDTO record : records) {
      if (record.getDate() != null && record.getClose() != null) {
        // Check if entry already exists
        var existing = gtNetHistoryquoteJpaRepository
            .findByGtNetInstrumentIdGtNetInstrumentAndDate(idGtNetInstrument, record.getDate());

        if (existing.isEmpty()) {
          GTNetHistoryquote hq = new GTNetHistoryquote();
          // Need to set the instrument reference - fetch it first
          var instrument = gtNetInstrumentSecurityJpaRepository.findById(idGtNetInstrument);
          if (instrument.isEmpty()) {
            var cpInstrument = gtNetInstrumentCurrencypairJpaRepository.findById(idGtNetInstrument);
            if (cpInstrument.isPresent()) {
              hq.setGtNetInstrument(cpInstrument.get());
            }
          } else {
            hq.setGtNetInstrument(instrument.get());
          }
          hq.setDate(record.getDate());
          hq.setOpen(record.getOpen());
          hq.setHigh(record.getHigh());
          hq.setLow(record.getLow());
          hq.setClose(record.getClose());
          hq.setVolume(record.getVolume());
          gtNetHistoryquoteJpaRepository.save(hq);
        }
      }
    }
  }
}
