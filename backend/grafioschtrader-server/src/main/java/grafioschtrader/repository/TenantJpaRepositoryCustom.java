package grafioschtrader.repository;

import java.util.Optional;

import grafioschtrader.entities.Tenant;
import jakarta.servlet.http.HttpServletResponse;

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
