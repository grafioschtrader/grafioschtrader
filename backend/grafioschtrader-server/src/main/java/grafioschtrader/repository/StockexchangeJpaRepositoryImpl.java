package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.Stockexchange;

public class StockexchangeJpaRepositoryImpl extends BaseRepositoryImpl<Stockexchange>
    implements StockexchangeJpaRepositoryCustom {

  @Autowired
  StockexchangeJpaRepository stockexchangeJpaRepository;

  @Override
  public Stockexchange saveOnlyAttributes(final Stockexchange stockexchange, final Stockexchange existingEntity,
      final Set<Class<? extends Annotation>> udatePropertyLevelClasses) throws Exception {
    return RepositoryHelper.saveOnlyAttributes(stockexchangeJpaRepository, stockexchange, existingEntity,
        udatePropertyLevelClasses);
  }

}
