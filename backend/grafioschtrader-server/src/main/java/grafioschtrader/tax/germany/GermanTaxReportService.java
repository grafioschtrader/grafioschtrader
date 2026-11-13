package grafioschtrader.tax.germany;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.common.ClientClock;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycashaccount;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.TaxCountry;
import grafioschtrader.entities.TaxYear;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.germantax.GermanTaxYearReport;
import grafioschtrader.reportviews.germantax.GermanTaxYearReport.PortfolioAssignment;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TaxCountryJpaRepository;
import grafioschtrader.repository.TaxYearJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.TaxWithholding;
import grafioschtrader.types.TransactionType;

/**
 * Loads what the German tax calculation needs for a tenant and runs {@link GermanTaxLotEngine}. The report belongs to a
 * tenant that reports in euro and has Germany as its country, and it always covers the whole tenant: the FIFO ledgers
 * security account would fall apart when only some security accounts were calculated.
 *
 * <p>
 * <strong>Important:</strong> This class is a stateless Spring component. The market data of a request lives in the
 * object created for it.
 * </p>
 */
@Service
public class GermanTaxReportService {

  /** The country code of the tenant for which the German tax help exists. */
  public static final String COUNTRY_GERMANY = "DE";
  /** The advance lump sum exists since 2018. */
  public static final int FIRST_TAX_YEAR = 2018;
  private static final String MAIN_CURRENCY = "EUR";
  private static final LocalDate BEFORE_FIRST_TRANSACTION = LocalDate.of(1900, 1, 1);
  /** The prices at the edge of a year count within this number of calendar days. */
  private static final int PRICE_TOLERANCE_DAYS = 7;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;
  @Autowired
  private TransactionJpaRepository transactionJpaRepository;
  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;
  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;
  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;
  @Autowired
  private DividendJpaRepository dividendJpaRepository;
  @Autowired
  private TaxCountryJpaRepository taxCountryJpaRepository;
  @Autowired
  private TaxYearJpaRepository taxYearJpaRepository;

  /**
   * Calculates the report of a tax year for a tenant.
   *
   * @param idTenant the tenant of the signed in user, never an id from the client
   * @param taxYear  the tax year, from 2018 to the current year
   * @return the starting values of the tax return
   * @throws DataViolationException when the tenant does not report in euro with the country Germany, or the year is
   *                                outside the range
   */
  public GermanTaxYearReport getReport(Integer idTenant, int taxYear) {
    final Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    checkAvailable(tenant);
    final LocalDate today = ClientClock.today();
    if (taxYear < FIRST_TAX_YEAR || taxYear > today.getYear()) {
      throw new DataViolationException("tax.year", "gt.german.tax.year.range",
          new Object[] { FIRST_TAX_YEAR, today.getYear() });
    }
    final List<Transaction> securityTransactions = new ArrayList<>();
    final List<Transaction> cashInterest = new ArrayList<>();
    for (Transaction transaction : transactionJpaRepository.findByIdTenantAndTransactionDateBetweenForReport(idTenant,
        BEFORE_FIRST_TRANSACTION, LocalDate.of(taxYear, 12, 31))) {
      if (transaction.getSecurity() != null) {
        securityTransactions.add(transaction);
      } else if (transaction.getTransactionType() == TransactionType.INTEREST_CASHACCOUNT) {
        cashInterest.add(transaction);
      }
    }
    final Map<Integer, List<Securitysplit>> splitMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdTenant(idTenant);
    final GermanTaxYearReport report = new GermanTaxLotEngine(new RepositoryMarketData(),
        new TenantPortfolios(tenant.getPortfolioList()), splitMap, today).calculate(taxYear, securityTransactions,
            cashInterest);
    return report;
  }

  /** Whether the tenant may use the German tax help: it reports in euro and its country is Germany. */
  public static boolean isAvailable(Tenant tenant) {
    return MAIN_CURRENCY.equals(tenant.getCurrency()) && COUNTRY_GERMANY.equals(tenant.getCountry());
  }

  private void checkAvailable(Tenant tenant) {
    if (!isAvailable(tenant)) {
      throw new DataViolationException("tenant.currency", "gt.german.tax.tenant.not.eligible", null);
    }
  }

  /** The portfolios of the tenant. Security accounts and cash accounts share one id space. */
  private static final class TenantPortfolios implements GermanTaxPortfolios {
    private final Map<Integer, Integer> portfolioByAccount = new HashMap<>();
    private final Map<Integer, TaxWithholding> withholdingByPortfolio = new HashMap<>();
    private final List<PortfolioAssignment> assignments = new ArrayList<>();

    TenantPortfolios(List<Portfolio> portfolioList) {
      for (Portfolio portfolio : portfolioList) {
        withholdingByPortfolio.put(portfolio.getIdPortfolio(), portfolio.getTaxWithholding());
        boolean hasAccounts = false;
        if (portfolio.getSecuritycashaccountList() != null) {
          for (Securitycashaccount account : portfolio.getSecuritycashaccountList()) {
            portfolioByAccount.put(account.getIdSecuritycashAccount(), portfolio.getIdPortfolio());
            hasAccounts = true;
          }
        }
        assignments.add(new PortfolioAssignment(portfolio.getIdPortfolio(), portfolio.getName(),
            portfolio.getTaxWithholding() == null ? null : portfolio.getTaxWithholding().name(), hasAccounts));
      }
    }

    @Override
    public Integer portfolioOfSecurityaccount(Integer idSecurityaccount) {
      return portfolioByAccount.get(idSecurityaccount);
    }

    @Override
    public Integer portfolioOfCashaccount(Integer idCashaccount) {
      return portfolioByAccount.get(idCashaccount);
    }

    @Override
    public TaxWithholding withholding(Integer idPortfolio) {
      return withholdingByPortfolio.get(idPortfolio);
    }

    @Override
    public List<PortfolioAssignment> assignments() {
      return assignments;
    }
  }

  /** The market data of one request, read from the history quotes and loaded when it is first needed. */
  private final class RepositoryMarketData implements GermanTaxMarketData {
    private final Map<String, NavigableMap<LocalDate, Double>> ratesByCurrency = new HashMap<>();
    private final Map<Integer, List<Historyquote>> quotesBySecurity = new HashMap<>();
    private final Map<Integer, List<Dividend>> dividendsBySecurity = new HashMap<>();
    private Map<Integer, Double> baseRates;

    @Override
    public Double rateToEur(String currency, LocalDate date) {
      if (MAIN_CURRENCY.equals(currency)) {
        return 1.0;
      }
      return ratesByCurrency.computeIfAbsent(currency, this::loadRates).get(date);
    }

    /** The closing rates of the currency pair into euro, or the inverse of the pair out of euro. */
    private NavigableMap<LocalDate, Double> loadRates(String currency) {
      final NavigableMap<LocalDate, Double> rates = new TreeMap<>();
      final Currencypair pair = currencypairJpaRepository.findByFromCurrencyAndToCurrency(currency, MAIN_CURRENCY);
      if (pair != null) {
        for (Historyquote quote : historyquoteJpaRepository
            .findByIdSecuritycurrencyOrderByDateAsc(pair.getIdSecuritycurrency())) {
          rates.put(quote.getDate(), quote.getClose());
        }
        return rates;
      }
      final Currencypair inverse = currencypairJpaRepository.findByFromCurrencyAndToCurrency(MAIN_CURRENCY, currency);
      if (inverse != null) {
        for (Historyquote quote : historyquoteJpaRepository
            .findByIdSecuritycurrencyOrderByDateAsc(inverse.getIdSecuritycurrency())) {
          if (quote.getClose() != 0.0) {
            rates.put(quote.getDate(), 1.0 / quote.getClose());
          }
        }
      }
      return rates;
    }

    private List<Historyquote> quotes(Integer idSecuritycurrency) {
      return quotesBySecurity.computeIfAbsent(idSecuritycurrency,
          historyquoteJpaRepository::findByIdSecuritycurrencyOrderByDateAsc);
    }

    @Override
    public PricePoint firstPrice(Integer idSecuritycurrency, int year) {
      final LocalDate start = LocalDate.of(year, 1, 1);
      final LocalDate latest = start.plusDays(PRICE_TOLERANCE_DAYS);
      for (Historyquote quote : quotes(idSecuritycurrency)) {
        if (!quote.getDate().isBefore(start)) {
          return quote.getDate().isAfter(latest) ? null : new PricePoint(quote.getDate(), quote.getClose());
        }
      }
      return null;
    }

    @Override
    public PricePoint lastPrice(Integer idSecuritycurrency, int year) {
      final LocalDate end = LocalDate.of(year, 12, 31);
      final LocalDate earliest = end.minusDays(PRICE_TOLERANCE_DAYS);
      Historyquote last = null;
      for (Historyquote quote : quotes(idSecuritycurrency)) {
        if (quote.getDate().isAfter(end)) {
          break;
        }
        last = quote;
      }
      return last == null || last.getDate().isBefore(earliest) ? null : new PricePoint(last.getDate(), last.getClose());
    }

    @Override
    public List<Distribution> distributions(Security security, int year) {
      final List<Distribution> distributions = new ArrayList<>();
      for (Dividend dividend : dividendsBySecurity.computeIfAbsent(security.getIdSecuritycurrency(),
          dividendJpaRepository::findByIdSecuritycurrencyOrderByExDateAsc)) {
        final LocalDate day = dividend.getPayDate() != null ? dividend.getPayDate() : dividend.getExDate();
        final Double amount = dividend.getAmountAdjusted() != null ? dividend.getAmountAdjusted()
            : dividend.getAmount();
        if (day != null && day.getYear() == year && amount != null) {
          distributions.add(new Distribution(day, amount,
              dividend.getCurrency() != null ? dividend.getCurrency() : security.getCurrency()));
        }
      }
      if (distributions.isEmpty() && security.getDistributionFrequency() != DistributionFrequency.DF_NONE) {
        return null;
      }
      return distributions;
    }

    @Override
    public Double baseRate(int year) {
      if (baseRates == null) {
        baseRates = new HashMap<>();
        final TaxCountry germany = taxCountryJpaRepository.findByCountryCode(COUNTRY_GERMANY);
        if (germany != null) {
          for (TaxYear taxYear : taxYearJpaRepository.findByIdTaxCountryOrderByTaxYearDesc(germany.getIdTaxCountry())) {
            if (taxYear.getFundBaseRate() != null) {
              baseRates.put(taxYear.getTaxYear().intValue(), taxYear.getFundBaseRate());
            }
          }
        }
      }
      return baseRates.get(year);
    }
  }
}
