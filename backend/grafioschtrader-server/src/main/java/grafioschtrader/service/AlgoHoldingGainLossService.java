package grafioschtrader.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;

/**
 * The gain or loss of an actual holding, measured against what it cost.
 *
 * <p>
 * The holding alert used to compare the last price with the previous close, which is the movement of the market since
 * yesterday and says nothing about the position: an instrument bought years ago at half the price reports a loss on any
 * red day. What the alert is specified to measure is the position's own performance against its cost basis, with a
 * short position gaining when the price falls.
 * </p>
 *
 * <p>
 * The calculation is not reimplemented here. It walks the tenant's transactions through {@link SecurityCalcService},
 * the same average cost accounting the portfolio reports use, and asks the resulting position for the percentage it
 * would show. An alert and the report the user then opens therefore cannot disagree. Everything is in the currency of
 * the instrument, which is also the currency of the price the alert compares, so no exchange rate enters into it.
 * </p>
 */
@Service
public class AlgoHoldingGainLossService {

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private SecurityCalcService securityCalcService;

  @Autowired
  private GlobalparametersService globalparametersService;

  /**
   * Whether the tenant currently holds this instrument, and the percentage that position would show at a given price.
   *
   * <p>
   * Absence of a position and an open position whose percentage cannot be formed are not the same thing. A closed
   * holding reports {@code open} false; a still-open holding whose cost basis is zero reports {@code open} true and
   * leaves {@code gainLossPercentage} null, because dividing by that basis is undefined. The holding alert uses that
   * distinction: no position is an evaluation failure, while a missing percentage only disables the percentage
   * thresholds and still lets the price bounds fire.
   * </p>
   *
   * @param open               whether the tenant still holds units of the instrument
   * @param gainLossPercentage the signed percentage against cost basis, positive for a gain in the direction the
   *                           position is held, or {@code null} when there is no open position or when the percentage
   *                           is undefined
   */
  public record HoldingObservation(boolean open, Double gainLossPercentage) {
  }

  /**
   * The percentage gain or loss of a tenant's open position in one instrument at a given price.
   *
   * @param idTenant the tenant whose transactions define the position
   * @param security the instrument
   * @param price    the price to value the position at, in the instrument's currency
   * @return the signed percentage, positive for a gain in the direction the position is held, or empty when the tenant
   *         holds no open position in the instrument, or when its cost basis is zero and a percentage is therefore
   *         undefined
   */
  public Optional<Double> positionGainLossPercentage(Integer idTenant, Security security, double price) {
    return Optional.ofNullable(observe(idTenant, security, price).gainLossPercentage());
  }

  /**
   * Separates absence of a position from an open position with an undefined percentage cost basis.
   *
   * @param idTenant the tenant whose transactions define the position
   * @param security the instrument
   * @param price    the price to value the position at, in the instrument's currency
   * @return the observation of that position at {@code price}
   */
  public HoldingObservation observe(Integer idTenant, Security security, double price) {
    List<Transaction> transactions = transactionJpaRepository.findByIdTenantAndIdSecurity(idTenant,
        security.getIdSecuritycurrency());
    if (transactions.isEmpty()) {
      return new HoldingObservation(false, null);
    }
    Tenant tenant = tenantJpaRepository.findById(idTenant).orElse(null);
    if (tenant == null) {
      return new HoldingObservation(false, null);
    }

    SecurityTransactionSummary summary = new SecurityTransactionSummary(security, null,
        globalparametersService.getCurrencyPrecision());
    Map<Integer, List<Securitysplit>> securitySplitMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdSecuritycurrency(security.getIdSecuritycurrency());
    List<Securitysplit> splits = securitySplitMap.get(security.getIdSecuritycurrency());
    if (splits != null) {
      summary.securityPositionSummary.securitycurrency.setSplitPropose(splits.toArray(new Securitysplit[0]));
    }

    // No DateTransactionCurrencypairMap: the percentage is a ratio of two amounts in the instrument's own currency, so
    // converting both into the tenant currency would divide the exchange rate out again. Passing null lets the service
    // build the minimal map it needs for the walk itself.
    securityCalcService.calcTransactions(security, tenant.isExcludeDivTax(), summary, securitySplitMap, transactions,
        LocalDate.now(), null);

    SecurityPositionSummary position = summary.securityPositionSummary;
    if (position.units == 0.0)
      return new HoldingObservation(false, null);
    if (position.adjustedCostBase == 0.0)
      return new HoldingObservation(true, null);
    position.calcGainLossByPrice(price);
    double percentage = position.getPositionGainLossPercentage();
    return new HoldingObservation(true, Double.isFinite(percentage) ? percentage : null);
  }
}
