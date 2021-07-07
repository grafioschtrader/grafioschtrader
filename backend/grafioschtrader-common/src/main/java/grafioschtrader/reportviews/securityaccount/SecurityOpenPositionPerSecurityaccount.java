package grafioschtrader.reportviews.securityaccount;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains all Securityaccounts with the open positions.
 *
 * @author Hugo Graf
 *
 */
public class SecurityOpenPositionPerSecurityaccount {
  public SecurityPositionSummary securityPositionSummary;
  public List<SecurityaccountOpenPositionUnits> securityaccountOpenPositionUnitsList = new ArrayList<>();

}
