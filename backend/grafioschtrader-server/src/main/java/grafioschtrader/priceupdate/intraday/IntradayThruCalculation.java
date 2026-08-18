package grafioschtrader.priceupdate.intraday;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;

import com.ezylang.evalex.Expression;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.dto.SecurityCurrencypairDerivedLinks;
import grafioschtrader.dto.SecurityCurrencypairDerivedLinks.VarNameLastPrice;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.repository.SecurityDerivedLinkJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.service.GlobalparametersService;

/**
 * Intraday price calculator for derived securities with formula-based or linked pricing.
 * 
 * <p>This class handles intraday price updates for securities whose prices are calculated based on other securities
 * or currency pairs rather than retrieved from external data feeds. It supports two calculation modes:
 * <ul>
 * <li><strong>Formula-based calculation</strong>: Uses mathematical expressions to calculate prices from linked instruments</li>
 * <li><strong>Direct linking</strong>: Copies the price directly from a single linked security or currency pair</li>
 * </ul></p>
 * 
 * <p><strong>Important:</strong> This calculation depends on other securities and currency pairs having up-to-date prices.
 * Therefore, this class should be executed <em>after</em> the intraday update of base securities and currencies to ensure
 * accurate calculated results. The calculation uses the EvalEx expression engine for mathematical formula evaluation.</p>
 * 
 * @param <S> the type of security currency extending Securitycurrency (note: implementation works specifically with Security entities)
 */
public class IntradayThruCalculation<S extends Securitycurrency<S>> extends BaseIntradayThru<Security> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());
  private final SecurityJpaRepository securityJpaRepository;
  private final SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository;

  /**
   * Constructs an intraday calculation processor for derived securities.
   * 
   * @param globalparametersService service for accessing global configuration parameters
   * @param securityJpaRepository repository for security entity persistence operations
   * @param securityDerivedLinkJpaRepository repository for managing security derivation relationships
   */
  public IntradayThruCalculation(GlobalparametersService globalparametersService,
      SecurityJpaRepository securityJpaRepository, SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository) {
    super(globalparametersService);
    this.securityJpaRepository = securityJpaRepository;
    this.securityDerivedLinkJpaRepository = securityDerivedLinkJpaRepository;

  }

  /**
   * Updates the calculated intraday price for a derived security based on linked instruments.
   * 
   * <p>This method performs a comprehensive calculation process:
   * <ul>
   * <li><strong>Validation</strong>: Checks retry limits, active status and update timing constraints, then claims the
   * update in the database so a concurrent caller for the same instrument stands down</li>
   * <li><strong>Link Resolution</strong>: Retrieves derived instrument links and current prices for linked securities</li>
   * <li><strong>Price Calculation</strong>:
   *   <ul>
   *   <li>For calculated securities: evaluates the formula expression using EvalEx with linked instrument prices as variables</li>
   *   <li>For non-calculated securities: directly copies the price from the first linked instrument</li>
   *   </ul>
   * </li>
   * <li><strong>Timestamp Management</strong>: Sets the timestamp to the newest intraday timestamp from linked instruments</li>
   * <li><strong>Error Handling</strong>: Manages retry counters and logs calculation failures</li>
   * <li><strong>Persistence</strong>: Saves the updated security with new price and metadata</li>
   * </ul></p>
   * 
   * @param security the derived security whose price should be calculated
   * @param maxIntraRetry maximum number of retry attempts for failed calculations, -1 for unlimited retries
   * @param scIntradayUpdateTimeout timeout in seconds for determining if delayed updates are allowed
   * @return the security with updated calculated price and retry counter, or original security if calculation was skipped
   */
  @Override
  public Security updateLastPriceSecurityCurrency(Security security, short maxIntraRetry, int scIntradayUpdateTimeout) {

    LocalDateTime now = LocalDateTime.now();
    if ((security.getRetryIntraLoad() < maxIntraRetry || maxIntraRetry == -1)
        && security.isActiveForIntradayUpdate(now.toLocalDate())
        && allowDelayedIntradayUpdate(security, scIntradayUpdateTimeout, now)
        && claimIntradayUpdate(security, scIntradayUpdateTimeout, now)) {

      SecurityCurrencypairDerivedLinks scdl = securityDerivedLinkJpaRepository
          .getDerivedInstrumentsLinksForSecurity(security);
      Expression expression = new Expression(security.getFormulaPrices());
      List<VarNameLastPrice> varNameLastPrices = scdl.getLastPricesByLinks(security.getIdLinkSecuritycurrency());
      security.setRetryIntraLoad((short) (security.getRetryIntraLoad() + 1));

      try {
        if (!varNameLastPrices.isEmpty()) {
          if (security.isCalculatedPrice()) {
            varNameLastPrices.forEach(vnlp -> expression.with(vnlp.varName, BigDecimal.valueOf(vnlp.sLast)));
            security.setSLast(expression.evaluate().getNumberValue().doubleValue());
          } else {
            security.setSLast(varNameLastPrices.get(0).sLast);
          }
          security.setRetryIntraLoad((short) 0);
          security.setSTimestamp(scdl.getNewestIntradayTimestamp());
        }
      } catch (final Exception e) {
        log.error("Last price calculuate failed on security={}", security.toString(), e);
      }
      try {
        security = securityJpaRepository.save(security);
      } catch (final ConcurrencyFailureException ex) {
        // Covers the optimistic case (ObjectOptimisticLockingFailureException, another writer bumped the version under
        // the detached copy this batch is holding) as well as the pessimistic one (CannotAcquireLockException). The
        // calculated last price is non critical and is refreshed within minutes. Letting either escape would abort the
        // whole derived instrument batch because of a single contended row.
        log.warn("Calculated intraday price save skipped, row was concurrently updated: security={}", security);
      } catch (final Exception ex) {
        log.error("Save failed for security={}", security, ex);
      }
    }
    return security;
  }

  /**
   * Determines if a delayed intraday update should be allowed based on timing constraints.
   * 
   * <p>Unlike connector-based updates, calculated price updates don't use feed connector delay settings.
   * Instead, this method uses a simple timeout-based approach to prevent excessive calculation frequency.</p>
   * 
   * @param security the security to check for update eligibility
   * @param scIntradayUpdateTimeout timeout in seconds defining the minimum interval between updates
   * @param now current timestamp for timing calculations
   * @return true if the security should be updated (no previous timestamp or timeout period has elapsed), false otherwise
   */
  private boolean allowDelayedIntradayUpdate(final Security security, final int scIntradayUpdateTimeout,
      LocalDateTime now) {
    final long lessThenPossible = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        - 1000L * scIntradayUpdateTimeout;
    return security.getSTimestamp() == null
        || security.getSTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() < lessThenPossible;
  }

  /**
   * Claims the intraday calculation of this derived instrument against concurrent writers.
   *
   * <p>
   * {@link #allowDelayedIntradayUpdate} decides purely on the in-memory entity, so two concurrent callers for the same
   * derived instrument both pass it and both write the row. Because the batch of
   * {@code SecurityJpaRepositoryImpl.updateAllLastPrices} works on detached entities read minutes earlier, the loser of
   * that race does not merely duplicate work, its {@code save} fails the {@code @Version} check. This claim resolves
   * the race in the database: only the caller whose conditional UPDATE affected a row proceeds. It runs after the
   * in-memory pre-filter, so the extra statement is only issued when an update actually looks due.
   * </p>
   *
   * <p>
   * Unlike {@code IntradayThruConnector} there is no feed connector and therefore no provider specific delay, so the
   * threshold is the plain global timeout already used by {@link #allowDelayedIntradayUpdate}. Note that a successful
   * save afterwards writes the <em>linked</em> instrument's timestamp back into {@code s_timestamp}, which is older
   * than the value the claim just set. The claim therefore serialises concurrent calculations but does not throttle
   * across runs, and it cannot protect against writers that bump the version without touching {@code s_timestamp}
   * (dividends, splits, security edits) - the catch around the save remains the safety net for those.
   * </p>
   *
   * @param security                the derived instrument to claim
   * @param scIntradayUpdateTimeout minimum interval between two updates in seconds
   * @param now                     the new timestamp to set
   * @return true if this caller won the claim and should perform the calculation
   */
  private boolean claimIntradayUpdate(final Security security, final int scIntradayUpdateTimeout,
      final LocalDateTime now) {
    if (security.getIdSecuritycurrency() == null) {
      // A not yet persisted instrument has no row that a second writer could contend for, so there is nothing to claim.
      return true;
    }
    return securityJpaRepository.claimIntradayUpdate(security.getIdSecuritycurrency(), now,
        now.minusSeconds(scIntradayUpdateTimeout)) > 0;
  }

  /**
   * Returns null as calculated securities do not support download links.
   * 
   * <p>Calculated securities derive their prices from other instruments rather than external data sources,
   * so download links are not applicable for this calculation-based approach.</p>
   * 
   * @param securitycurrency the security (parameter ignored for calculated securities)
   * @return always null as download links are not supported for calculated prices
   */
  @Override
  public String getSecuritycurrencyIntraDownloadLinkAsUrlStr(Security securitycurrency) {
    return null;
  }

  /**
   * Returns null as calculated securities do not use feed connectors for download links.
   * 
   * <p>This method is not applicable for calculated securities since they compute their prices based on
   * linked instruments rather than retrieving data from external feed connectors.</p>
   * 
   * @param securitycurrency the security (parameter ignored for calculated securities)
   * @param feedConnector the feed connector (parameter ignored for calculated securities)
   * @return always null as feed connector-based download links are not supported for calculated prices
   */
  @Override
  public String createDownloadLink(Security securitycurrency, IFeedConnector feedConnector) {
    return null;
  }

}
