package grafioschtrader.service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetInstrument;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.GTNetLastprice;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import grafioschtrader.repository.GTNetInstrumentCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetInstrumentSecurityJpaRepository;
import grafioschtrader.repository.GTNetLastpriceJpaRepository;

/**
 * Service for managing the GTNet lastprice push pool.
 *
 * Provides unified methods for updating the push pool (GTNetInstrument* + GTNetLastprice tables)
 * from various sources:
 * <ul>
 *   <li>Connector-fetched prices (Security/Currencypair entities)</li>
 *   <li>Pushed prices from remote servers (InstrumentPriceDTO)</li>
 *   <li>Prices received during exchange requests (InstrumentPriceDTO)</li>
 * </ul>
 *
 * All methods follow the same pattern:
 * <ol>
 *   <li>Find or create GTNetInstrument entry</li>
 *   <li>Find or create GTNetLastprice entry</li>
 *   <li>Update only if the new price has a newer timestamp</li>
 * </ol>
 */
@Service
public class GTNetLastpricePoolService {

  private static final Logger log = LoggerFactory.getLogger(GTNetLastpricePoolService.class);

  @Autowired
  private GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository;

  @Autowired
  private GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;

  @Autowired
  private GTNetLastpriceJpaRepository gtNetLastpriceJpaRepository;

  // ==================== Security Methods ====================

  /**
   * Updates the push pool with security prices from connector fetch.
   *
   * @param securities list of securities with updated prices
   * @return number of prices accepted (created or updated)
   */
  @Transactional
  public int updateSecurityLastprices(List<Security> securities) {
    if (securities == null || securities.isEmpty()) {
      return 0;
    }

    // Filter securities that have valid ISIN, currency, and timestamp
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
    Map<String, GTNetInstrumentSecurity> instrumentMap = buildSecurityInstrumentMap(pairs);

    // Get existing lastprice entries
    Map<Integer, GTNetLastprice> lastpriceMap = buildLastpriceMap(
        instrumentMap.values().stream()
            .map(GTNetInstrumentSecurity::getIdGtNetInstrument)
            .collect(Collectors.toList()));

    int updatedCount = 0;

    for (Security security : validSecurities) {
      String key = security.getIsin() + "|" + security.getCurrency();
      GTNetInstrumentSecurity instrument = instrumentMap.get(key);

      // Create instrument if it doesn't exist
      if (instrument == null) {
        instrument = gtNetInstrumentSecurityJpaRepository
            .findOrCreateInstrument(security.getIsin(), security.getCurrency());
        instrumentMap.put(key, instrument);
        log.debug("Created new instrument pool entry for security ISIN={}", security.getIsin());
      }

      // Update lastprice if newer
      if (updateLastpriceIfNewer(instrument, lastpriceMap, security.getSTimestamp(),
          security.getSOpen(), security.getSHigh(), security.getSLow(),
          security.getSLast(), security.getSVolume())) {
        updatedCount++;
      }
    }

    if (updatedCount > 0) {
      log.info("Updated {} security lastprice entries in push pool", updatedCount);
    }

    return updatedCount;
  }

  /**
   * Updates the push pool with security prices from DTOs.
   *
   * @param dtos list of security price DTOs
   * @return number of prices accepted (created or updated)
   */
  @Transactional
  public int updateSecurityLastpricesFromDTO(List<InstrumentPriceDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return 0;
    }

    // Filter DTOs that have valid ISIN, currency, and last price
    List<InstrumentPriceDTO> validDTOs = dtos.stream()
        .filter(dto -> dto.getIsin() != null && dto.getCurrency() != null && dto.getLast() != null)
        .collect(Collectors.toList());

    if (validDTOs.isEmpty()) {
      return 0;
    }

    // Get existing instrument entries from pool
    List<String[]> pairs = validDTOs.stream()
        .map(dto -> new String[] { dto.getIsin(), dto.getCurrency() })
        .collect(Collectors.toList());
    Map<String, GTNetInstrumentSecurity> instrumentMap = buildSecurityInstrumentMap(pairs);

    // Get existing lastprice entries
    Map<Integer, GTNetLastprice> lastpriceMap = buildLastpriceMap(
        instrumentMap.values().stream()
            .map(GTNetInstrumentSecurity::getIdGtNetInstrument)
            .collect(Collectors.toList()));

    int updatedCount = 0;

    for (InstrumentPriceDTO dto : validDTOs) {
      String key = dto.getIsin() + "|" + dto.getCurrency();
      GTNetInstrumentSecurity instrument = instrumentMap.get(key);

      // Create instrument if it doesn't exist
      if (instrument == null) {
        instrument = gtNetInstrumentSecurityJpaRepository
            .findOrCreateInstrument(dto.getIsin(), dto.getCurrency());
        instrumentMap.put(key, instrument);
        log.debug("Created new instrument pool entry for security ISIN={}", dto.getIsin());
      }

      // Update lastprice if newer
      if (updateLastpriceIfNewer(instrument, lastpriceMap, dto.getTimestamp(),
          dto.getOpen(), dto.getHigh(), dto.getLow(), dto.getLast(), dto.getVolume())) {
        updatedCount++;
      }
    }

    if (updatedCount > 0) {
      log.info("Updated {} security lastprice entries in push pool from DTOs", updatedCount);
    }

    return updatedCount;
  }

  // ==================== Currencypair Methods ====================

  /**
   * Updates the push pool with currency pair prices from connector fetch.
   *
   * @param currencypairs list of currency pairs with updated prices
   * @return number of prices accepted (created or updated)
   */
  @Transactional
  public int updateCurrencypairLastprices(List<Currencypair> currencypairs) {
    if (currencypairs == null || currencypairs.isEmpty()) {
      return 0;
    }

    // Filter currency pairs that have valid currencies and timestamp
    List<Currencypair> validPairs = currencypairs.stream()
        .filter(cp -> cp.getFromCurrency() != null && !cp.getFromCurrency().isEmpty()
            && cp.getToCurrency() != null && !cp.getToCurrency().isEmpty()
            && cp.getSTimestamp() != null)
        .collect(Collectors.toList());

    if (validPairs.isEmpty()) {
      return 0;
    }

    // Get existing instrument entries from pool
    List<String[]> pairs = validPairs.stream()
        .map(cp -> new String[] { cp.getFromCurrency(), cp.getToCurrency() })
        .collect(Collectors.toList());
    Map<String, GTNetInstrumentCurrencypair> instrumentMap = buildCurrencypairInstrumentMap(pairs);

    // Get existing lastprice entries
    Map<Integer, GTNetLastprice> lastpriceMap = buildLastpriceMap(
        instrumentMap.values().stream()
            .map(GTNetInstrumentCurrencypair::getIdGtNetInstrument)
            .collect(Collectors.toList()));

    int updatedCount = 0;

    for (Currencypair currencypair : validPairs) {
      String key = currencypair.getFromCurrency() + "|" + currencypair.getToCurrency();
      GTNetInstrumentCurrencypair instrument = instrumentMap.get(key);

      // Create instrument if it doesn't exist
      if (instrument == null) {
        instrument = gtNetInstrumentCurrencypairJpaRepository
            .findOrCreateInstrument(currencypair.getFromCurrency(), currencypair.getToCurrency());
        instrumentMap.put(key, instrument);
        log.debug("Created new instrument pool entry for currencypair {}/{}",
            currencypair.getFromCurrency(), currencypair.getToCurrency());
      }

      // Update lastprice if newer (currencypairs don't have volume)
      if (updateLastpriceIfNewer(instrument, lastpriceMap, currencypair.getSTimestamp(),
          currencypair.getSOpen(), currencypair.getSHigh(), currencypair.getSLow(),
          currencypair.getSLast(), null)) {
        updatedCount++;
      }
    }

    if (updatedCount > 0) {
      log.info("Updated {} currencypair lastprice entries in push pool", updatedCount);
    }

    return updatedCount;
  }

  /**
   * Updates the push pool with currency pair prices from DTOs.
   *
   * @param dtos list of currency pair price DTOs
   * @return number of prices accepted (created or updated)
   */
  @Transactional
  public int updateCurrencypairLastpricesFromDTO(List<InstrumentPriceDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return 0;
    }

    // Filter DTOs that have valid currencies and last price
    List<InstrumentPriceDTO> validDTOs = dtos.stream()
        .filter(dto -> dto.getCurrency() != null && dto.getToCurrency() != null && dto.getLast() != null)
        .collect(Collectors.toList());

    if (validDTOs.isEmpty()) {
      return 0;
    }

    // Get existing instrument entries from pool
    List<String[]> pairs = validDTOs.stream()
        .map(dto -> new String[] { dto.getCurrency(), dto.getToCurrency() })
        .collect(Collectors.toList());
    Map<String, GTNetInstrumentCurrencypair> instrumentMap = buildCurrencypairInstrumentMap(pairs);

    // Get existing lastprice entries
    Map<Integer, GTNetLastprice> lastpriceMap = buildLastpriceMap(
        instrumentMap.values().stream()
            .map(GTNetInstrumentCurrencypair::getIdGtNetInstrument)
            .collect(Collectors.toList()));

    int updatedCount = 0;

    for (InstrumentPriceDTO dto : validDTOs) {
      String key = dto.getCurrency() + "|" + dto.getToCurrency();
      GTNetInstrumentCurrencypair instrument = instrumentMap.get(key);

      // Create instrument if it doesn't exist
      if (instrument == null) {
        instrument = gtNetInstrumentCurrencypairJpaRepository
            .findOrCreateInstrument(dto.getCurrency(), dto.getToCurrency());
        instrumentMap.put(key, instrument);
        log.debug("Created new instrument pool entry for currencypair {}/{}",
            dto.getCurrency(), dto.getToCurrency());
      }

      // Update lastprice if newer
      if (updateLastpriceIfNewer(instrument, lastpriceMap, dto.getTimestamp(),
          dto.getOpen(), dto.getHigh(), dto.getLow(), dto.getLast(), dto.getVolume())) {
        updatedCount++;
      }
    }

    if (updatedCount > 0) {
      log.info("Updated {} currencypair lastprice entries in push pool from DTOs", updatedCount);
    }

    return updatedCount;
  }

  // ==================== Helper Methods ====================

  /**
   * Builds a map of existing security instruments by key "ISIN|CURRENCY".
   */
  private Map<String, GTNetInstrumentSecurity> buildSecurityInstrumentMap(List<String[]> pairs) {
    List<GTNetInstrumentSecurity> existing = gtNetInstrumentSecurityJpaRepository.findByIsinCurrencyTuples(pairs);
    return existing.stream()
        .collect(Collectors.toMap(
            e -> e.getIsin() + "|" + e.getCurrency(),
            Function.identity()));
  }

  /**
   * Builds a map of existing currencypair instruments by key "FROM|TO".
   */
  private Map<String, GTNetInstrumentCurrencypair> buildCurrencypairInstrumentMap(List<String[]> pairs) {
    List<GTNetInstrumentCurrencypair> existing = gtNetInstrumentCurrencypairJpaRepository.findByCurrencyTuples(pairs);
    return existing.stream()
        .collect(Collectors.toMap(
            e -> e.getFromCurrency() + "|" + e.getToCurrency(),
            Function.identity()));
  }

  /**
   * Builds a map of existing lastprice entries by instrument ID.
   */
  private Map<Integer, GTNetLastprice> buildLastpriceMap(List<Integer> instrumentIds) {
    if (instrumentIds == null || instrumentIds.isEmpty()) {
      return new HashMap<>();
    }
    return gtNetLastpriceJpaRepository.findByGtNetInstrumentIdGtNetInstrumentIn(instrumentIds).stream()
        .collect(Collectors.toMap(
            lp -> lp.getGtNetInstrument().getIdGtNetInstrument(),
            Function.identity()));
  }

  /**
   * Updates or creates a lastprice entry if the new timestamp is newer.
   *
   * @param instrument the GTNet instrument (security or currencypair)
   * @param lastpriceMap map of existing lastprice entries (will be updated with new entries)
   * @param timestamp the new timestamp
   * @param open the new open price
   * @param high the new high price
   * @param low the new low price
   * @param last the new last price
   * @param volume the new volume (may be null for currencypairs)
   * @return true if the entry was created or updated
   */
  private boolean updateLastpriceIfNewer(GTNetInstrument instrument, Map<Integer, GTNetLastprice> lastpriceMap,
      Date timestamp, Double open, Double high, Double low, Double last, Long volume) {

    Integer instrumentId = instrument.getIdGtNetInstrument();
    GTNetLastprice existing = lastpriceMap.get(instrumentId);

    if (existing == null) {
      // Create new lastprice entry
      GTNetLastprice newLastprice = new GTNetLastprice();
      newLastprice.setGtNetInstrument(instrument);
      updateLastpriceFields(newLastprice, timestamp, open, high, low, last, volume);
      gtNetLastpriceJpaRepository.save(newLastprice);
      lastpriceMap.put(instrumentId, newLastprice);
      return true;
    } else if (isNewer(timestamp, existing.getTimestamp())) {
      // Update existing entry with newer price
      updateLastpriceFields(existing, timestamp, open, high, low, last, volume);
      gtNetLastpriceJpaRepository.save(existing);
      return true;
    }

    return false;
  }

  private void updateLastpriceFields(GTNetLastprice lastprice, Date timestamp,
      Double open, Double high, Double low, Double last, Long volume) {
    lastprice.setTimestamp(timestamp);
    lastprice.setOpen(open);
    lastprice.setHigh(high);
    lastprice.setLow(low);
    lastprice.setLast(last);
    lastprice.setVolume(volume);
  }

  private boolean isNewer(Date newTimestamp, Date existingTimestamp) {
    if (newTimestamp == null) {
      return false;
    }
    if (existingTimestamp == null) {
      return true;
    }
    return newTimestamp.after(existingTimestamp);
  }
}
