package grafioschtrader.priceupdate.intraday;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
   * <li><strong>Validation</strong>: Checks retry limits, active status, and update timing constraints</li>
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

    Date now = new Date();
    if ((security.getRetryIntraLoad() < maxIntraRetry || maxIntraRetry == -1) && security.isActiveForIntradayUpdate(now)
        && allowDelayedIntradayUpdate(security, scIntradayUpdateTimeout, now)) {

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
      security = securityJpaRepository.save(security);
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
  private boolean allowDelayedIntradayUpdate(final Security security, final int scIntradayUpdateTimeout, Date now) {
    final long lessThenPossible = now.getTime() - 1000 * scIntradayUpdateTimeout;
    return security.getSTimestamp() == null || security.getSTimestamp().getTime() < lessThenPossible;
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
