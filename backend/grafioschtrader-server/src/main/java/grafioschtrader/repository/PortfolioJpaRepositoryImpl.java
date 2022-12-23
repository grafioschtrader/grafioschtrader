package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.codec.binary.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class PortfolioJpaRepositoryImpl extends BaseRepositoryImpl<Portfolio> implements PortfolioJpaRepositoryCustom {

  // TODO Remove EntityManager and use Spring Data for queries
  @PersistenceContext
  private EntityManager em;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  // @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  public void setCurrencypairJpaRepository(@Lazy final CurrencypairJpaRepository currencypairJpaRepository) {
    this.currencypairJpaRepository = currencypairJpaRepository;
  }

  @Override
  @Transactional
  @Modifying
  public Portfolio removeCashaccounts(final Integer idPortfolio) {
    em.createQuery("DELETE FROM Cashaccount c WHERE c.portfolio.idPortfolio = ?1").setParameter(1, idPortfolio)
        .executeUpdate();
    return portfolioJpaRepository.getReferenceById(idPortfolio);
  }

  @Override
  @Transactional
  @Modifying
  public Portfolio removeSecurityaccounts(final Integer idPortfolio) {
    em.createQuery("DELETE FROM Securityaccount s WHERE s.portfolio.idPortfolio = ?1").setParameter(1, idPortfolio)
        .executeUpdate();
    return portfolioJpaRepository.getReferenceById(idPortfolio);
  }

  @Override
  public List<Portfolio> setExistingTransactionOnSecurityaccount(Integer idTenant) {
    List<Portfolio> portfolios = portfolioJpaRepository.findByIdTenantOrderByName(idTenant);
    final List<Integer> idsSecurityaccount = portfolioJpaRepository.getExistingTransactionOnSecurityaccount(idTenant);
    portfolios.forEach(
        p -> p.getSecurityaccountList().stream().filter(s -> idsSecurityaccount.contains(s.getIdSecuritycashAccount()))
            .findFirst().ifPresent(s -> s.setHasTransaction(true)));
    return portfolios;
  }

  @Override
  public Portfolio saveOnlyAttributes(final Portfolio portfolio, Portfolio existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {

    boolean currencyChanged = existingEntity == null
        || !StringUtils.equals(existingEntity.getCurrency(), portfolio.getCurrency());

    Portfolio portfolioNew = RepositoryHelper.saveOnlyAttributes(portfolioJpaRepository, portfolio, existingEntity,
        updatePropertyLevelClasses);

    if (currencyChanged) {
      taskDataChangeJpaRepository
          .save(new TaskDataChange(TaskType.CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO, TaskDataExecPriority.PRIO_NORMAL,
              LocalDateTime.now(), portfolioNew.getIdPortfolio(), Portfolio.class.getSimpleName()));
    }

    return portfolioNew;
  }

  @Override
  public int delEntityWithTenant(Integer id, Integer idTenant) {
    return portfolioJpaRepository.deleteByIdPortfolioAndIdTenant(id, idTenant);
  }

  @Override
  @Transactional
  public Integer createNotExistingCurrencypairs(Integer idPortfolio) {
    Optional<Portfolio> portfolioOpt = portfolioJpaRepository.findById(idPortfolio);
    if (portfolioOpt.isPresent()) {
      Portfolio portfolio = portfolioOpt.get();

      Set<String> existingFromCurrency = currencypairJpaRepository.getFromCurrencyByToCurrency(portfolio.getCurrency());
      Set<String> requiredFromCurrency = new HashSet<>();
      Tenant tenant = tenantJpaRepository.getReferenceById(portfolio.getIdTenant());
      requiredFromCurrency.add(tenant.getCurrency());
      requiredFromCurrency.addAll(currencypairJpaRepository
          .getSecurityTransactionCurrenciesForPortfolioExclude(idPortfolio, portfolio.getCurrency()));
      requiredFromCurrency.remove(portfolio.getCurrency());
      requiredFromCurrency.removeAll(existingFromCurrency);
      requiredFromCurrency.forEach(fromCurrency -> currencypairJpaRepository.createNonExistingCurrencypair(fromCurrency,
          portfolio.getCurrency(), false));
      return portfolio.getIdTenant();
    }
    return null;
  }

}
