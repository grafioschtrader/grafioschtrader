package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.StockexchangeJpaRepository.IdStockexchangeIndexName;

public class StockexchangeJpaRepositoryImpl extends BaseRepositoryImpl<Stockexchange>
    implements StockexchangeJpaRepositoryCustom {

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Override
  public Stockexchange saveOnlyAttributes(final Stockexchange stockexchange, final Stockexchange existingEntity,
      final Set<Class<? extends Annotation>> udatePropertyLevelClasses) throws Exception {
    return RepositoryHelper.saveOnlyAttributes(stockexchangeJpaRepository, stockexchange, existingEntity,
        udatePropertyLevelClasses);
  }

  @Override
  public List<Stockexchange> getAllStockExchanges(boolean includeNameOfCalendarIndex) {
    List<Stockexchange> stockexchanes = stockexchangeJpaRepository.findAllByOrderByNameAsc();
    if (includeNameOfCalendarIndex) {
      Map<Integer, String> idSecurtyMap = stockexchangeJpaRepository.getIdStockexchangeAndIndexNameForCalendarUpd().stream()
          .collect(Collectors.toMap(IdStockexchangeIndexName::getIdStockexchange, e -> e.getNameIndexSecurity()));
      stockexchanes.forEach(se -> se.setNameIndexUpdCalendar(idSecurtyMap.get(se.getIdStockexchange())));
    }

    return stockexchanes;
  }

}
