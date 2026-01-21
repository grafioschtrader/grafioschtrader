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

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.entities.GTNetLastprice;

/**
 * Implementation of custom repository methods for GTNetInstrumentCurrencypair.
 *
 * Handles batch queries for instrument matching and coordinates updates between
 * the instrument pool and lastprice tables.
 */
public class GTNetInstrumentCurrencypairJpaRepositoryImpl implements GTNetInstrumentCurrencypairJpaRepositoryCustom {

  private static final Logger log = LoggerFactory.getLogger(GTNetInstrumentCurrencypairJpaRepositoryImpl.class);

  @Autowired
  private GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;

  @Autowired
  private GTNetLastpriceJpaRepository gtNetLastpriceJpaRepository;

  @Override
  public List<GTNetInstrumentCurrencypair> findByCurrencyTuples(List<String[]> currencyPairs) {
    if (currencyPairs == null || currencyPairs.isEmpty()) {
      return Collections.emptyList();
    }

    // Convert tuples to composite keys for JPQL query
    List<String> keys = currencyPairs.stream()
        .filter(pair -> pair[0] != null && pair[1] != null)
        .map(pair -> pair[0] + "|" + pair[1])
        .collect(Collectors.toList());

    if (keys.isEmpty()) {
      return Collections.emptyList();
    }

    return gtNetInstrumentCurrencypairJpaRepository.findByCurrencyPairKeys(keys);
  }

  @Override
  @Transactional
  public int updateFromConnectorFetch(List<Currencypair> currencypairs) {
    if (currencypairs == null || currencypairs.isEmpty()) {
      return 0;
    }

    // Filter currency pairs that have valid currencies and a timestamp
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
    List<GTNetInstrumentCurrencypair> existingInstruments = findByCurrencyTuples(pairs);

    // Build map for quick lookup: "FROM|TO" -> existing instrument
    Map<String, GTNetInstrumentCurrencypair> instrumentMap = existingInstruments.stream()
        .collect(Collectors.toMap(
            e -> e.getFromCurrency() + "|" + e.getToCurrency(),
            Function.identity()));

    // Get existing lastprice entries for these instruments
    List<Integer> instrumentIds = existingInstruments.stream()
        .map(GTNetInstrumentCurrencypair::getIdGtNetInstrument)
        .collect(Collectors.toList());
    Map<Integer, GTNetLastprice> lastpriceMap = gtNetLastpriceJpaRepository
        .findByGtNetInstrumentIdGtNetInstrumentIn(instrumentIds).stream()
        .collect(Collectors.toMap(
            lp -> lp.getGtNetInstrument().getIdGtNetInstrument(),
            Function.identity()));

    int updatedCount = 0;

    for (Currencypair currencypair : validPairs) {
      String key = currencypair.getFromCurrency() + "|" + currencypair.getToCurrency();
      GTNetInstrumentCurrencypair instrument = instrumentMap.get(key);
      Date connectorTimestamp = currencypair.getSTimestamp();

      // Create instrument if it doesn't exist
      if (instrument == null) {
        instrument = findOrCreateInstrument(currencypair.getFromCurrency(), currencypair.getToCurrency());
        instrumentMap.put(key, instrument);
        log.debug("Created new instrument pool entry for currencypair {}/{}",
            currencypair.getFromCurrency(), currencypair.getToCurrency());
      }

      // Find or create lastprice entry
      GTNetLastprice lastprice = lastpriceMap.get(instrument.getIdGtNetInstrument());

      if (lastprice == null) {
        // Create new lastprice entry
        lastprice = createLastpriceFromCurrencypair(instrument, currencypair);
        gtNetLastpriceJpaRepository.save(lastprice);
        lastpriceMap.put(instrument.getIdGtNetInstrument(), lastprice);
        updatedCount++;
        log.debug("Created new lastprice entry for currencypair {}/{}",
            currencypair.getFromCurrency(), currencypair.getToCurrency());
      } else if (lastprice.getTimestamp() == null || connectorTimestamp.after(lastprice.getTimestamp())) {
        // Update existing lastprice entry with newer price
        updateLastpriceFromCurrencypair(lastprice, currencypair);
        gtNetLastpriceJpaRepository.save(lastprice);
        updatedCount++;
        log.debug("Updated lastprice entry for currencypair {}/{}",
            currencypair.getFromCurrency(), currencypair.getToCurrency());
      }
      // else: existing lastprice has newer or equal timestamp, skip
    }

    if (updatedCount > 0) {
      log.info("Updated {} currencypair lastprice entries in push pool from connector fetch", updatedCount);
    }

    return updatedCount;
  }

  @Override
  @Transactional
  public GTNetInstrumentCurrencypair findOrCreateInstrument(String fromCurrency, String toCurrency) {
    Optional<GTNetInstrumentCurrencypair> existing = gtNetInstrumentCurrencypairJpaRepository
        .findByFromCurrencyAndToCurrency(fromCurrency, toCurrency);

    if (existing.isPresent()) {
      return existing.get();
    }

    // Create new instrument
    GTNetInstrumentCurrencypair newInstrument = new GTNetInstrumentCurrencypair();
    newInstrument.setFromCurrency(fromCurrency);
    newInstrument.setToCurrency(toCurrency);
    return gtNetInstrumentCurrencypairJpaRepository.save(newInstrument);
  }

  private GTNetLastprice createLastpriceFromCurrencypair(GTNetInstrumentCurrencypair instrument,
      Currencypair currencypair) {
    GTNetLastprice lastprice = new GTNetLastprice();
    lastprice.setGtNetInstrument(instrument);
    updateLastpriceFromCurrencypair(lastprice, currencypair);
    return lastprice;
  }

  private void updateLastpriceFromCurrencypair(GTNetLastprice lastprice, Currencypair currencypair) {
    lastprice.setTimestamp(currencypair.getSTimestamp());
    lastprice.setOpen(currencypair.getSOpen());
    lastprice.setHigh(currencypair.getSHigh());
    lastprice.setLow(currencypair.getSLow());
    lastprice.setLast(currencypair.getSLast());
    // Currencypair does not have volume
  }

}
