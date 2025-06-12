package grafioschtrader.repository;

import java.util.Optional;

import grafiosch.repository.BaseRepositoryCustom;
import grafiosch.repository.TenantBaseCustom;
import grafioschtrader.entities.Tenant;

/**
 * Custom repository interface for tenant-specific operations in GrafioschTrader. Extends base repository functionality
 * with tenant management operations.
 */
public interface TenantJpaRepositoryCustom extends BaseRepositoryCustom<Tenant>, TenantBaseCustom {

  /**
   * Removes all watchlists associated with the specified tenant.
   * 
   * @param idTenant the tenant ID whose watchlists should be removed
   * @return the tenant entity after watchlist removal
   */
  Tenant removeAllWatchlistByIdTenant(Integer idTenant);

  /**
   * Removes all portfolios associated with the specified tenant.
   * 
   * @param idTenant the tenant ID whose portfolios should be removed
   * @return the tenant entity after portfolio removal
   */
  Tenant removeAllPortfolios(Integer idTenant);

  /**
   * Attaches watchlist data to the tenant entity for lazy loading.
   * 
   * @param idTenant the tenant ID to attach watchlists to
   * @return the tenant entity with attached watchlist data
   */
  Tenant attachWatchlist(Integer idTenant);

  /**
   * Checks if dividend tax costs should be excluded for the current tenant.
   * 
   * @return true if dividend tax costs should be excluded, false otherwise
   */
  boolean isExcludeDividendTaxcost();

  /**
   * Creates missing currency pairs for the specified tenant based on portfolio currencies.
   * 
   * @param idTenant the tenant ID to create currency pairs for
   * @return Optional containing the tenant if found, empty otherwise
   */
  Optional<Tenant> createNotExistingCurrencypairs(Integer idTenant);

  /**
   * Changes the currency for both tenant and all associated portfolios.
   * 
   * @param currency the new currency code to set
   * @return the updated tenant entity
   */
  Tenant changeCurrencyTenantAndPortfolios(String currency);

  /**
   * Sets the watchlist to be used for performance calculations.
   * 
   * @param idWatchlist the watchlist ID to use for performance calculations
   * @return the updated tenant entity
   */
  Tenant setWatchlistForPerformance(Integer idWatchlist);

}
