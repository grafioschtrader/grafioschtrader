package grafioschtrader.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Security;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;

/**
 * Share of each position and group in the report total, as shown in the holdings reports. The total is the net value,
 * so a loss making margin position has a negative share and the remaining positions add up to more than 100.
 */
class SecurityPositionShareOfTotalTest {

  @Test
  @DisplayName("Security 10,000 and CFD at -5,000 give 200% and -100% of a total of 5,000")
  void lossMakingMarginPositionHasNegativeShare() {
    var grand = new SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<String>>("CHF", 2);
    var equities = group(grand, "EQUITIES", position(1, 10_000));
    var cfd = position(2, -5_000);
    var cfds = group(grand, "CFD", cfd);
    grand.roundGrandTotals();

    grand.calcShareOfTotalPercentages();

    assertEquals(5_000, grand.grandAccountValueSecurityMC);
    assertEquals(200.0, equities.securityPositionSummaryList.getFirst().getShareOfTotalPercentage());
    assertEquals(-100.0, cfd.getShareOfTotalPercentage());
    assertEquals(200.0, equities.getGroupShareOfTotalPercentage());
    assertEquals(-100.0, cfds.getGroupShareOfTotalPercentage());
    assertEquals(100.0, grand.getGrandShareOfTotalPercentage());
  }

  @Test
  @DisplayName("Shares of all positions add up to 100")
  void sharesAddUpToHundred() {
    var grand = new SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<String>>("CHF", 2);
    group(grand, "EQUITIES", position(1, 3_000), position(2, 1_000));
    group(grand, "CASH", position(-1, 2_000));
    grand.roundGrandTotals();

    grand.calcShareOfTotalPercentages();

    double sum = grand.securityPositionGroupSummaryList.stream()
        .flatMap(group -> group.securityPositionSummaryList.stream())
        .mapToDouble(SecurityPositionSummary::getShareOfTotalPercentage).sum();
    assertEquals(100.0, sum, 0.0001);
    assertEquals(50.0, grand.securityPositionGroupSummaryList.getFirst().securityPositionSummaryList.getFirst()
        .getShareOfTotalPercentage());
  }

  @Test
  @DisplayName("A total that is not positive leaves every share empty")
  void nonPositiveTotalLeavesSharesNull() {
    var grand = new SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<String>>("CHF", 2);
    var stock = position(1, 1_000);
    var cfd = position(2, -3_000);
    var equities = group(grand, "EQUITIES", stock, cfd);
    grand.roundGrandTotals();

    grand.calcShareOfTotalPercentages();

    assertNull(stock.getShareOfTotalPercentage());
    assertNull(cfd.getShareOfTotalPercentage());
    assertNull(equities.getGroupShareOfTotalPercentage());
    assertNull(grand.getGrandShareOfTotalPercentage());
  }

  private SecurityPositionDynamicGroupSummary<String> group(
      SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<String>> grand, String name,
      SecurityPositionSummary... positions) {
    var group = new SecurityPositionDynamicGroupSummary<String>(name);
    for (SecurityPositionSummary position : positions) {
      group.addToGroupSummaryAndCalcGroupTotals(position);
    }
    grand.calcGrandTotal(group);
    return group;
  }

  private SecurityPositionSummary position(int id, double accountValueMC) {
    var security = new Security();
    security.setIdSecuritycurrency(id);
    security.setName("Security " + id);
    security.setCurrency("CHF");
    var position = new SecurityPositionSummary("CHF", security, Map.of("CHF", 2));
    position.accountValueSecurityMC = accountValueMC;
    return position;
  }
}
