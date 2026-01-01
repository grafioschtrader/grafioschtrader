package grafioschtrader.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetLastpriceCurrencypair;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class GTNetLastpriceCurrencypairJpaRepositoryImpl
    extends GTNetLastpriceSecurityCurrencyService<GTNetLastpriceCurrencypair, Currencypair>
    implements GTNetLastpriceCurrencypairJpaRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private GTNetLastpriceCurrencypairJpaRepository gTNetLastpriceCurrencypairRepository;

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

}
