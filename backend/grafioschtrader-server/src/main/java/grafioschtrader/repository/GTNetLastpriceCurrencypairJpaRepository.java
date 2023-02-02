package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.GTNetLastpriceCurrencypair;

public interface GTNetLastpriceCurrencypairJpaRepository
    extends JpaRepository<GTNetLastpriceCurrencypair, Integer>, GTNetLastpriceCurrencypairJpaRepositoryCustom {

  @Query(value = """
      SELECT c.*, p.* FROM gt_net_lastprice_currencypair c JOIN gt_net_lastprice p ON c.id_gt_net_lastprice = p.id_gt_net_lastprice
      WHERE (c.from_currency, c.to_currency) IN ((?1, ?2))""", nativeQuery = true)
  List<GTNetLastpriceCurrencypair> getLastpricesByListByFromAndToCurrencies(List<String> fromCurrencies,
      List<String> toCurrencies);

}
