package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.GTNetLastpriceSecurity;

public interface GTNetLastpriceSecurityJpaRepository
    extends JpaRepository<GTNetLastpriceSecurity, Integer>, GTNetLastpriceSecurityJpaRepositoryCustom {

  @Query(value = """
      SELECT s.*, p.* FROM gt_net_lastprice_security s JOIN gt_net_lastprice p ON p.id_gt_net_lastprice = s.id_gt_net_lastprice 
      WHERE (s.isin, s.currency) IN ((?1, ?2))""",  nativeQuery = true)
  List <GTNetLastpriceSecurity> getLastpricesByListByIsinsAndCurrencies(List<String> isins, List<String> currencies);
}
