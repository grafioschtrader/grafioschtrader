package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.common.ClientClock;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.CostBasisMethods;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.realizedgain.RealizedGainGrandTotal;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.service.CostBasisMethodService;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.CostBasisMethod;

/**
 * Report of the capital gains a tenant realized by selling securities, grouped by calendar year and in the main
 * currency. It is a neutral report that works for every country: it states what the transaction calculation of the
 * selected cost basis method realized and calculates no tax.
 *
 * <p>
 * It runs separately from {@link SecurityDividendsReport}, which knows no cost and feeds the tax statement eCH-0196 and
 * the income charts. The calculation itself is {@link RealizedGainCalculator}, this class only loads what it needs.
 * </p>
 *
 * <p>
 * <strong>Important:</strong> This class is a stateless Spring component and holds no member variables besides its
 * collaborators.
 * </p>
 */
@Component
public class RealizedGainReport {

  /** Earlier than any transaction a user can book. */
  private static final LocalDate BEFORE_FIRST_TRANSACTION = LocalDate.of(1900, 1, 1);

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private CostBasisMethodService costBasisMethodService;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private SecurityCalcService securityCalcService;

  @Autowired
  private ReportCurrencyMapLoader reportCurrencyMapLoader;

  /**
   * Calculates the realized gains of a tenant.
   *
   * @param idTenant           the tenant whose sales are reported
   * @param idsSecurityaccount the security accounts whose sales are listed, empty or -1 for all. The filter does not
   *                           change the gain of a sale. An id of another tenant matches no sale.
   * @param costBasisMethod    the method for all security accounts, or null for the method of each security account as
   *                           tenant and portfolios have set it
   * @return the years with their sales and the sums over all years
   */
  public RealizedGainGrandTotal getRealizedGainGrandTotalByTenant(final Integer idTenant,
      final List<Integer> idsSecurityaccount, final CostBasisMethod costBasisMethod) {
    final Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    final LocalDate today = ClientClock.today();
    final Map<String, Integer> currencyPrecision = globalparametersService.getCurrencyPrecision();
    final int precisionMC = currencyPrecision.getOrDefault(tenant.getCurrency(),
        BaseConstants.FID_STANDARD_FRACTION_DIGITS);
    final DateTransactionCurrencypairMap dateCurrencyMap = reportCurrencyMapLoader
        .loadForCashaccountAndSecurityCurrencies(tenant, today);
    final CostBasisMethods costBasisMethods = costBasisMethod == null ? costBasisMethodService.forTenant(idTenant)
        : CostBasisMethods.all(costBasisMethod);
    final Map<Integer, List<Securitysplit>> securitySplitMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdTenant(idTenant);
    final RealizedGainGrandTotal grandTotal = new RealizedGainCalculator(securityCalcService, tenant.getCurrency(),
        currencyPrecision, precisionMC).calculate(getTransactionsBySecurity(idTenant, today), securitySplitMap, today,
            dateCurrencyMap, costBasisMethods, tenant.isExcludeDivTax(), toSecurityaccountSet(idsSecurityaccount));
    grandTotal.portfolioList = tenant.getPortfolioList();
    grandTotal.tenantCountry = tenant.getCountry();
    return grandTotal;
  }

  /** All bookings that belong to a security, grouped by it in the order of their time. */
  private Map<Security, List<Transaction>> getTransactionsBySecurity(Integer idTenant, LocalDate today) {
    final Map<Integer, List<Transaction>> byIdSecurity = new LinkedHashMap<>();
    for (Transaction transaction : transactionJpaRepository
        .findByIdTenantAndTransactionDateBetweenForReport(idTenant, BEFORE_FIRST_TRANSACTION, today)) {
      if (transaction.getSecurity() != null && transaction.getIdSecurityaccount() != null) {
        byIdSecurity.computeIfAbsent(transaction.getSecurity().getIdSecuritycurrency(), _ -> new ArrayList<>())
            .add(transaction);
      }
    }
    final Map<Security, List<Transaction>> bySecurity = new LinkedHashMap<>();
    byIdSecurity.values().forEach(transactions -> bySecurity.put(transactions.getFirst().getSecurity(), transactions));
    return bySecurity;
  }

  /** Null selects all security accounts, as the dividends report does for an empty list or the single id -1. */
  private static Set<Integer> toSecurityaccountSet(List<Integer> idsSecurityaccount) {
    if (idsSecurityaccount == null || idsSecurityaccount.isEmpty()
        || (idsSecurityaccount.size() == 1 && idsSecurityaccount.get(0) == -1)) {
      return null;
    }
    return new HashSet<>(idsSecurityaccount);
  }
}
