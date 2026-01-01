package grafioschtrader.gtnet.handler.impl.lastprice;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNetLastpriceCurrencypair;
import grafioschtrader.entities.GTNetLastpriceSecurity;
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import grafioschtrader.repository.GTNetLastpriceCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetLastpriceSecurityJpaRepository;

/**
 * Strategy for AC_PUSH_OPEN mode: queries GTNetLastprice* tables (shared push pool).
 *
 * Behavior:
 * <ul>
 *   <li>Queries GTNetLastpriceSecurity and GTNetLastpriceCurrencypair tables</li>
 *   <li>Returns prices from the shared pool that are newer than requested</li>
 *   <li>For instruments NOT found in the pool, returns the request as a "final price"
 *       if the request has a non-zero last price</li>
 *   <li>Does NOT update any local entities</li>
 * </ul>
 */
@Component
public class PushOpenLastpriceQueryStrategy implements LastpriceQueryStrategy {

  @Autowired
  private GTNetLastpriceSecurityJpaRepository gtNetLastpriceSecurityJpaRepository;

  @Autowired
  private GTNetLastpriceCurrencypairJpaRepository gtNetLastpriceCurrencypairJpaRepository;

  @Override
  public List<InstrumentPriceDTO> querySecurities(List<InstrumentPriceDTO> requested, Set<Integer> sendableIds) {
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

    // Single batch query for all securities from push pool
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
        InstrumentPriceDTO req = entry.getValue();
        if (req.getLast() != null && req.getLast() != 0.0) {
          result.add(req);
        }
      }
    }

    return result;
  }

  @Override
  public List<InstrumentPriceDTO> queryCurrencypairs(List<InstrumentPriceDTO> requested, Set<Integer> sendableIds) {
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

    // Single batch query for all currency pairs from push pool
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
        InstrumentPriceDTO req = entry.getValue();
        if (req.getLast() != null && req.getLast() != 0.0) {
          result.add(req);
        }
      }
    }

    return result;
  }

  private boolean isNewer(Date local, Date requested) {
    if (local == null) {
      return false;
    }
    if (requested == null) {
      return true;
    }
    return local.after(requested);
  }

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
