package grafioschtrader.repository;

import java.util.Optional;

import grafiosch.repository.BaseRepositoryCustom;
import grafiosch.repository.TenantBaseCustom;
import grafioschtrader.entities.Tenant;

public interface TenantJpaRepositoryCustom extends BaseRepositoryCustom<Tenant>, TenantBaseCustom {

  Tenant removeAllWatchlistByIdTenant(Integer idTenant);

  Tenant removeAllPortfolios(Integer idTenant);

  Tenant attachWatchlist(Integer idTenant);

  boolean isExcludeDividendTaxcost();

  Optional<Tenant> createNotExistingCurrencypairs(Integer idTenant);

  Tenant changeCurrencyTenantAndPortfolios(String currency);

  Tenant setWatchlistForPerformance(Integer idWatchlist);

}
