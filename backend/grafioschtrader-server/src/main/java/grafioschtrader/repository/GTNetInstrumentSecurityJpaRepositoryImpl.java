package grafioschtrader.repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.Security;
import grafioschtrader.service.GTNetLastpricePoolService;

/**
 * Implementation of custom repository methods for GTNetInstrumentSecurity.
 *
 * Handles batch queries for instrument matching. Price pool updates are delegated to
 * {@link GTNetLastpricePoolService} for unified handling.
 */
public class GTNetInstrumentSecurityJpaRepositoryImpl implements GTNetInstrumentSecurityJpaRepositoryCustom {

  @Autowired
  private GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository;

  @Autowired
  private GTNetLastpricePoolService gtNetLastpricePoolService;

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
  public int updateFromConnectorFetch(List<Security> securities) {
    return gtNetLastpricePoolService.updateSecurityLastprices(securities);
  }

  @Override
  @Transactional
  public GTNetInstrumentSecurity findOrCreateInstrument(String isin, String currency) {
    Optional<GTNetInstrumentSecurity> existing = gtNetInstrumentSecurityJpaRepository
        .findByIsinAndCurrency(isin, currency);

    if (existing.isPresent()) {
      return existing.get();
    }

    // Create new instrument
    GTNetInstrumentSecurity newInstrument = new GTNetInstrumentSecurity();
    newInstrument.setIsin(isin);
    newInstrument.setCurrency(currency);
    return gtNetInstrumentSecurityJpaRepository.save(newInstrument);
  }

}
