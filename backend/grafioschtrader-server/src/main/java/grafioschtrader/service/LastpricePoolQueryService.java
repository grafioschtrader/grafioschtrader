package grafioschtrader.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.GTNetLastprice;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GTNetInstrumentCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetInstrumentSecurityJpaRepository;
import grafioschtrader.repository.GTNetLastpriceJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Service for querying the local push pool (GTNetInstrument* and GTNetLastprice tables) for intraday prices.
 *
 * This service provides reusable query logic for:
 * <ul>
 *   <li>Local AC_PUSH_OPEN servers querying their own push pool before contacting remote servers</li>
 *   <li>LastpriceExchangeHandler responding to remote requests with push pool data</li>
 * </ul>
 *
 * Supports two data sources:
 * <ul>
 *   <li>Push pool (AC_PUSH_OPEN mode): Queries GTNetInstrumentSecurity/GTNetInstrumentCurrencypair
 *       and GTNetLastprice tables</li>
 *   <li>Local entities (AC_OPEN mode): Queries Security/Currencypair tables directly</li>
 * </ul>
 */
@Service
public class LastpricePoolQueryService {

  @Autowired
  private GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository;

  @Autowired
  private GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;

  @Autowired
  private GTNetLastpriceJpaRepository gtNetLastpriceJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  /**
   * Result of a push pool query, containing found prices and keys of instruments not found.
   */
  public static class PoolQueryResult {
    public final List<InstrumentPriceDTO> prices;
    public final Set<String> notFoundKeys;

    public PoolQueryResult(List<InstrumentPriceDTO> prices, Set<String> notFoundKeys) {
      this.prices = prices;
      this.notFoundKeys = notFoundKeys;
    }

    public boolean hasResults() {
      return prices != null && !prices.isEmpty();
    }
  }

  /**
   * Queries securities from the push pool (GTNetInstrumentSecurity and GTNetLastprice tables).
   * For instruments not found, returns the request as a "final price" if last price is non-zero.
   *
   * @param requested list of requested securities with their current timestamps
   * @param sendableIds set of IDs allowed to be sent (empty means all allowed)
   * @return query result with found prices and not-found keys
   */
  public PoolQueryResult querySecuritiesFromPushPool(List<InstrumentPriceDTO> requested, Set<Integer> sendableIds) {
    List<InstrumentPriceDTO> result = new ArrayList<>();
    Set<String> notFoundKeys = new HashSet<>();

    if (requested == null || requested.isEmpty()) {
      return new PoolQueryResult(result, notFoundKeys);
    }

    // Build list of valid ISIN+currency tuples
    List<String[]> tuples = new ArrayList<>();
    Map<String, InstrumentPriceDTO> requestMap = new HashMap<>();
    for (InstrumentPriceDTO req : requested) {
      if (req.getIsin() != null && req.getCurrency() != null) {
        tuples.add(new String[] { req.getIsin(), req.getCurrency() });
        requestMap.put(req.getIsin() + ":" + req.getCurrency(), req);
      }
    }

    if (tuples.isEmpty()) {
      return new PoolQueryResult(result, notFoundKeys);
    }

    // Single batch query for all security instruments
    List<GTNetInstrumentSecurity> instruments = gtNetInstrumentSecurityJpaRepository.findByIsinCurrencyTuples(tuples);

    // Get lastprice entries for found instruments
    List<Integer> instrumentIds = instruments.stream()
        .map(GTNetInstrumentSecurity::getIdGtNetInstrument)
        .toList();
    Map<Integer, GTNetLastprice> lastpriceMap = new HashMap<>();
    if (!instrumentIds.isEmpty()) {
      gtNetLastpriceJpaRepository.findByGtNetInstrumentIdGtNetInstrumentIn(instrumentIds)
          .forEach(lp -> lastpriceMap.put(lp.getGtNetInstrument().getIdGtNetInstrument(), lp));
    }

    // Track which instruments were found in the pool
    Set<String> foundKeys = new HashSet<>();

    // Match results with requests and filter by timestamp
    for (GTNetInstrumentSecurity instrument : instruments) {
      String key = instrument.getIsin() + ":" + instrument.getCurrency();
      foundKeys.add(key);
      InstrumentPriceDTO req = requestMap.get(key);
      GTNetLastprice lastprice = lastpriceMap.get(instrument.getIdGtNetInstrument());

      if (req != null && lastprice != null && isNewer(lastprice.getTimestamp(), req.getTimestamp())) {
        result.add(fromInstrumentAndLastprice(instrument, lastprice));
      }
    }

    // For instruments not found in pool, return request as final price if last is non-zero
    for (Map.Entry<String, InstrumentPriceDTO> entry : requestMap.entrySet()) {
      if (!foundKeys.contains(entry.getKey())) {
        notFoundKeys.add(entry.getKey());
        InstrumentPriceDTO req = entry.getValue();
        if (req.getLast() != null && req.getLast() != 0.0) {
          result.add(req);
        }
      }
    }

    return new PoolQueryResult(result, notFoundKeys);
  }

  /**
   * Queries currency pairs from the push pool (GTNetInstrumentCurrencypair and GTNetLastprice tables).
   * For instruments not found, returns the request as a "final price" if last price is non-zero.
   *
   * @param requested list of requested currency pairs with their current timestamps
   * @param sendableIds set of IDs allowed to be sent (empty means all allowed)
   * @return query result with found prices and not-found keys
   */
  public PoolQueryResult queryCurrencypairsFromPushPool(List<InstrumentPriceDTO> requested, Set<Integer> sendableIds) {
    List<InstrumentPriceDTO> result = new ArrayList<>();
    Set<String> notFoundKeys = new HashSet<>();

    if (requested == null || requested.isEmpty()) {
      return new PoolQueryResult(result, notFoundKeys);
    }

    // Build list of valid fromCurrency+toCurrency tuples
    List<String[]> tuples = new ArrayList<>();
    Map<String, InstrumentPriceDTO> requestMap = new HashMap<>();
    for (InstrumentPriceDTO req : requested) {
      if (req.getCurrency() != null && req.getToCurrency() != null) {
        tuples.add(new String[] { req.getCurrency(), req.getToCurrency() });
        requestMap.put(req.getCurrency() + ":" + req.getToCurrency(), req);
      }
    }

    if (tuples.isEmpty()) {
      return new PoolQueryResult(result, notFoundKeys);
    }

    // Single batch query for all currency pair instruments
    List<GTNetInstrumentCurrencypair> instruments = gtNetInstrumentCurrencypairJpaRepository.findByCurrencyTuples(tuples);

    // Get lastprice entries for found instruments
    List<Integer> instrumentIds = instruments.stream()
        .map(GTNetInstrumentCurrencypair::getIdGtNetInstrument)
        .toList();
    Map<Integer, GTNetLastprice> lastpriceMap = new HashMap<>();
    if (!instrumentIds.isEmpty()) {
      gtNetLastpriceJpaRepository.findByGtNetInstrumentIdGtNetInstrumentIn(instrumentIds)
          .forEach(lp -> lastpriceMap.put(lp.getGtNetInstrument().getIdGtNetInstrument(), lp));
    }

    // Track which instruments were found in the pool
    Set<String> foundKeys = new HashSet<>();

    // Match results with requests and filter by timestamp
    for (GTNetInstrumentCurrencypair instrument : instruments) {
      String key = instrument.getFromCurrency() + ":" + instrument.getToCurrency();
      foundKeys.add(key);
      InstrumentPriceDTO req = requestMap.get(key);
      GTNetLastprice lastprice = lastpriceMap.get(instrument.getIdGtNetInstrument());

      if (req != null && lastprice != null && isNewer(lastprice.getTimestamp(), req.getTimestamp())) {
        result.add(fromInstrumentAndLastprice(instrument, lastprice));
      }
    }

    // For instruments not found in pool, return request as final price if last is non-zero
    for (Map.Entry<String, InstrumentPriceDTO> entry : requestMap.entrySet()) {
      if (!foundKeys.contains(entry.getKey())) {
        notFoundKeys.add(entry.getKey());
        InstrumentPriceDTO req = entry.getValue();
        if (req.getLast() != null && req.getLast() != 0.0) {
          result.add(req);
        }
      }
    }

    return new PoolQueryResult(result, notFoundKeys);
  }

  /**
   * Queries securities from local Security entities.
   * Uses a single batch query for all requested securities.
   *
   * @param requested list of requested securities with their current timestamps
   * @param sendableIds set of IDs allowed to be sent (empty means all allowed)
   * @return list of matching prices that are newer than requested
   */
  public List<InstrumentPriceDTO> querySecuritiesFromLocal(List<InstrumentPriceDTO> requested, Set<Integer> sendableIds) {
    List<InstrumentPriceDTO> result = new ArrayList<>();
    if (requested == null || requested.isEmpty()) {
      return result;
    }

    // Build list of valid ISIN+currency tuples
    List<String[]> tuples = new ArrayList<>();
    Map<String, InstrumentPriceDTO> requestMap = new HashMap<>();
    for (InstrumentPriceDTO req : requested) {
      if (req.getIsin() != null && req.getCurrency() != null) {
        tuples.add(new String[] { req.getIsin(), req.getCurrency() });
        requestMap.put(req.getIsin() + ":" + req.getCurrency(), req);
      }
    }

    if (tuples.isEmpty()) {
      return result;
    }

    // Single batch query for all securities
    List<Security> securities = securityJpaRepository.findByIsinCurrencyTuples(tuples);

    // Match results with requests, check sendable permissions, and filter by timestamp
    for (Security security : securities) {
      // Check if we're allowed to send this instrument
      if (!sendableIds.isEmpty() && !sendableIds.contains(security.getIdSecuritycurrency())) {
        continue;
      }

      String key = security.getIsin() + ":" + security.getCurrency();
      InstrumentPriceDTO req = requestMap.get(key);
      if (req != null && isNewer(security.getSTimestamp(), req.getTimestamp())) {
        result.add(InstrumentPriceDTO.fromSecurity(security));
      }
    }
    return result;
  }

  /**
   * Queries currency pairs from local Currencypair entities.
   * Uses a single batch query for all requested currency pairs.
   *
   * @param requested list of requested currency pairs with their current timestamps
   * @param sendableIds set of IDs allowed to be sent (empty means all allowed)
   * @return list of matching prices that are newer than requested
   */
  public List<InstrumentPriceDTO> queryCurrencypairsFromLocal(List<InstrumentPriceDTO> requested, Set<Integer> sendableIds) {
    List<InstrumentPriceDTO> result = new ArrayList<>();
    if (requested == null || requested.isEmpty()) {
      return result;
    }

    // Build list of valid fromCurrency+toCurrency tuples
    List<String[]> tuples = new ArrayList<>();
    Map<String, InstrumentPriceDTO> requestMap = new HashMap<>();
    for (InstrumentPriceDTO req : requested) {
      if (req.getCurrency() != null && req.getToCurrency() != null) {
        tuples.add(new String[] { req.getCurrency(), req.getToCurrency() });
        requestMap.put(req.getCurrency() + ":" + req.getToCurrency(), req);
      }
    }

    if (tuples.isEmpty()) {
      return result;
    }

    // Single batch query for all currency pairs
    List<Currencypair> currencypairs = currencypairJpaRepository.findByCurrencyTuples(tuples);

    // Match results with requests, check sendable permissions, and filter by timestamp
    for (Currencypair currencypair : currencypairs) {
      // Check if we're allowed to send this instrument
      if (!sendableIds.isEmpty() && !sendableIds.contains(currencypair.getIdSecuritycurrency())) {
        continue;
      }

      String key = currencypair.getFromCurrency() + ":" + currencypair.getToCurrency();
      InstrumentPriceDTO req = requestMap.get(key);
      if (req != null && isNewer(currencypair.getSTimestamp(), req.getTimestamp())) {
        result.add(InstrumentPriceDTO.fromCurrencypair(currencypair));
      }
    }
    return result;
  }

  /**
   * Checks if the local timestamp is newer than the requested timestamp.
   */
  private boolean isNewer(Date local, Date requested) {
    if (local == null) {
      return false;
    }
    if (requested == null) {
      return true; // Requester has no data, any local data is newer
    }
    return local.after(requested);
  }

  /**
   * Creates an InstrumentPriceDTO from a GTNetInstrumentSecurity and GTNetLastprice.
   */
  private InstrumentPriceDTO fromInstrumentAndLastprice(GTNetInstrumentSecurity instrument, GTNetLastprice price) {
    InstrumentPriceDTO dto = new InstrumentPriceDTO();
    dto.setIsin(instrument.getIsin());
    dto.setCurrency(instrument.getCurrency());
    dto.setToCurrency(null);
    dto.setTimestamp(price.getTimestamp());
    dto.setOpen(price.getOpen());
    dto.setHigh(price.getHigh());
    dto.setLow(price.getLow());
    dto.setLast(price.getLast());
    dto.setVolume(price.getVolume());
    return dto;
  }

  /**
   * Creates an InstrumentPriceDTO from a GTNetInstrumentCurrencypair and GTNetLastprice.
   */
  private InstrumentPriceDTO fromInstrumentAndLastprice(GTNetInstrumentCurrencypair instrument, GTNetLastprice price) {
    InstrumentPriceDTO dto = new InstrumentPriceDTO();
    dto.setIsin(null);
    dto.setCurrency(instrument.getFromCurrency());
    dto.setToCurrency(instrument.getToCurrency());
    dto.setTimestamp(price.getTimestamp());
    dto.setOpen(price.getOpen());
    dto.setHigh(price.getHigh());
    dto.setLow(price.getLow());
    dto.setLast(price.getLast());
    dto.setVolume(price.getVolume());
    return dto;
  }
}
