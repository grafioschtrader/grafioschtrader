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
import grafioschtrader.entities.GTNetLastpriceCurrencypair;
import grafioschtrader.entities.GTNetLastpriceSecurity;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GTNetLastpriceCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetLastpriceSecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Service for querying the local push pool (GTNetLastprice* tables) for intraday prices.
 *
 * This service provides reusable query logic for:
 * <ul>
 *   <li>Local AC_PUSH_OPEN servers querying their own push pool before contacting remote servers</li>
 *   <li>LastpriceExchangeHandler responding to remote requests with push pool data</li>
 * </ul>
 *
 * Supports two data sources:
 * <ul>
 *   <li>Push pool (AC_PUSH_OPEN mode): Queries GTNetLastpriceSecurity/GTNetLastpriceCurrencypair tables</li>
 *   <li>Local entities (AC_OPEN mode): Queries Security/Currencypair tables directly</li>
 * </ul>
 */
@Service
public class LastpricePoolQueryService {

  @Autowired
  private GTNetLastpriceSecurityJpaRepository gtNetLastpriceSecurityJpaRepository;

  @Autowired
  private GTNetLastpriceCurrencypairJpaRepository gtNetLastpriceCurrencypairJpaRepository;

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
   * Queries securities from the push pool (GTNetLastpriceSecurity table).
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

    // Single batch query for all securities
    List<GTNetLastpriceSecurity> prices = gtNetLastpriceSecurityJpaRepository.findByIsinCurrencyTuples(tuples);

    // Track which instruments were found in the pool
    Set<String> foundKeys = new HashSet<>();

    // Match results with requests and filter by timestamp
    for (GTNetLastpriceSecurity price : prices) {
      String key = price.getIsin() + ":" + price.getCurrency();
      foundKeys.add(key);
      InstrumentPriceDTO req = requestMap.get(key);
      if (req != null && isNewer(price.getTimestamp(), req.getTimestamp())) {
        result.add(fromGTNetLastpriceSecurity(price));
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
   * Queries currency pairs from the push pool (GTNetLastpriceCurrencypair table).
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

    // Single batch query for all currency pairs
    List<GTNetLastpriceCurrencypair> prices = gtNetLastpriceCurrencypairJpaRepository.findByCurrencyTuples(tuples);

    // Track which instruments were found in the pool
    Set<String> foundKeys = new HashSet<>();

    // Match results with requests and filter by timestamp
    for (GTNetLastpriceCurrencypair price : prices) {
      String key = price.getFromCurrency() + ":" + price.getToCurrency();
      foundKeys.add(key);
      InstrumentPriceDTO req = requestMap.get(key);
      if (req != null && isNewer(price.getTimestamp(), req.getTimestamp())) {
        result.add(fromGTNetLastpriceCurrencypair(price));
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
   * Creates an InstrumentPriceDTO from a GTNetLastpriceSecurity entity.
   */
  private InstrumentPriceDTO fromGTNetLastpriceSecurity(GTNetLastpriceSecurity price) {
    InstrumentPriceDTO dto = new InstrumentPriceDTO();
    dto.setIsin(price.getIsin());
    dto.setCurrency(price.getCurrency());
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
   * Creates an InstrumentPriceDTO from a GTNetLastpriceCurrencypair entity.
   */
  private InstrumentPriceDTO fromGTNetLastpriceCurrencypair(GTNetLastpriceCurrencypair price) {
    InstrumentPriceDTO dto = new InstrumentPriceDTO();
    dto.setIsin(null);
    dto.setCurrency(price.getFromCurrency());
    dto.setToCurrency(price.getToCurrency());
    dto.setTimestamp(price.getTimestamp());
    dto.setOpen(price.getOpen());
    dto.setHigh(price.getHigh());
    dto.setLow(price.getLow());
    dto.setLast(price.getLast());
    dto.setVolume(price.getVolume());
    return dto;
  }
}
