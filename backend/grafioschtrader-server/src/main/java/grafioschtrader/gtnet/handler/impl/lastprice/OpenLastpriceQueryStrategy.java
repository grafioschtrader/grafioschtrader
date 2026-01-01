package grafioschtrader.gtnet.handler.impl.lastprice;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Strategy for AC_OPEN mode: queries local Security/Currencypair entities and updates them
 * if incoming prices are newer.
 *
 * Behavior:
 * <ul>
 *   <li>Queries local Security and Currencypair entities</li>
 *   <li>If incoming request prices are newer than local, updates local entities</li>
 *   <li>Returns local prices that are newer than what was requested</li>
 *   <li>Does NOT interact with GTNetLastprice* tables</li>
 * </ul>
 *
 * This enables bidirectional price exchange: the server both receives updates and shares its own data.
 */
@Component
public class OpenLastpriceQueryStrategy implements LastpriceQueryStrategy {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Override
  @Transactional
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

    // Single batch query for all securities
    List<Security> securities = securityJpaRepository.findByIsinCurrencyTuples(tuples);

    for (Security security : securities) {
      String key = security.getIsin() + ":" + security.getCurrency();
      InstrumentPriceDTO req = requestMap.get(key);
      if (req == null) {
        continue;
      }

      // Update local instrument if incoming price is newer
      if (isNewer(req.getTimestamp(), security.getSTimestamp())) {
        updateSecurityFromDTO(security, req);
        // After update, local is now same as request, so nothing newer to return for this one
      } else if (isNewer(security.getSTimestamp(), req.getTimestamp())) {
        // Local is newer than request - check if we're allowed to send
        if (sendableIds.isEmpty() || sendableIds.contains(security.getIdSecuritycurrency())) {
          result.add(InstrumentPriceDTO.fromSecurity(security));
        }
      }
    }

    return result;
  }

  @Override
  @Transactional
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

    // Single batch query for all currency pairs
    List<Currencypair> currencypairs = currencypairJpaRepository.findByCurrencyTuples(tuples);

    for (Currencypair currencypair : currencypairs) {
      String key = currencypair.getFromCurrency() + ":" + currencypair.getToCurrency();
      InstrumentPriceDTO req = requestMap.get(key);
      if (req == null) {
        continue;
      }

      // Update local instrument if incoming price is newer
      if (isNewer(req.getTimestamp(), currencypair.getSTimestamp())) {
        updateCurrencypairFromDTO(currencypair, req);
        // After update, local is now same as request, so nothing newer to return for this one
      } else if (isNewer(currencypair.getSTimestamp(), req.getTimestamp())) {
        // Local is newer than request - check if we're allowed to send
        if (sendableIds.isEmpty() || sendableIds.contains(currencypair.getIdSecuritycurrency())) {
          result.add(InstrumentPriceDTO.fromCurrencypair(currencypair));
        }
      }
    }

    return result;
  }

  private boolean isNewer(Date candidate, Date existing) {
    if (candidate == null) {
      return false;
    }
    if (existing == null) {
      return true;
    }
    return candidate.after(existing);
  }

  private void updateSecurityFromDTO(Security security, InstrumentPriceDTO dto) {
    security.setSTimestamp(dto.getTimestamp());
    if (dto.getOpen() != null) {
      security.setSOpen(dto.getOpen());
    }
    if (dto.getHigh() != null) {
      security.setSHigh(dto.getHigh());
    }
    if (dto.getLow() != null) {
      security.setSLow(dto.getLow());
    }
    if (dto.getLast() != null) {
      security.setSLast(dto.getLast());
    }
    if (dto.getVolume() != null) {
      security.setSVolume(dto.getVolume());
    }
  }

  private void updateCurrencypairFromDTO(Currencypair currencypair, InstrumentPriceDTO dto) {
    currencypair.setSTimestamp(dto.getTimestamp());
    if (dto.getOpen() != null) {
      currencypair.setSOpen(dto.getOpen());
    }
    if (dto.getHigh() != null) {
      currencypair.setSHigh(dto.getHigh());
    }
    if (dto.getLow() != null) {
      currencypair.setSLow(dto.getLow());
    }
    if (dto.getLast() != null) {
      currencypair.setSLast(dto.getLast());
    }
    // Note: Currencypair entities don't have volume field
  }
}
