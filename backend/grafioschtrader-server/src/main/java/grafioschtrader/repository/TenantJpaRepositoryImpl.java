package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.codec.binary.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.TaskDataChange;
import grafiosch.entities.TenantBase;
import grafiosch.entities.User;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.repository.TenantBaseImpl;
import grafiosch.repository.UserJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TenantKindType;
import grafioschtrader.entities.Tenant;
import grafioschtrader.types.TaskTypeExtended;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class TenantJpaRepositoryImpl extends TenantBaseImpl<Tenant> implements TenantJpaRepositoryCustom {

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

 
  
  @Override
  @Transactional
  @Modifying
  public Tenant removeAllWatchlistByIdTenant(final Integer idTenant) {
    em.createQuery("DELETE FROM Watchlist w WHERE idTenant = ?1").setParameter(1, idTenant).executeUpdate();
    return tenantJpaRepository.getReferenceById(idTenant);
  }

  @Override
  @Transactional
  @Modifying
  public Tenant removeAllPortfolios(final Integer idTenant) {
    em.createQuery("DELETE FROM Portfolio p WHERE idTenant = ?1").setParameter(1, idTenant).executeUpdate();
    return tenantJpaRepository.getReferenceById(idTenant);
  }

  @Override
  @Transactional
  @Modifying
  public Tenant attachWatchlist(final Integer idTenant) {
    Tenant tenantFull = tenantJpaRepository.getReferenceById(idTenant);
    tenantFull = em.merge(tenantFull);
    tenantFull.getWatchlistList().size();
    return tenantFull;
  }

  @Override
  @Transactional
  @Modifying
  public Tenant saveOnlyAttributes(final Tenant tenant, Tenant existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    Tenant createEditTenant = tenant;
    boolean currencyChanged = existingEntity == null
        || !StringUtils.equals(existingEntity.getCurrency(), tenant.getCurrency());
    User user = null;
    if (tenant.getIdTenant() != null) {
      createEditTenant = tenantJpaRepository.getReferenceById(tenant.getIdTenant());
      createEditTenant.updateThis(tenant);
    } else {
      // Attach tenant to existing user
      user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
      createEditTenant.setCreateIdUser(user.getIdUser());
      createEditTenant.setTenantKindType(TenantKindType.MAIN);
    }
    final Tenant teantNew = tenantJpaRepository.save(createEditTenant);

    if (user != null && user.getIdTenant() == null) {
      user.setIdTenant(teantNew.getIdTenant());
      userJpaRepository.save(user);
    }
    if (currencyChanged) {
      // Holding tables recreation
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO,
          TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now(), teantNew.getIdTenant(), Tenant.class.getSimpleName()));
    }
    return teantNew;
  }

  @Override
  public boolean isExcludeDividendTaxcost() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    final Tenant tenant = tenantJpaRepository.getReferenceById(((User) authentication.getDetails()).getIdTenant());
    return (tenant.isExcludeDivTax());
  }

 

  @Override
  @Transactional
  public Optional<Tenant> createNotExistingCurrencypairs(Integer idTenant) {
    Optional<Tenant> tenantOpt = tenantJpaRepository.findById(idTenant);
    if (tenantOpt.isPresent()) {
      Tenant tenant = tenantOpt.get();
      Set<String> existingFromCurrency = currencypairJpaRepository.getFromCurrencyByToCurrency(tenant.getCurrency());
      Set<String> requiredFromCurrency = new HashSet<>();
      tenant.getPortfolioList().forEach(p -> requiredFromCurrency.add(p.getCurrency()));
      requiredFromCurrency.addAll(
          currencypairJpaRepository.getSecurityTransactionCurrenciesForTenantExclude(idTenant, tenant.getCurrency()));
      requiredFromCurrency.remove(tenant.getCurrency());
      requiredFromCurrency.removeAll(existingFromCurrency);
      requiredFromCurrency.forEach(fromCurrency -> currencypairJpaRepository.createNonExistingCurrencypair(fromCurrency,
          tenant.getCurrency(), false));
    }
    return tenantOpt;
  }

  @Override
  @Transactional
  public Tenant changeCurrencyTenantAndPortfolios(String currency) {
    Currency.getAvailableCurrencies().contains(Currency.getInstance(currency));
    final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();
    final Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    tenant.setCurrency(currency);
    tenant.getPortfolioList().forEach(p -> p.setCurrency(currency));
    tenantJpaRepository.save(tenant);
    taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.CURRENCY_CHANGED_ON_TENANT_AND_PORTFOLIO,
        TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now(), tenant.getIdTenant(), TenantBase.TABNAME));
    return tenant;
  }

  @Override
  public Tenant setWatchlistForPerformance(Integer idWatchlist) {
    final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();
    final Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    tenant.setIdWatchlistPerformance(idWatchlist);
    tenantJpaRepository.save(tenant);
    return tenant;
  }

 
  

  

}
