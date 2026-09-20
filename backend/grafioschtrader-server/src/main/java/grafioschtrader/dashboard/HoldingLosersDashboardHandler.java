package grafioschtrader.dashboard;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import grafioschtrader.reports.HoldingMoversReport;
import grafioschtrader.reports.HoldingMoversReport.Direction;
import tools.jackson.databind.ObjectMapper;

/** The held instruments that lost the most, over each of the three sessions the card offers. */
@Component
@Order(110)
public class HoldingLosersDashboardHandler extends HoldingMoversDashboardHandler {

  public HoldingLosersDashboardHandler(HoldingMoversReport report, ObjectMapper mapper) {
    super("HOLDING_LOSERS", Direction.LOSERS, report, mapper);
  }
}
