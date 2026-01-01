package grafioschtrader.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.GTNetLastpriceSecurity;
import grafioschtrader.entities.Security;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class GTNetLastpriceSecurityJpaRepositoryImpl
    extends GTNetLastpriceSecurityCurrencyService<GTNetLastpriceSecurity, Security>
    implements GTNetLastpriceSecurityJpaRepositoryCustom {

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

}
