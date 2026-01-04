package grafioschtrader.repository;

import java.util.ArrayList;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Implementation of custom repository methods for GTNetInstrumentCurrencypair.
 *
 * Handles batch queries for instrument matching and coordinates updates between
 * the instrument pool and lastprice tables.
 */
public class GTNetInstrumentCurrencypairJpaRepositoryImpl implements GTNetInstrumentCurrencypairJpaRepositoryCustom {

  private static final Logger log = LoggerFactory.getLogger(GTNetInstrumentCurrencypairJpaRepositoryImpl.class);

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;

  @Autowired
  private GTNetLastpriceJpaRepository gtNetLastpriceJpaRepository;

  @Override
  @SuppressWarnings("unchecked")
  public List<GTNetInstrumentCurrencypair> findByCurrencyTuples(List<String[]> currencyPairs) {
    if (currencyPairs == null || currencyPairs.isEmpty()) {
      return Collections.emptyList();
    }

    // Build dynamic SQL with tuple IN clause: WHERE (from_currency, to_currency) IN (('EUR','USD'), ('CHF','EUR'), ...)
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT c.* FROM gt_net_instrument i ");
    sql.append("JOIN gt_net_instrument_currencypair c ON i.id_gt_net_instrument = c.id_gt_net_instrument ");
    sql.append("WHERE (c.from_currency, c.to_currency) IN (");

    List<Object> params = new ArrayList<>();
    for (int i = 0; i < currencyPairs.size(); i++) {
      if (i > 0) {
        sql.append(", ");
      }
      sql.append("(?, ?)");
      params.add(currencyPairs.get(i)[0]); // fromCurrency
      params.add(currencyPairs.get(i)[1]); // toCurrency
    }
    sql.append(")");

    var query = entityManager.createNativeQuery(sql.toString(), GTNetInstrumentCurrencypair.class);
    for (int i = 0; i < params.size(); i++) {
      query.setParameter(i + 1, params.get(i));
    }

    return query.getResultList();
  }

  @Override
  @Transactional
  public int updateFromConnectorFetch(List<Currencypair> currencypairs, Integer idGtNet) {
    if (currencypairs == null || currencypairs.isEmpty() || idGtNet == null) {
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
        instrument = findOrCreateInstrument(currencypair.getFromCurrency(), currencypair.getToCurrency(),
            currencypair.getIdSecuritycurrency(), idGtNet);
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
  public GTNetInstrumentCurrencypair findOrCreateInstrument(String fromCurrency, String toCurrency,
      Integer idSecuritycurrency, Integer idGtNet) {
    Optional<GTNetInstrumentCurrencypair> existing = gtNetInstrumentCurrencypairJpaRepository
        .findByIdGtNetAndFromCurrencyAndToCurrency(idGtNet, fromCurrency, toCurrency);

    if (existing.isPresent()) {
      GTNetInstrumentCurrencypair instrument = existing.get();
      // Update idSecuritycurrency if it was null and now we have a local reference
      if (instrument.getIdSecuritycurrency() == null && idSecuritycurrency != null) {
        instrument.setIdSecuritycurrency(idSecuritycurrency);
        return gtNetInstrumentCurrencypairJpaRepository.save(instrument);
      }
      return instrument;
    }

    // Create new instrument
    GTNetInstrumentCurrencypair newInstrument = new GTNetInstrumentCurrencypair();
    newInstrument.setIdGtNet(idGtNet);
    newInstrument.setFromCurrency(fromCurrency);
    newInstrument.setToCurrency(toCurrency);
    newInstrument.setIdSecuritycurrency(idSecuritycurrency);
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
