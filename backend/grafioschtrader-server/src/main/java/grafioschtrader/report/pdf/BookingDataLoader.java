package grafioschtrader.report.pdf;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import grafioschtrader.reports.ReportHelper;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;

/** Loads booking data once with explicit scope and calendar, using the period metrics' fee conversion inputs. */
@Component
public class BookingDataLoader {
  private final TransactionJpaRepository transactions;
  private final HistoryquoteJpaRepository quotes;
  private final CurrencypairJpaRepository currencies;
  private final TradingDaysPlusJpaRepository tradingDays;

  public BookingDataLoader(TransactionJpaRepository transactions, HistoryquoteJpaRepository quotes,
      CurrencypairJpaRepository currencies, TradingDaysPlusJpaRepository tradingDays) {
    this.transactions = transactions;
    this.quotes = quotes;
    this.currencies = currencies;
    this.tradingDays = tradingDays;
  }

  /**
   * Loads the scope's bookings and converts them without accessing the current user or request clock.
   *
   * @param context                   authenticated tenant, owned portfolio (optional) and captured client calendar
   * @param from                      excluded valuation base
   * @param to                        inclusive closing date
   * @param currency                  main currency of the scope
   * @param feeInterestFxAtCutOffDate whether fees and account interest use the closing-date exchange rate
   * @param excludeDivTax             whether dividend withholding tax is added back to net income
   * @param opening                   opening holdings, or null when only the transaction list is selected
   * @param closing                   shared closing holdings, or null when no valuation section is selected
   * @return converted bookings and, when valuations are supplied, position contributions
   */
  public BookingData load(ReportContext context, LocalDate from, LocalDate to, String currency,
      boolean feeInterestFxAtCutOffDate, boolean excludeDivTax, HoldingsData opening, HoldingsData closing) {
    Integer tenant = context.idTenant(), portfolio = context.idPortfolio();
    var bookings = portfolio == null ? transactions.findByIdTenantAndTransactionDateBetweenForReport(tenant, from, to)
        : transactions.findByIdTenantAndIdPortfolioAndTransactionDateBetweenForReport(tenant, from, to, portfolio);
    var rates = new DateTransactionCurrencypairMap(currency, to,
        portfolio == null ? quotes.getHistoryquotesForAllForeignTransactionsByIdTenant(tenant)
            : quotes.getHistoryquotesForAllForeignTransactionsByIdPortfolio(portfolio),
        portfolio == null ? currencies.getAllCurrencypairsForTenantByTenant(tenant)
            : currencies.getAllCurrencypairsForPortfolioByPortfolio(portfolio),
        tradingDays.hasTradingDayBetweenUntilYesterday(to, context.today()), feeInterestFxAtCutOffDate,
        context.today());
    ReportHelper.loadUntilDateHistoryquotes(tenant, quotes, rates);
    return BookingData.calculate(bookings, opening, closing, rates, excludeDivTax);
  }
}
