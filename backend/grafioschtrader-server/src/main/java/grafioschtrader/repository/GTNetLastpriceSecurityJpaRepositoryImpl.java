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

import grafioschtrader.entities.GTNetLastpriceSecurity;
import grafioschtrader.entities.Security;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class GTNetLastpriceSecurityJpaRepositoryImpl
    extends GTNetLastpriceSecurityCurrencyService<GTNetLastpriceSecurity, Security>
    implements GTNetLastpriceSecurityJpaRepositoryCustom {

  private static final Logger log = LoggerFactory.getLogger(GTNetLastpriceSecurityJpaRepositoryImpl.class);

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private GTNetLastpriceSecurityJpaRepository gtNetLastpriceSecurityJpaRepository;

  protected List<GTNetLastpriceSecurity> readUpdateGetLastpriceValues(List<Security> securities) {
    List<String[]> pairs = securities.stream()
        .map(s -> new String[] { s.getIsin(), s.getCurrency() })
        .collect(Collectors.toList());
    return findByIsinCurrencyTuples(pairs);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<GTNetLastpriceSecurity> findByIsinCurrencyTuples(List<String[]> isinCurrencyPairs) {
    if (isinCurrencyPairs == null || isinCurrencyPairs.isEmpty()) {
      return Collections.emptyList();
    }

    // Build dynamic SQL with tuple IN clause: WHERE (isin, currency) IN (('US123','USD'), ('DE456','EUR'), ...)
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT s.*, p.* FROM gt_net_lastprice_security s ");
    sql.append("JOIN gt_net_lastprice p ON p.id_gt_net_lastprice = s.id_gt_net_lastprice ");
    sql.append("WHERE (s.isin, s.currency) IN (");

    List<Object> params = new ArrayList<>();
    for (int i = 0; i < isinCurrencyPairs.size(); i++) {
      if (i > 0) {
        sql.append(", ");
      }
      sql.append("(?, ?)");
      params.add(isinCurrencyPairs.get(i)[0]); // isin
      params.add(isinCurrencyPairs.get(i)[1]); // currency
    }
    sql.append(")");

    var query = entityManager.createNativeQuery(sql.toString(), GTNetLastpriceSecurity.class);
    for (int i = 0; i < params.size(); i++) {
      query.setParameter(i + 1, params.get(i));
    }

    return query.getResultList();
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

    // Get existing entries from push pool
    List<String[]> pairs = validSecurities.stream()
        .map(s -> new String[] { s.getIsin(), s.getCurrency() })
        .collect(Collectors.toList());
    List<GTNetLastpriceSecurity> existingEntries = findByIsinCurrencyTuples(pairs);

    // Build map for quick lookup: "ISIN|CURRENCY" -> existing entry
    Map<String, GTNetLastpriceSecurity> existingMap = existingEntries.stream()
        .collect(Collectors.toMap(
            e -> e.getIsin() + "|" + e.getCurrency(),
            Function.identity()));

    int updatedCount = 0;

    for (Security security : validSecurities) {
      String key = security.getIsin() + "|" + security.getCurrency();
      GTNetLastpriceSecurity existing = existingMap.get(key);
      Date connectorTimestamp = security.getSTimestamp();

      if (existing == null) {
        // Create new entry
        GTNetLastpriceSecurity newEntry = createFromSecurity(security, idGtNet);
        gtNetLastpriceSecurityJpaRepository.save(newEntry);
        updatedCount++;
        log.debug("Created new push pool entry for security ISIN={}", security.getIsin());
      } else if (existing.getTimestamp() == null
          || connectorTimestamp.after(existing.getTimestamp())) {
        // Update existing entry with newer price
        updateFromSecurity(existing, security);
        gtNetLastpriceSecurityJpaRepository.save(existing);
        updatedCount++;
        log.debug("Updated push pool entry for security ISIN={}", security.getIsin());
      }
      // else: existing entry has newer or equal timestamp, skip
    }

    if (updatedCount > 0) {
      log.info("Updated {} security entries in push pool from connector fetch", updatedCount);
    }

    return updatedCount;
  }

  private GTNetLastpriceSecurity createFromSecurity(Security security, Integer idGtNet) {
    GTNetLastpriceSecurity entry = new GTNetLastpriceSecurity();
    entry.setIdGtNet(idGtNet);
    entry.setIsin(security.getIsin());
    entry.setCurrency(security.getCurrency());
    updateFromSecurity(entry, security);
    return entry;
  }

  private void updateFromSecurity(GTNetLastpriceSecurity entry, Security security) {
    entry.setTimestamp(security.getSTimestamp());
    entry.setOpen(security.getSOpen());
    entry.setHigh(security.getSHigh());
    entry.setLow(security.getSLow());
    entry.setLast(security.getSLast());
    entry.setVolume(security.getSVolume());
  }

}
