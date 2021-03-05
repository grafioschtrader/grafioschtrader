package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.commons.codec.binary.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.User;
import grafioschtrader.exportdelete.MySqlDeleteMyData;
import grafioschtrader.exportdelete.MySqlExportMyData;
import grafioschtrader.rest.helper.RestHelper;
import grafioschtrader.types.TaskType;
import grafioschtrader.types.TenantKindType;

public class TenantJpaRepositoryImpl extends BaseRepositoryImpl<Tenant> implements TenantJpaRepositoryCustom {

  @PersistenceContext
  private EntityManager em;

  @Autowired
  TenantJpaRepository tenantJpaRepository;

  @Autowired
  UserJpaRepository userJpaRepository;

  @Autowired
  TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  JdbcTemplate jdbcTemplate;
  
  @Value("${gt.demo.account.pattern}")
  private String demoAccountPattern;
 

  @Override
  @Transactional
  @Modifying
  public Tenant removeAllWatchlistByIdTenant(final Integer idTenant) {
    em.createQuery("DELETE FROM Watchlist w WHERE idTenant = ?1").setParameter(1, idTenant).executeUpdate();
    return tenantJpaRepository.getOne(idTenant);
  }

  @Override
  @Transactional
  @Modifying
  public Tenant removeAllPortfolios(final Integer idTenant) {
    em.createQuery("DELETE FROM Portfolio p WHERE idTenant = ?1").setParameter(1, idTenant).executeUpdate();
    return tenantJpaRepository.getOne(idTenant);
  }

  @Override
  @Transactional
  @Modifying
  public Tenant attachWatchlist(final Integer idTenant) {
    Tenant tenantFull = tenantJpaRepository.getOne(idTenant);
    tenantFull = em.merge(tenantFull);
    tenantFull.getWatchlistList().size();
    return tenantFull;
  }

  @Override
  @Transactional
  @Modifying
  public Tenant saveOnlyAttributes(final Tenant tenant, Tenant existingEntity,
      final Set<Class<? extends Annotation>> udatePropertyLevelClasses) {
    Tenant createEditTenant = tenant;
    boolean currencyChanged = existingEntity == null
        || !StringUtils.equals(existingEntity.getCurrency(), tenant.getCurrency());
    User user = null;
    if (tenant.getIdTenant() != null) {
      createEditTenant = tenantJpaRepository.getOne(tenant.getIdTenant());
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
      // Holdling tables recreation
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskType.CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO, (short) 22,
          LocalDateTime.now(), teantNew.getIdTenant(), Tenant.TABNAME));
    }

    return teantNew;
  }

  @Override
  public boolean isExcludeDividendTaxcost() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    final Tenant tenant = tenantJpaRepository.getOne(((User) authentication.getDetails()).getIdTenant());
    return (tenant.isExcludeDivTax());
  }

  @Override
  public StringBuilder exportPersonalData() throws Exception {
    MySqlExportMyData mySqlExportMyData = new MySqlExportMyData(jdbcTemplate);
    return mySqlExportMyData.exportDataMyData();
  }

  @Override
  public void deleteMyDataAndUserAccount() throws Exception {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    RestHelper.isDemoAccount(demoAccountPattern, user.getUsername());
    
    MySqlDeleteMyData mySqlDeleteMyData = new MySqlDeleteMyData(jdbcTemplate);
    mySqlDeleteMyData.deleteMyData();
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
    final Tenant tenant = tenantJpaRepository.getOne(idTenant);
    tenant.setCurrency(currency);
    tenant.getPortfolioList().forEach(p -> p.setCurrency(currency));
    tenantJpaRepository.save(tenant);
    taskDataChangeJpaRepository.save(new TaskDataChange(TaskType.CURRENCY_CHANGED_ON_TENANT_AND_PORTFOLIO, (short) 22,
        LocalDateTime.now(), tenant.getIdTenant(), Tenant.TABNAME));
    return tenant;
  }
  
  @Override
  public Tenant setWatchlistForPerformance(Integer idWatchlist) {
    final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();
    final Tenant tenant = tenantJpaRepository.getOne(idTenant);
    tenant.setIdWatchlistPerformance(idWatchlist);
    tenantJpaRepository.save(tenant);
    return tenant;
  }
  

}
