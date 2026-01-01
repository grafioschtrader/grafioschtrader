package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.GTNetLastpriceCurrencypair;

public interface GTNetLastpriceCurrencypairJpaRepositoryCustom {

  /**
   * Queries currency pairs from the push-open pool by batch of fromCurrency+toCurrency tuples.
   * Uses a single SQL query with dynamic tuple IN clause for efficiency.
   *
   * @param currencyPairs list of [fromCurrency, toCurrency] pairs to query
   * @return list of matching GTNetLastpriceCurrencypair entities
   */
  List<GTNetLastpriceCurrencypair> findByCurrencyTuples(List<String[]> currencyPairs);
}
