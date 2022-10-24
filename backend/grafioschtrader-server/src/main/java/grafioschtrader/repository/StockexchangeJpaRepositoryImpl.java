package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.dto.StockexchangeBaseData;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.StockexchangeMic;
import grafioschtrader.repository.StockexchangeJpaRepository.IdStockexchangeIndexName;

public class StockexchangeJpaRepositoryImpl extends BaseRepositoryImpl<Stockexchange>
    implements StockexchangeJpaRepositoryCustom {

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private StockexchangeMicJpaRepository stockexchangeMicJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Override
  public Stockexchange saveOnlyAttributes(final Stockexchange stockexchange, final Stockexchange existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {

    if (stockexchange.getMic() != null) {
      Optional<StockexchangeMic> stockexchangeMic = stockexchangeMicJpaRepository
          .findByMicAndCountryCode(stockexchange.getMic(), stockexchange.getCountryCode());
      if(!stockexchangeMic.isPresent()) {
        throw new IllegalArgumentException("The combination of MIC and Country does not exists!");
      }
    }

    return RepositoryHelper.saveOnlyAttributes(stockexchangeJpaRepository, stockexchange, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  public List<Stockexchange> getAllStockExchanges(boolean includeNameOfCalendarIndex) {
    List<Stockexchange> stockexchanes = stockexchangeJpaRepository.findAllByOrderByNameAsc();
    if (includeNameOfCalendarIndex) {
      Map<Integer, String> idSecurtyMap = stockexchangeJpaRepository.getIdStockexchangeAndIndexNameForCalendarUpd()
          .stream()
          .collect(Collectors.toMap(IdStockexchangeIndexName::getIdStockexchange, e -> e.getNameIndexSecurity()));
      stockexchanes.forEach(se -> se.setNameIndexUpdCalendar(idSecurtyMap.get(se.getIdStockexchange())));
    }
    return stockexchanes;
  }

  @Override
  public StockexchangeBaseData getAllStockexchangesBaseData() {
    return new StockexchangeBaseData(getAllStockExchanges(true), stockexchangeJpaRepository.stockexchangesHasSecurity(),
        stockexchangeMicJpaRepository.findAll(), globalparametersJpaRepository.getCountriesForSelectBox());
  }
}
