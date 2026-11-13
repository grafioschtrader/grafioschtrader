package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;

/**
 * Loads the exchange rates a tenant report converts with. The dividends report and the realized gain report share it,
 * so both read the same rates for the cash account currencies of the transactions of a tenant.
 *
 * <p>
 * <strong>Important:</strong> This class is a stateless Spring component and holds no member variables besides its
 * repositories.
 * </p>
 */
@Component
public class ReportCurrencyMapLoader {

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  /**
   * Loads the rates of the cash account currencies on the days of the interest, fee, purchase, sale and dividend
   * transactions of the tenant, as the dividends report always did.
   *
   * @param tenant the tenant whose transactions determine the rates
   * @return the currency map without an until date
   */
  public DateTransactionCurrencypairMap loadForCashaccountCurrencies(final Tenant tenant) {
    final CompletableFuture<List<Object[]>> rates = CompletableFuture.supplyAsync(
        () -> historyquoteJpaRepository.getHistoryquoteCurrenciesForIntrFeeBuySellDivByIdTenantAndMainCurrency(
            tenant.getIdTenant(), tenant.getCurrency()));
    final CompletableFuture<List<Currencypair>> currencypairs = CompletableFuture.supplyAsync(
        () -> currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(tenant.getIdTenant()));
    return new DateTransactionCurrencypairMap(tenant.getCurrency(), null, rates.join(), currencypairs.join(), true,
        false);
  }

  /**
   * Loads the rates of every currency the transactions of the tenant are exposed to, the currency of the cash account
   * and the one of the security, on the day of each transaction and whatever its type. The finance costs of a margin
   * product need such a rate as well, and a sale of a security in a third currency needs the rate of its currency.
   *
   * @param tenant    the tenant whose transactions determine the rates
   * @param untilDate the last day the report covers, normally today
   * @return the currency map until the given day
   */
  public DateTransactionCurrencypairMap loadForCashaccountAndSecurityCurrencies(final Tenant tenant,
      final LocalDate untilDate) {
    final CompletableFuture<List<Object[]>> transactionRates = CompletableFuture.supplyAsync(
        () -> historyquoteJpaRepository.getHistoryquoteTransactionCurrenciesByIdTenantAndMainCurrency(
            tenant.getIdTenant(), tenant.getCurrency()));
    final CompletableFuture<List<Currencypair>> currencypairs = CompletableFuture.supplyAsync(
        () -> currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(tenant.getIdTenant()));

    return new DateTransactionCurrencypairMap(tenant.getCurrency(), untilDate, transactionRates.join(),
        currencypairs.join(), true, false, untilDate);
  }
}
