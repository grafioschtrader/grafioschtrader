package grafioschtrader.dashboard;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import grafiosch.dashboard.DashboardLayoutRepository;
import grafiosch.dashboard.DashboardWidgetHandler;
import grafiosch.dto.DashboardDtos.Descriptor;
import grafiosch.dto.DashboardDtos.Payload;
import grafiosch.dynamic.model.DynamicModelHelper;
import grafiosch.entities.User;
import grafioschtrader.dto.HoldingMoversConfig;
import grafioschtrader.reports.HoldingMoversReport;
import grafioschtrader.reports.HoldingMoversReport.Direction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared behaviour of the two cards ranking held instruments by how far they moved: one shows the top of the ranking,
 * the other the bottom, and nothing else differs.
 *
 * <p>
 * Both delegate to one report, so the two cards can never disagree about a figure that appears on both - the same
 * reason the alert path shares its position calculation with the reports rather than repeating it.
 * </p>
 */
public abstract class HoldingMoversDashboardHandler implements DashboardWidgetHandler {

  /** The one setting a card may change per read. Its value is an ISO date. */
  private static final String CHOSEN_DATE = "chosenDate";
  private static final int MAX_TOP_N = 5;

  private final String type;
  private final Direction direction;
  private final HoldingMoversReport report;
  private final ObjectMapper mapper;

  protected HoldingMoversDashboardHandler(String type, Direction direction, HoldingMoversReport report,
      ObjectMapper mapper) {
    this.type = type;
    this.direction = direction;
    this.report = report;
    this.mapper = mapper;
  }

  @Override
  public Descriptor descriptor() {
    return new Descriptor(type, "DASHBOARD_" + type, "DASHBOARD_" + type + "_HELP", "HALF", Map.of("topN", 3),
        DynamicModelHelper.getFormDefinitionOfEntityClass(HoldingMoversConfig.class, 1));
  }

  /** Investment data needs a client; a user who has not set one up yet has nothing to rank. */
  @Override
  public boolean available(User user) {
    return user.getIdTenant() != null;
  }

  /**
   * Accepts only the row count. The bounds repeat those of {@link HoldingMoversConfig} because its annotations describe
   * the form for the client and are never executed against the stored settings.
   */
  @Override
  public void validate(JsonNode config) {
    JsonNode topN = config.path("topN");
    if (!config.isObject() || config.size() != 1 || !topN.isIntegralNumber() || !topN.canConvertToInt()
        || topN.asInt() < 1 || topN.asInt() > MAX_TOP_N) {
      DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
    }
  }

  /**
   * Accepts the chosen session of the third branch, and nothing else. A date outside the trading calendar is not an
   * error here - the report moves it back to the nearest session and reports which one it used.
   */
  @Override
  public void validateTransient(JsonNode override) {
    if (override.size() != 1 || !override.path(CHOSEN_DATE).isString()) {
      DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
    }
    try {
      LocalDate.parse(override.path(CHOSEN_DATE).asString());
    } catch (DateTimeParseException ex) {
      DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
    }
  }

  @Override
  public Payload load(User user, JsonNode config) {
    JsonNode chosen = config.path(CHOSEN_DATE);
    LocalDate chosenDate = chosen.isString() ? LocalDate.parse(chosen.asString()) : null;
    return new Payload(List.of(),
        mapper.valueToTree(report.getMovers(user.getIdTenant(), config.path("topN").asInt(3), chosenDate, direction)));
  }
}
