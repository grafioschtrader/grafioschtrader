package grafioschtrader.gtnet.handler.impl.historyquote;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.m2m.model.HistoryquoteRecordDTO;
import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Strategy for AC_OPEN mode: queries local Security/Currencypair historyquote data only.
 *
 * Behavior:
 * <ul>
 *   <li>Queries local Security and Currencypair entities to find matching instruments</li>
 *   <li>Uses batch queries with 10-day threshold optimization for efficient data retrieval</li>
 *   <li>Filters results by sendableIds - only sends data for allowed instruments</li>
 *   <li>Returns local historical data that matches the request</li>
 *   <li>For instruments where no data is available AND the server WANTS to receive data,
 *       returns a "want to receive" marker with the date from which data is desired</li>
 *   <li>Does NOT interact with GTNetHistoryquote table</li>
 * </ul>
 *
 * Unlike AC_PUSH_OPEN, this mode only shares data from instruments that exist in the local database,
 * and must consider sendableIds to filter what can be exchanged.
 */
@Component
public class OpenHistoryquoteQueryStrategy extends BaseHistoryquoteQueryStrategy {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Override
  @Transactional(readOnly = true)
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

    // Get IDs of securities we want to receive historical data for
    Set<Integer> receivableIds = securityJpaRepository.findIdsWithGtNetHistoricalRecv();

    // Single batch query for all securities
    List<Security> securities = securityJpaRepository.findByIsinCurrencyTuples(tuplesAndMap.tuples());

    if (securities.isEmpty()) {
      return result;
    }

    // Separate sendable securities from receive-only
    List<Security> sendableSecurities = new ArrayList<>();
    Map<Integer, Security> securityById = new HashMap<>();

    for (Security security : securities) {
      Integer securityId = security.getIdSecuritycurrency();
      securityById.put(securityId, security);

      boolean canSend = sendableIds.isEmpty() || sendableIds.contains(securityId);
      if (canSend) {
        sendableSecurities.add(security);
      } else if (receivableIds.contains(securityId)) {
        // Cannot send, but want to receive - add marker
        addWantToReceiveMarkerForSecurity(result, security);
      }
    }

    if (sendableSecurities.isEmpty()) {
      return result;
    }

    // Calculate threshold date (10 days ago)
    Date thresholdDate = addDays(new Date(), -THRESHOLD_DAYS);

    // Determine batch fromDate based on requests
    Date batchFromDate = determineBatchFromDate(sendableSecurities, tuplesAndMap.requestMap(), thresholdDate,
        s -> s.getIsin() + ":" + s.getCurrency());

    // Collect security IDs for batch query
    List<Integer> securityIds = sendableSecurities.stream()
        .map(Security::getIdSecuritycurrency)
        .collect(Collectors.toList());

    // Batch query historyquotes
    List<Historyquote> allQuotes = historyquoteJpaRepository
        .findByIdSecuritycurrencyInAndDateGreaterThanEqual(securityIds, batchFromDate);

    // Group quotes by idSecuritycurrency for efficient lookup
    Map<Integer, List<Historyquote>> quotesBySecurityId = allQuotes.stream()
        .collect(Collectors.groupingBy(Historyquote::getIdSecuritycurrency));

    // Build results for each sendable security
    for (Security security : sendableSecurities) {
      String key = security.getIsin() + ":" + security.getCurrency();
      InstrumentHistoryquoteDTO req = tuplesAndMap.requestMap().get(key);

      if (req != null && req.getFromDate() != null && req.getToDate() != null) {
        Integer securityId = security.getIdSecuritycurrency();
        List<Historyquote> quotes = quotesBySecurityId.getOrDefault(securityId, List.of());

        // Filter to requested date range and convert
        List<HistoryquoteRecordDTO> records = convertHistoryquotes(quotes);
        records = filterRecordsByDateRange(records, req.getFromDate(), req.getToDate());

        if (!records.isEmpty()) {
          InstrumentHistoryquoteDTO response = buildSecurityResponse(
              security.getIsin(), security.getCurrency(), req.getFromDate(), req.getToDate(), records);
          if (response != null) {
            result.add(response);
          }
        } else if (receivableIds.contains(securityId)) {
          // No quotes available in requested range but we want to receive data
          addWantToReceiveMarkerForSecurity(result, security);
        }
      }
    }

    return result;
  }

  /**
   * Adds a "want to receive" marker for a security.
   * The marker indicates the date from which we need historical data.
   */
  private void addWantToReceiveMarkerForSecurity(List<InstrumentHistoryquoteDTO> result, Security security) {
    Date latestDate = historyquoteJpaRepository.getMaxDateByIdSecurity(security.getIdSecuritycurrency());
    if (latestDate != null) {
      Date wantsFromDate = addDays(latestDate, 1);
      result.add(InstrumentHistoryquoteDTO.forSecurityWantToReceive(
          security.getIsin(), security.getCurrency(), wantsFromDate));
    } else {
      // No local data at all - want data from the security's active date
      Date activeFrom = security.getActiveFromDate();
      if (activeFrom != null) {
        result.add(InstrumentHistoryquoteDTO.forSecurityWantToReceive(
            security.getIsin(), security.getCurrency(), activeFrom));
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
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

    // Get IDs of currency pairs we want to receive historical data for
    Set<Integer> receivableIds = currencypairJpaRepository.findIdsWithGtNetHistoricalRecv();

    // Single batch query for all currency pairs
    List<Currencypair> currencypairs = currencypairJpaRepository.findByCurrencyTuples(tuplesAndMap.tuples());

    if (currencypairs.isEmpty()) {
      return result;
    }

    // Separate sendable currency pairs from receive-only
    List<Currencypair> sendablePairs = new ArrayList<>();
    Map<Integer, Currencypair> pairById = new HashMap<>();

    for (Currencypair pair : currencypairs) {
      Integer pairId = pair.getIdSecuritycurrency();
      pairById.put(pairId, pair);

      boolean canSend = sendableIds.isEmpty() || sendableIds.contains(pairId);
      if (canSend) {
        sendablePairs.add(pair);
      } else if (receivableIds.contains(pairId)) {
        // Cannot send, but want to receive - add marker
        addWantToReceiveMarkerForCurrencypair(result, pair);
      }
    }

    if (sendablePairs.isEmpty()) {
      return result;
    }

    // Calculate threshold date (10 days ago)
    Date thresholdDate = addDays(new Date(), -THRESHOLD_DAYS);

    // Determine batch fromDate based on requests
    Date batchFromDate = determineBatchFromDate(sendablePairs, tuplesAndMap.requestMap(), thresholdDate,
        p -> p.getFromCurrency() + ":" + p.getToCurrency());

    // Collect currency pair IDs for batch query
    List<Integer> pairIds = sendablePairs.stream()
        .map(Currencypair::getIdSecuritycurrency)
        .collect(Collectors.toList());

    // Batch query historyquotes
    List<Historyquote> allQuotes = historyquoteJpaRepository
        .findByIdSecuritycurrencyInAndDateGreaterThanEqual(pairIds, batchFromDate);

    // Group quotes by idSecuritycurrency for efficient lookup
    Map<Integer, List<Historyquote>> quotesByPairId = allQuotes.stream()
        .collect(Collectors.groupingBy(Historyquote::getIdSecuritycurrency));

    // Build results for each sendable currency pair
    for (Currencypair pair : sendablePairs) {
      String key = pair.getFromCurrency() + ":" + pair.getToCurrency();
      InstrumentHistoryquoteDTO req = tuplesAndMap.requestMap().get(key);

      if (req != null && req.getFromDate() != null && req.getToDate() != null) {
        Integer pairId = pair.getIdSecuritycurrency();
        List<Historyquote> quotes = quotesByPairId.getOrDefault(pairId, List.of());

        // Filter to requested date range and convert
        List<HistoryquoteRecordDTO> records = convertHistoryquotes(quotes);
        records = filterRecordsByDateRange(records, req.getFromDate(), req.getToDate());

        if (!records.isEmpty()) {
          InstrumentHistoryquoteDTO response = buildCurrencypairResponse(
              pair.getFromCurrency(), pair.getToCurrency(), req.getFromDate(), req.getToDate(), records);
          if (response != null) {
            result.add(response);
          }
        } else if (receivableIds.contains(pairId)) {
          // No quotes available in requested range but we want to receive data
          addWantToReceiveMarkerForCurrencypair(result, pair);
        }
      }
    }

    return result;
  }

  /**
   * Adds a "want to receive" marker for a currency pair.
   * The marker indicates the date from which we need historical data.
   */
  private void addWantToReceiveMarkerForCurrencypair(List<InstrumentHistoryquoteDTO> result, Currencypair currencypair) {
    Date latestDate = historyquoteJpaRepository.getMaxDateByIdSecurity(currencypair.getIdSecuritycurrency());
    if (latestDate != null) {
      Date wantsFromDate = addDays(latestDate, 1);
      result.add(InstrumentHistoryquoteDTO.forCurrencypairWantToReceive(
          currencypair.getFromCurrency(), currencypair.getToCurrency(), wantsFromDate));
    }
    // Note: Currency pairs don't have an activeFromDate like securities
  }

}
