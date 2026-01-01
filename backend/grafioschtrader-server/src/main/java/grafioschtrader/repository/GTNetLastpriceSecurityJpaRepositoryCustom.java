package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.GTNetLastpriceSecurity;

public interface GTNetLastpriceSecurityJpaRepositoryCustom {

  /**
   * Queries securities from the push-open pool by batch of ISIN+currency tuples.
   * Uses a single SQL query with dynamic tuple IN clause for efficiency.
   *
   * @param isinCurrencyPairs list of [isin, currency] pairs to query
   * @return list of matching GTNetLastpriceSecurity entities
   */
  List<GTNetLastpriceSecurity> findByIsinCurrencyTuples(List<String[]> isinCurrencyPairs);
}
