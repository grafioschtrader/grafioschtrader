package grafioschtrader.priceupdate.intraday;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ezylang.evalex.Expression;

import grafioschtrader.dto.SecurityCurrencypairDerivedLinks;
import grafioschtrader.dto.SecurityCurrencypairDerivedLinks.VarNameLastPrice;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.SecurityDerivedLinkJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Intraday update for calculated prices.</br>
 * Prices depend on other prices of securities or currency pairs, those are not
 * updated with this class. Because of that this calculation of prices should be
 * called after intraday update of securities and currencies.
 *
 * @author Hugo Graf
 *
 * @param <S>
 */
public class IntradayThruCalculation<S extends Securitycurrency<S>> extends BaseIntradayThru<Security> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());
  private final SecurityJpaRepository securityJpaRepository;
  private final SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository;

  public IntradayThruCalculation(GlobalparametersJpaRepository globalparametersJpaRepository,
      SecurityJpaRepository securityJpaRepository, SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository) {
    super(globalparametersJpaRepository);
    this.securityJpaRepository = securityJpaRepository;
    this.securityDerivedLinkJpaRepository = securityDerivedLinkJpaRepository;

  }

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

  private boolean allowDelayedIntradayUpdate(final Security security, final int scIntradayUpdateTimeout, Date now) {
    final long lessThenPossible = now.getTime() - 1000 * scIntradayUpdateTimeout;
    return security.getSTimestamp() == null || security.getSTimestamp().getTime() < lessThenPossible;
  }

  @Override
  public String getSecuritycurrencyIntraDownloadLinkAsUrlStr(Security securitycurrency) {
    return null;
  }

}
