package grafioschtrader.tax.germany;

import java.util.List;

import grafioschtrader.reportviews.germantax.GermanTaxYearReport.PortfolioAssignment;
import grafioschtrader.types.TaxWithholding;

/**
 * The portfolios of the tenant as the German tax calculation sees them: which portfolio a security account or a cash
 * account belongs to and what withholding is assigned to the portfolio.
 */
public interface GermanTaxPortfolios {

  /** The portfolio of a security account, null when it is unknown. */
  Integer portfolioOfSecurityaccount(Integer idSecurityaccount);

  /** The portfolio of a cash account, null when it is unknown. */
  Integer portfolioOfCashaccount(Integer idCashaccount);

  /** The withholding assigned to a portfolio, null while it is not assigned. */
  TaxWithholding withholding(Integer idPortfolio);

  /** All portfolios with their assignment, for the overview of the report. */
  List<PortfolioAssignment> assignments();
}
