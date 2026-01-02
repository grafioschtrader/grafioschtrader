package grafioschtrader.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetLastpriceCurrencypair;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class GTNetLastpriceCurrencypairJpaRepositoryImpl
    extends GTNetLastpriceSecurityCurrencyService<GTNetLastpriceCurrencypair, Currencypair>
    implements GTNetLastpriceCurrencypairJpaRepositoryCustom {

  private static final Logger log = LoggerFactory.getLogger(GTNetLastpriceCurrencypairJpaRepositoryImpl.class);

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private GTNetLastpriceCurrencypairJpaRepository gtNetLastpriceCurrencypairJpaRepository;

  @Override
  @SuppressWarnings("unchecked")
  public List<GTNetLastpriceCurrencypair> findByCurrencyTuples(List<String[]> currencyPairs) {
    if (currencyPairs == null || currencyPairs.isEmpty()) {
      return Collections.emptyList();
    }

    // Build dynamic SQL with tuple IN clause: WHERE (from_currency, to_currency) IN (('EUR','USD'), ('CHF','EUR'), ...)
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT c.*, p.* FROM gt_net_lastprice_currencypair c ");
    sql.append("JOIN gt_net_lastprice p ON c.id_gt_net_lastprice = p.id_gt_net_lastprice ");
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

    var query = entityManager.createNativeQuery(sql.toString(), GTNetLastpriceCurrencypair.class);
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

    // Get existing entries from push pool
    List<String[]> pairs = validPairs.stream()
        .map(cp -> new String[] { cp.getFromCurrency(), cp.getToCurrency() })
        .collect(Collectors.toList());
    List<GTNetLastpriceCurrencypair> existingEntries = findByCurrencyTuples(pairs);

    // Build map for quick lookup: "FROM|TO" -> existing entry
    Map<String, GTNetLastpriceCurrencypair> existingMap = existingEntries.stream()
        .collect(Collectors.toMap(
            e -> e.getFromCurrency() + "|" + e.getToCurrency(),
            Function.identity()));

    int updatedCount = 0;

    for (Currencypair currencypair : validPairs) {
      String key = currencypair.getFromCurrency() + "|" + currencypair.getToCurrency();
      GTNetLastpriceCurrencypair existing = existingMap.get(key);
      Date connectorTimestamp = currencypair.getSTimestamp();

      if (existing == null) {
        // Create new entry
        GTNetLastpriceCurrencypair newEntry = createFromCurrencypair(currencypair, idGtNet);
        gtNetLastpriceCurrencypairJpaRepository.save(newEntry);
        updatedCount++;
        log.debug("Created new push pool entry for currencypair {}/{}",
            currencypair.getFromCurrency(), currencypair.getToCurrency());
      } else if (existing.getTimestamp() == null
          || connectorTimestamp.after(existing.getTimestamp())) {
        // Update existing entry with newer price
        updateFromCurrencypair(existing, currencypair);
        gtNetLastpriceCurrencypairJpaRepository.save(existing);
        updatedCount++;
        log.debug("Updated push pool entry for currencypair {}/{}",
            currencypair.getFromCurrency(), currencypair.getToCurrency());
      }
      // else: existing entry has newer or equal timestamp, skip
    }

    if (updatedCount > 0) {
      log.info("Updated {} currencypair entries in push pool from connector fetch", updatedCount);
    }

    return updatedCount;
  }

  private GTNetLastpriceCurrencypair createFromCurrencypair(Currencypair currencypair, Integer idGtNet) {
    GTNetLastpriceCurrencypair entry = new GTNetLastpriceCurrencypair();
    entry.setIdGtNet(idGtNet);
    entry.setFromCurrency(currencypair.getFromCurrency());
    entry.setToCurrency(currencypair.getToCurrency());
    updateFromCurrencypair(entry, currencypair);
    return entry;
  }

  private void updateFromCurrencypair(GTNetLastpriceCurrencypair entry, Currencypair currencypair) {
    entry.setTimestamp(currencypair.getSTimestamp());
    entry.setOpen(currencypair.getSOpen());
    entry.setHigh(currencypair.getSHigh());
    entry.setLow(currencypair.getSLow());
    entry.setLast(currencypair.getSLast());
    // Currencypair does not have volume
  }

}
