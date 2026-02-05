package grafioschtrader.repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.service.GTNetLastpricePoolService;

/**
 * Implementation of custom repository methods for GTNetInstrumentCurrencypair.
 *
 * Handles batch queries for instrument matching. Price pool updates are delegated to
 * {@link GTNetLastpricePoolService} for unified handling.
 */
public class GTNetInstrumentCurrencypairJpaRepositoryImpl implements GTNetInstrumentCurrencypairJpaRepositoryCustom {

  @Autowired
  private GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;

  @Autowired
  private GTNetLastpricePoolService gtNetLastpricePoolService;

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
    return gtNetLastpricePoolService.updateCurrencypairLastprices(currencypairs);
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

}
