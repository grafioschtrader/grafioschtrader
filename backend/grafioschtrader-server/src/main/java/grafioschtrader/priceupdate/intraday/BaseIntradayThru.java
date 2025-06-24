package grafioschtrader.priceupdate.intraday;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.ThreadHelper;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.priceupdate.BaseQuoteThru;
import grafioschtrader.service.GlobalparametersService;

/**
 * Abstract base class providing common functionality for intraday price update implementations.
 * 
 * <p>This class serves as the foundation for all intraday price update strategies by providing standardized
 * batch processing capabilities with configurable threading options. It implements the common orchestration
 * logic for processing multiple securities while delegating the actual update implementation to concrete
 * subclasses.</p>
 * 
 * <p>Key features provided by this base class:
 * <ul>
 * <li><strong>Batch Processing</strong>: Efficient handling of multiple securities with single-threaded and parallel execution modes</li>
 * <li><strong>Configuration Integration</strong>: Automatic retrieval of global parameters for retry limits and timeouts</li>
 * <li><strong>Threading Control</strong>: Flexible execution using either sequential processing or ForkJoinPool for parallelism</li>
 * <li><strong>Transaction Management</strong>: Proper transaction boundaries for batch update operations</li>
 * <li><strong>Performance Optimization</strong>: Intelligent thread pool sizing based on system core multipliers</li>
 * </ul></p>
 * 
 * <p>Concrete implementations must provide the entity-specific update logic by implementing the abstract
 * {@code updateLastPriceSecurityCurrency} method, while this base class handles the orchestration of
 * batch operations and threading coordination.</p>
 * 
 * @param <S> the type of security currency extending Securitycurrency (Security or Currencypair)
 */
public abstract class BaseIntradayThru<S extends Securitycurrency<S>> extends BaseQuoteThru
    implements IIntradayLoad<S> {

  protected final GlobalparametersService globalparametersService;

  /**
   * Constructs the base intraday processor with global parameter access.
   * 
   * @param globalparametersService service for retrieving global configuration parameters
   */
  protected BaseIntradayThru(GlobalparametersService globalparametersService) {
    this.globalparametersService = globalparametersService;
  }

  /**
   * Updates intraday prices for multiple securities using default global retry configuration.
   * 
   * <p>This convenience method retrieves the maximum retry limit from global parameters and delegates
   * to the full parameter version. It provides a simplified interface for batch updates when custom
   * retry configuration is not required.</p>
   * 
   * <p>The method is transactional and modifying, ensuring proper database transaction management
   * for the batch update operations.</p>
   * 
   * @param securtycurrencies list of securities or currency pairs to update
   * @param singleThread true to force single-threaded execution, false for parallel processing
   * @return list of updated securities with new intraday prices and retry state
   */
  @Override
  @Transactional
  @Modifying
  public List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies, boolean singleThread) {
    final short maxIntraRetry = globalparametersService.getMaxIntraRetry();
    return updateLastPriceOfSecuritycurrency(securtycurrencies, maxIntraRetry, singleThread);
  }

  /**
   * Updates intraday prices for multiple securities with comprehensive batch processing and threading control.
   * 
   * <p>This method provides the core batch processing logic with intelligent execution strategy selection:
   * <ul>
   * <li><strong>Single Entity</strong>: Direct method call without threading overhead for optimal performance</li>
   * <li><strong>Multiple Entities + Single Thread</strong>: Sequential forEach processing for debugging or when parallelism is undesired</li>
   * <li><strong>Multiple Entities + Parallel</strong>: ForkJoinPool-based parallel execution using ThreadHelper for optimal throughput</li>
   * </ul></p>
   * 
   * <p>The parallel execution leverages {@code FORK_JOIN_POOL_CORE_MULTIPLIER} to optimize thread pool sizing
   * based on system capabilities, ensuring efficient resource utilization without overwhelming the system.</p>
   * 
   * <p>Global timeout configuration is automatically retrieved and applied consistently across all entities
   * in the batch, ensuring uniform timing behavior regardless of the processing strategy selected.</p>
   * 
   * @param securtycurrencies list of securities or currency pairs to update
   * @param maxIntraRetry maximum number of retry attempts for failed updates, -1 for unlimited retries
   * @param singleThread true to force single-threaded sequential execution, false for parallel ForkJoinPool processing
   * @return list of updated securities with new intraday prices and retry state, maintaining input order for single-threaded execution
   */
  @Override
  public List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies, final short maxIntraRetry,
      boolean singleThread) {
    final int scIntradayUpdateTimeout = globalparametersService.getSecurityCurrencyIntradayUpdateTimeout();
    final List<S> securtycurrenciesUpd = new ArrayList<>();
    if (securtycurrencies.size() > 1) {
      if (singleThread) {
        securtycurrencies.forEach(securitycurrency -> {
          securtycurrenciesUpd
              .add(updateLastPriceSecurityCurrency(securitycurrency, maxIntraRetry, scIntradayUpdateTimeout));
        });
      } else {
        ThreadHelper.executeForkJoinPool(() -> securtycurrencies.parallelStream().forEach(securitycurrency -> {
          securtycurrenciesUpd
              .add(updateLastPriceSecurityCurrency(securitycurrency, maxIntraRetry, scIntradayUpdateTimeout));
        }), GlobalConstants.FORK_JOIN_POOL_CORE_MULTIPLIER);
      }
    } else if (securtycurrencies.size() == 1) {
      securtycurrenciesUpd
          .add(updateLastPriceSecurityCurrency(securtycurrencies.getFirst(), maxIntraRetry, scIntradayUpdateTimeout));
    }
    return securtycurrenciesUpd;
  }
}
