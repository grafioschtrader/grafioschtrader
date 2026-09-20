package grafioschtrader.dashboard;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import grafioschtrader.reports.HoldingMoversReport;
import grafioschtrader.reports.HoldingMoversReport.Direction;
import tools.jackson.databind.ObjectMapper;

/** The held instruments that gained the most, over each of the three sessions the card offers. */
@Component
@Order(100)
public class HoldingWinnersDashboardHandler extends HoldingMoversDashboardHandler {

  public HoldingWinnersDashboardHandler(HoldingMoversReport report, ObjectMapper mapper) {
    super("HOLDING_WINNERS", Direction.WINNERS, report, mapper);
  }
}
