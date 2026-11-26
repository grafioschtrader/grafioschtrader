package grafioschtrader.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.service.DailyLimitService;
import grafiosch.types.OperationType;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.BenchmarkSource;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resolves, reads and writes the benchmark of the period performance comparison.
 *
 * <p>
 * The benchmark is stored in {@code tenant.id_benchmark} (client view and default of every portfolio) and
 * {@code portfolio.id_benchmark} (override). Visibility follows the rule of the watchlist indicator columns: a public
 * or tenant-private security, or a currency pair. In a simulation environment the private securities belong to the
 * main tenant, so visibility is checked against it while the assignment itself stays with the simulation tenant.
 * </p>
 */
@Service
public class PerformanceBenchmarkService {

  /** Pseudo entity name of the daily budget of benchmark assignments. */
  public static final String ENTITY_NAME = "PerformanceBenchmark";

  private final TenantJpaRepository tenantJpaRepository;
  private final PortfolioJpaRepository portfolioJpaRepository;
  private final SecurityJpaRepository securityJpaRepository;
  private final CurrencypairJpaRepository currencypairJpaRepository;
  private final DailyLimitService dailyLimitService;

  public PerformanceBenchmarkService(TenantJpaRepository tenantJpaRepository,
      PortfolioJpaRepository portfolioJpaRepository, SecurityJpaRepository securityJpaRepository,
      CurrencypairJpaRepository currencypairJpaRepository, DailyLimitService dailyLimitService) {
    this.tenantJpaRepository = tenantJpaRepository;
    this.portfolioJpaRepository = portfolioJpaRepository;
    this.securityJpaRepository = securityJpaRepository;
    this.currencypairJpaRepository = currencypairJpaRepository;
    this.dailyLimitService = dailyLimitService;
  }

  /**
   * A benchmark that resolved for a report.
   *
   * @param instrument the security or currency pair
   * @param source     where it came from
   */
  public record ResolvedBenchmark(Securitycurrency<?> instrument, BenchmarkSource source) {

    /** Currency of the quotes: the currency of a security, the to-currency of a pair. */
    public String currency() {
      return instrument instanceof Security security ? security.getCurrency()
          : ((Currencypair) instrument).getToCurrency();
    }
  }

  @Schema(description = "The effective benchmark of a scope of the period performance")
  public record PerformanceBenchmarkView(
      @Schema(description = "The portfolio of the scope, null for the client view") Integer idPortfolio,
      @Schema(description = "The benchmark instrument") Integer idSecuritycurrency,
      @Schema(description = "Display name of the benchmark") String name,
      @Schema(description = "Currency of the benchmark quotes") String currency,
      @Schema(description = "Stored with the portfolio or inherited from the tenant") BenchmarkSource source) {
  }

  /**
   * Resolves the benchmark of a report.
   *
   * @param tenant    tenant of the report
   * @param portfolio portfolio of the report, null for the client view
   * @param requested benchmark of the request: null for the stored one, 0 for none, otherwise an instrument id
   * @return the benchmark, empty when none resolves
   * @throws DataViolationException when the requested id is negative or names an instrument the tenant cannot read
   */
  public Optional<ResolvedBenchmark> resolve(Tenant tenant, Portfolio portfolio, Integer requested) {
    Integer visibilityTenant = visibilityTenant(tenant);
    if (requested != null) {
      if (requested == 0) {
        return Optional.empty();
      }
      return Optional.of(new ResolvedBenchmark(requireReadable(requested, visibilityTenant),
          BenchmarkSource.BENCHMARK_SOURCE_REQUEST));
    }
    return resolveStored(tenant, portfolio, visibilityTenant);
  }

  /**
   * The effective stored benchmark of a scope of the authenticated user.
   *
   * @param user        the authenticated user
   * @param idPortfolio the portfolio, null for the client view
   * @return the view, empty when no readable benchmark is stored for the scope or inherited
   */
  public Optional<PerformanceBenchmarkView> view(User user, Integer idPortfolio) {
    Tenant tenant = tenantJpaRepository.getReferenceById(user.getIdTenant());
    Portfolio portfolio = ownPortfolio(user, idPortfolio);
    return resolveStored(tenant, portfolio, visibilityTenant(tenant)).map(r -> toView(idPortfolio, r));
  }

  /**
   * Stores the benchmark of a scope. Writes of a read-only tenant access never get here, the tenant read-only filter
   * refuses them.
   *
   * @param user               the authenticated user
   * @param idPortfolio        the portfolio, null for the client view
   * @param idSecuritycurrency the benchmark instrument
   * @return the effective benchmark of the scope after saving
   */
  @Transactional
  public PerformanceBenchmarkView assign(User user, Integer idPortfolio, Integer idSecuritycurrency) {
    Portfolio portfolio = ownPortfolio(user, idPortfolio);
    Tenant tenant = tenantJpaRepository.getReferenceById(user.getIdTenant());
    if (idSecuritycurrency == null) {
      throw new DataViolationException("id.benchmark", "gt.benchmark.required", null);
    }
    Securitycurrency<?> instrument = requireReadable(idSecuritycurrency, visibilityTenant(tenant));
    write(user, portfolio, instrument.getIdSecuritycurrency());
    BenchmarkSource source = portfolio == null ? BenchmarkSource.BENCHMARK_SOURCE_TENANT
        : BenchmarkSource.BENCHMARK_SOURCE_PORTFOLIO;
    return toView(idPortfolio, new ResolvedBenchmark(instrument, source));
  }

  /**
   * Removes the benchmark of exactly one scope. A portfolio then inherits the benchmark of the tenant again.
   *
   * @param user        the authenticated user
   * @param idPortfolio the portfolio, null for the client view
   */
  @Transactional
  public void clear(User user, Integer idPortfolio) {
    write(user, ownPortfolio(user, idPortfolio), null);
  }

  /**
   * Whether a tenant may read an instrument as benchmark, used when tenant data is copied.
   *
   * @param idSecuritycurrency the instrument, may be null
   * @param idTenant           the tenant that would use it
   * @return true for a readable instrument
   */
  public boolean isReadable(Integer idSecuritycurrency, Integer idTenant) {
    return idSecuritycurrency != null && readable(idSecuritycurrency, idTenant) != null;
  }

  private Optional<ResolvedBenchmark> resolveStored(Tenant tenant, Portfolio portfolio, Integer visibilityTenant) {
    if (portfolio != null) {
      Securitycurrency<?> instrument = readable(portfolio.getIdBenchmark(), visibilityTenant);
      if (instrument != null) {
        return Optional.of(new ResolvedBenchmark(instrument, BenchmarkSource.BENCHMARK_SOURCE_PORTFOLIO));
      }
    }
    Securitycurrency<?> instrument = readable(tenant.getIdBenchmark(), visibilityTenant);
    if (instrument != null) {
      return Optional.of(new ResolvedBenchmark(instrument, BenchmarkSource.BENCHMARK_SOURCE_TENANT));
    }
    if (tenant.getIdParentTenant() != null) {
      Tenant parent = tenantJpaRepository.getReferenceById(tenant.getIdParentTenant());
      instrument = readable(parent.getIdBenchmark(), visibilityTenant);
      if (instrument != null) {
        return Optional.of(new ResolvedBenchmark(instrument, BenchmarkSource.BENCHMARK_SOURCE_PARENT_TENANT));
      }
    }
    return Optional.empty();
  }

  private void write(User user, Portfolio portfolio, Integer idBenchmark) {
    dailyLimitService.check(user, ENTITY_NAME, 1);
    if (portfolio == null) {
      tenantJpaRepository.updateIdBenchmark(user.getIdTenant(), idBenchmark);
    } else {
      portfolioJpaRepository.updateIdBenchmark(user.getIdTenant(), portfolio.getIdPortfolio(), idBenchmark);
    }
    dailyLimitService.log(user.getIdUser(), ENTITY_NAME, OperationType.UPDATE, 1);
  }

  private Portfolio ownPortfolio(User user, Integer idPortfolio) {
    if (idPortfolio == null) {
      return null;
    }
    Portfolio portfolio = portfolioJpaRepository.findByIdTenantAndIdPortfolio(user.getIdTenant(), idPortfolio);
    if (portfolio == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return portfolio;
  }

  private Securitycurrency<?> requireReadable(Integer idSecuritycurrency, Integer visibilityTenant) {
    if (idSecuritycurrency < 0) {
      throw new DataViolationException("id.benchmark", "WIC_BENCHMARK_UNREADABLE", null);
    }
    Securitycurrency<?> instrument = readable(idSecuritycurrency, visibilityTenant);
    if (instrument == null) {
      throw new DataViolationException("id.benchmark", "WIC_BENCHMARK_UNREADABLE", null);
    }
    if (instrument instanceof Security security && security.getStockexchange().isNoMarketValue()) {
      throw new DataViolationException("id.benchmark", "BENCHMARK_PERIOD_PRICES", null);
    }
    return instrument;
  }

  private Securitycurrency<?> readable(Integer idSecuritycurrency, Integer visibilityTenant) {
    if (idSecuritycurrency == null) {
      return null;
    }
    Security security = securityJpaRepository
        .findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(idSecuritycurrency, visibilityTenant);
    if (security != null) {
      return security;
    }
    return currencypairJpaRepository.findById(idSecuritycurrency).orElse(null);
  }

  private static Integer visibilityTenant(Tenant tenant) {
    return tenant.getIdParentTenant() != null ? tenant.getIdParentTenant() : tenant.getIdTenant();
  }

  private static PerformanceBenchmarkView toView(Integer idPortfolio, ResolvedBenchmark resolved) {
    return new PerformanceBenchmarkView(idPortfolio, resolved.instrument().getIdSecuritycurrency(),
        resolved.instrument().getName(), resolved.currency(), resolved.source());
  }
}
