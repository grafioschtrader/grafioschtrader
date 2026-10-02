package grafioschtrader.reportviews.performance;

import java.time.LocalDate;

/**
 * Net external flow of one calendar day, netted over all cash accounts of the scope (tenant or portfolio). A transfer
 * between two accounts of the same scope nets to zero and does not appear.
 */
public interface IDailyExternalFlow {

  /** Booking day of the flow. */
  LocalDate getFlowDate();

  /** Deposits less withdrawals of that day in main currency; a withdrawal is negative. */
  double getFlowMC();
}
