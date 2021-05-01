package grafioschtrader.repository;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.io.IOUtils;
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
import grafioschtrader.types.TaskType;
import grafioschtrader.types.TenantKindType;

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
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
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

  public void getExportPersonalDataAsZip(HttpServletResponse response) throws Exception {
    Resource resource = resourceLoader.getResource("classpath:/db/migration/gt_ddl.sql");

    StringBuilder sqlStatement = new MySqlExportMyData(jdbcTemplate).exportDataMyData();

    File tempSqlStatement = File.createTempFile("gt_data", ".sql");

    // Delete temp file when program exits.
    tempSqlStatement.deleteOnExit();

    // Write to temp file
    try (BufferedWriter out = new BufferedWriter(new FileWriter(tempSqlStatement))) {
      out.write(sqlStatement.toString());

    } catch (IOException e) {
      throw e;
    }

    // setting headers
    response.setStatus(HttpServletResponse.SC_OK);
    response.addHeader("Content-Disposition", "attachment; filename=\"gt.zip\"");

    ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream());

    // create a list to add files to be zipped
    ArrayList<File> files = new ArrayList<>(1);
    files.add(resource.getFile());
    files.add(tempSqlStatement);

    // package files
    for (File file : files) {
      // new zip entry and copying inputstream with file to zipOutputStream, after all
      // closing streams
      zipOutputStream.putNextEntry(new ZipEntry(file.getName()));
      FileInputStream fileInputStream = new FileInputStream(file);

      IOUtils.copy(fileInputStream, zipOutputStream);

      fileInputStream.close();
      zipOutputStream.closeEntry();
    }

    zipOutputStream.close();
    tempSqlStatement.delete();
  }
  
  
}
