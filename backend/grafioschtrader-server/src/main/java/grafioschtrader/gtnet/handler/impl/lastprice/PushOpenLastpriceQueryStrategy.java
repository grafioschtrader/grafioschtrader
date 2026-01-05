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
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.GTNetLastprice;
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import grafioschtrader.repository.GTNetInstrumentCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetInstrumentSecurityJpaRepository;
import grafioschtrader.repository.GTNetLastpriceJpaRepository;
import grafioschtrader.service.GlobalparametersService;

/**
 * Strategy for AC_PUSH_OPEN mode: queries GTNetInstrument* and GTNetLastprice tables (shared push pool).
 *
 * Behavior:
 * <ul>
 *   <li>Queries GTNetInstrumentSecurity/GTNetInstrumentCurrencypair and GTNetLastprice tables</li>
 *   <li>Returns prices from the shared pool that are newer than requested</li>
 *   <li>For instruments NOT found in the pool: creates new entries in GTNetInstrument* and GTNetLastprice
 *       if the request has a non-null last price, then returns the created entry</li>
 *   <li>Does NOT update local Security/Currencypair entities</li>
 * </ul>
 */
@Component
public class PushOpenLastpriceQueryStrategy implements LastpriceQueryStrategy {

  @Autowired
  private GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository;

  @Autowired
  private GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;

  @Autowired
  private GTNetLastpriceJpaRepository gtNetLastpriceJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

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

    // Single batch query for all security instruments from pool
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

    // For instruments not found in pool, create new entries if last is not null
    Integer myGtNetId = globalparametersService.getGTNetMyEntryID();
    if (myGtNetId != null) {
      for (Map.Entry<String, InstrumentPriceDTO> entry : requestMap.entrySet()) {
        if (!foundKeys.contains(entry.getKey())) {
          InstrumentPriceDTO req = entry.getValue();
          if (req.getLast() != null) {
            // Create instrument entry
            GTNetInstrumentSecurity newInstrument = gtNetInstrumentSecurityJpaRepository
                .findOrCreateInstrument(req.getIsin(), req.getCurrency(), null, myGtNetId);

            // Create lastprice entry
            GTNetLastprice newLastprice = new GTNetLastprice();
            newLastprice.setGtNetInstrument(newInstrument);
            updateLastpriceFromDTO(newLastprice, req);
            gtNetLastpriceJpaRepository.save(newLastprice);

            result.add(req);
          }
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

    // Single batch query for all currency pair instruments from pool
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

    // For instruments not found in pool, create new entries if last is not null
    Integer myGtNetId = globalparametersService.getGTNetMyEntryID();
    if (myGtNetId != null) {
      for (Map.Entry<String, InstrumentPriceDTO> entry : requestMap.entrySet()) {
        if (!foundKeys.contains(entry.getKey())) {
          InstrumentPriceDTO req = entry.getValue();
          if (req.getLast() != null) {
            // Create instrument entry
            GTNetInstrumentCurrencypair newInstrument = gtNetInstrumentCurrencypairJpaRepository
                .findOrCreateInstrument(req.getCurrency(), req.getToCurrency(), null, myGtNetId);

            // Create lastprice entry
            GTNetLastprice newLastprice = new GTNetLastprice();
            newLastprice.setGtNetInstrument(newInstrument);
            updateLastpriceFromDTO(newLastprice, req);
            gtNetLastpriceJpaRepository.save(newLastprice);

            result.add(req);
          }
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

  private void updateLastpriceFromDTO(GTNetLastprice lastprice, InstrumentPriceDTO dto) {
    lastprice.setTimestamp(dto.getTimestamp());
    lastprice.setOpen(dto.getOpen());
    lastprice.setHigh(dto.getHigh());
    lastprice.setLow(dto.getLow());
    lastprice.setLast(dto.getLast());
    lastprice.setVolume(dto.getVolume());
  }
}
