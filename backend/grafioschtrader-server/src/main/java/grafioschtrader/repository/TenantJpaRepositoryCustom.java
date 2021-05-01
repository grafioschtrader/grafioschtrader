package grafioschtrader.repository;

import java.util.Optional;

import javax.servlet.http.HttpServletResponse;

import grafioschtrader.entities.Tenant;

public interface TenantJpaRepositoryCustom extends BaseRepositoryCustom<Tenant> {

  Tenant removeAllWatchlistByIdTenant(Integer idTenant);

  Tenant removeAllPortfolios(Integer idTenant);

  Tenant attachWatchlist(Integer idTenant);

  boolean isExcludeDividendTaxcost();

  void deleteMyDataAndUserAccount() throws Exception;

  Optional<Tenant> createNotExistingCurrencypairs(Integer idTenant);

  Tenant changeCurrencyTenantAndPortfolios(String currency);

  Tenant setWatchlistForPerformance(Integer idWatchlist);
  
  void getExportPersonalDataAsZip(HttpServletResponse response) throws Exception;
}
