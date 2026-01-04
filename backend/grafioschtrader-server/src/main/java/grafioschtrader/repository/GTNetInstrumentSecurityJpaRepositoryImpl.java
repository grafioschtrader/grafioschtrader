package grafioschtrader.repository;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.GTNetLastprice;
import grafioschtrader.entities.Security;

/**
 * Implementation of custom repository methods for GTNetInstrumentSecurity.
 *
 * Handles batch queries for instrument matching and coordinates updates between
 * the instrument pool and lastprice tables.
 */
public class GTNetInstrumentSecurityJpaRepositoryImpl implements GTNetInstrumentSecurityJpaRepositoryCustom {

  private static final Logger log = LoggerFactory.getLogger(GTNetInstrumentSecurityJpaRepositoryImpl.class);

  @Autowired
  private GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository;

  @Autowired
  private GTNetLastpriceJpaRepository gtNetLastpriceJpaRepository;

  @Override
  public List<GTNetInstrumentSecurity> findByIsinCurrencyTuples(List<String[]> isinCurrencyPairs) {
    if (isinCurrencyPairs == null || isinCurrencyPairs.isEmpty()) {
      return Collections.emptyList();
    }

    // Convert tuples to composite keys for JPQL query
    List<String> keys = isinCurrencyPairs.stream()
        .filter(pair -> pair[0] != null && pair[1] != null)
        .map(pair -> pair[0] + "|" + pair[1])
        .collect(Collectors.toList());

    if (keys.isEmpty()) {
      return Collections.emptyList();
    }

    return gtNetInstrumentSecurityJpaRepository.findByIsinCurrencyKeys(keys);
  }

  @Override
  @Transactional
  public int updateFromConnectorFetch(List<Security> securities, Integer idGtNet) {
    if (securities == null || securities.isEmpty() || idGtNet == null) {
      return 0;
    }

    // Filter securities that have valid ISIN and a timestamp
    List<Security> validSecurities = securities.stream()
        .filter(s -> s.getIsin() != null && !s.getIsin().isEmpty()
            && s.getCurrency() != null && s.getSTimestamp() != null)
        .collect(Collectors.toList());

    if (validSecurities.isEmpty()) {
      return 0;
    }

    // Get existing instrument entries from pool
    List<String[]> pairs = validSecurities.stream()
        .map(s -> new String[] { s.getIsin(), s.getCurrency() })
        .collect(Collectors.toList());
    List<GTNetInstrumentSecurity> existingInstruments = findByIsinCurrencyTuples(pairs);

    // Build map for quick lookup: "ISIN|CURRENCY" -> existing instrument
    Map<String, GTNetInstrumentSecurity> instrumentMap = existingInstruments.stream()
        .collect(Collectors.toMap(
            e -> e.getIsin() + "|" + e.getCurrency(),
            Function.identity()));

    // Get existing lastprice entries for these instruments
    List<Integer> instrumentIds = existingInstruments.stream()
        .map(GTNetInstrumentSecurity::getIdGtNetInstrument)
        .collect(Collectors.toList());
    Map<Integer, GTNetLastprice> lastpriceMap = gtNetLastpriceJpaRepository
        .findByGtNetInstrumentIdGtNetInstrumentIn(instrumentIds).stream()
        .collect(Collectors.toMap(
            lp -> lp.getGtNetInstrument().getIdGtNetInstrument(),
            Function.identity()));

    int updatedCount = 0;

    for (Security security : validSecurities) {
      String key = security.getIsin() + "|" + security.getCurrency();
      GTNetInstrumentSecurity instrument = instrumentMap.get(key);
      Date connectorTimestamp = security.getSTimestamp();

      // Create instrument if it doesn't exist
      if (instrument == null) {
        instrument = findOrCreateInstrument(security.getIsin(), security.getCurrency(),
            security.getIdSecuritycurrency(), idGtNet);
        instrumentMap.put(key, instrument);
        log.debug("Created new instrument pool entry for security ISIN={}", security.getIsin());
      }

      // Find or create lastprice entry
      GTNetLastprice lastprice = lastpriceMap.get(instrument.getIdGtNetInstrument());

      if (lastprice == null) {
        // Create new lastprice entry
        lastprice = createLastpriceFromSecurity(instrument, security);
        gtNetLastpriceJpaRepository.save(lastprice);
        lastpriceMap.put(instrument.getIdGtNetInstrument(), lastprice);
        updatedCount++;
        log.debug("Created new lastprice entry for security ISIN={}", security.getIsin());
      } else if (lastprice.getTimestamp() == null || connectorTimestamp.after(lastprice.getTimestamp())) {
        // Update existing lastprice entry with newer price
        updateLastpriceFromSecurity(lastprice, security);
        gtNetLastpriceJpaRepository.save(lastprice);
        updatedCount++;
        log.debug("Updated lastprice entry for security ISIN={}", security.getIsin());
      }
      // else: existing lastprice has newer or equal timestamp, skip
    }

    if (updatedCount > 0) {
      log.info("Updated {} security lastprice entries in push pool from connector fetch", updatedCount);
    }

    return updatedCount;
  }

  @Override
  @Transactional
  public GTNetInstrumentSecurity findOrCreateInstrument(String isin, String currency, Integer idSecuritycurrency,
      Integer idGtNet) {
    Optional<GTNetInstrumentSecurity> existing = gtNetInstrumentSecurityJpaRepository
        .findByIdGtNetAndIsinAndCurrency(idGtNet, isin, currency);

    if (existing.isPresent()) {
      GTNetInstrumentSecurity instrument = existing.get();
      // Update idSecuritycurrency if it was null and now we have a local reference
      if (instrument.getIdSecuritycurrency() == null && idSecuritycurrency != null) {
        instrument.setIdSecuritycurrency(idSecuritycurrency);
        return gtNetInstrumentSecurityJpaRepository.save(instrument);
      }
      return instrument;
    }

    // Create new instrument
    GTNetInstrumentSecurity newInstrument = new GTNetInstrumentSecurity();
    newInstrument.setIdGtNet(idGtNet);
    newInstrument.setIsin(isin);
    newInstrument.setCurrency(currency);
    newInstrument.setIdSecuritycurrency(idSecuritycurrency);
    return gtNetInstrumentSecurityJpaRepository.save(newInstrument);
  }

  private GTNetLastprice createLastpriceFromSecurity(GTNetInstrumentSecurity instrument, Security security) {
    GTNetLastprice lastprice = new GTNetLastprice();
    lastprice.setGtNetInstrument(instrument);
    updateLastpriceFromSecurity(lastprice, security);
    return lastprice;
  }

  private void updateLastpriceFromSecurity(GTNetLastprice lastprice, Security security) {
    lastprice.setTimestamp(security.getSTimestamp());
    lastprice.setOpen(security.getSOpen());
    lastprice.setHigh(security.getSHigh());
    lastprice.setLow(security.getSLow());
    lastprice.setLast(security.getSLast());
    lastprice.setVolume(security.getSVolume());
  }

}
