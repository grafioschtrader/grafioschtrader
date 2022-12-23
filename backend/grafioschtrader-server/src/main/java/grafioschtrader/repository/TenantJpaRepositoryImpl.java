package grafioschtrader.repository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.codec.binary.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
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
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;
import grafioschtrader.types.TenantKindType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletResponse;

public class TenantJpaRepositoryImpl extends BaseRepositoryImpl<Tenant> implements TenantJpaRepositoryCustom {

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

  @Autowired
  private ResourceLoader resourceLoader;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Value("${gt.demo.account.pattern.de}")
  private String demoAccountPatternDE;

  @Value("${gt.demo.account.pattern.en}")
  private String demoAccountPatternEN;

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
      // Holdling tables recreation
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskType.CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO,
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
  public void deleteMyDataAndUserAccount() throws Exception {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    RestHelper.isDemoAccount(demoAccountPatternDE, user.getUsername());
    RestHelper.isDemoAccount(demoAccountPatternEN, user.getUsername());

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
    final Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    tenant.setCurrency(currency);
    tenant.getPortfolioList().forEach(p -> p.setCurrency(currency));
    tenantJpaRepository.save(tenant);
    taskDataChangeJpaRepository.save(new TaskDataChange(TaskType.CURRENCY_CHANGED_ON_TENANT_AND_PORTFOLIO,
        TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now(), tenant.getIdTenant(), Tenant.TABNAME));
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

  @Override
  public void getExportPersonalDataAsZip(HttpServletResponse response) throws Exception {
    String ddlFileName = "gt_ddl.sql";
    Resource resourceDdl = resourceLoader.getResource("classpath:db/migration/" + ddlFileName);

    StringBuilder sqlStatement = new MySqlExportMyData(jdbcTemplate).exportDataMyData();

    // setting headers
    response.setStatus(HttpServletResponse.SC_OK);
    response.addHeader("Content-Disposition", "attachment; filename=\"gt.zip\"");

    ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream());
    addZipEntry(zipOutputStream, resourceDdl.getInputStream(), ddlFileName);
    InputStream dmlInputStream = new ByteArrayInputStream(sqlStatement.toString().getBytes());
    addZipEntry(zipOutputStream, dmlInputStream, "gt_data.sql");
    zipOutputStream.close();

  }

  private void addZipEntry(ZipOutputStream zos, InputStream in, String entryName) throws IOException {
    byte buffer[] = new byte[16384];
    zos.putNextEntry(new ZipEntry(entryName));
    int length;
    while ((length = in.read(buffer)) >= 0) {
      zos.write(buffer, 0, length);
    }
    in.close();
    zos.closeEntry();
  }

}
