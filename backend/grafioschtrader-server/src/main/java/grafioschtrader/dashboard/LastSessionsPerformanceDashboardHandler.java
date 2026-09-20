package grafioschtrader.dashboard;

import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import grafiosch.dashboard.DashboardLayoutRepository;
import grafiosch.dashboard.DashboardWidgetHandler;
import grafiosch.dto.DashboardDtos.Descriptor;
import grafiosch.dto.DashboardDtos.Payload;
import grafiosch.dynamic.model.DynamicModelHelper;
import grafiosch.entities.User;
import grafioschtrader.dto.LastSessionsPerformanceConfig;
import grafioschtrader.reports.PerformanceReport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The card reporting what the client, or one of its portfolios, gained or lost over the last completed sessions.
 *
 * <p>
 * It delegates to the same report the period performance page uses, so the two can never disagree about a figure that
 * appears on both, and it deliberately picks its own dates: a card has no date fields a reader could correct, so a
 * range refused because one of its ends carries incomplete prices would only produce an error nobody asked for.
 * </p>
 */
@Component
@Order(120)
public class LastSessionsPerformanceDashboardHandler implements DashboardWidgetHandler {

  private static final String TYPE = "PERFORMANCE_LAST_SESSIONS";

  /** The one setting a card may change per read. Its value is a portfolio id, or null for the whole client. */
  private static final String ID_PORTFOLIO = "idPortfolio";

  private static final String DAYS = "days";
  private static final int MIN_DAYS = 2;
  private static final int MAX_DAYS = 10;
  private static final int DEFAULT_DAYS = 5;

  private final PerformanceReport performanceReport;
  private final ObjectMapper mapper;

  public LastSessionsPerformanceDashboardHandler(PerformanceReport performanceReport, ObjectMapper mapper) {
    this.performanceReport = performanceReport;
    this.mapper = mapper;
  }

  @Override
  public Descriptor descriptor() {
    return new Descriptor(TYPE, "DASHBOARD_" + TYPE, "DASHBOARD_" + TYPE + "_HELP", "HALF", Map.of(DAYS, DEFAULT_DAYS),
        DynamicModelHelper.getFormDefinitionOfEntityClass(LastSessionsPerformanceConfig.class, 1));
  }

  /** Investment data needs a client; a user who has not set one up yet has nothing to report on. */
  @Override
  public boolean available(User user) {
    return user.getIdTenant() != null;
  }

  /**
   * Accepts only the number of sessions. The bounds repeat those of {@link LastSessionsPerformanceConfig} because its
   * annotations describe the form for the client and are never executed against the stored settings.
   */
  @Override
  public void validate(JsonNode config) {
    JsonNode days = config.path(DAYS);
    if (!config.isObject() || config.size() != 1 || !days.isIntegralNumber() || !days.canConvertToInt()
        || days.asInt() < MIN_DAYS || days.asInt() > MAX_DAYS) {
      DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
    }
  }

  /**
   * Accepts the portfolio of a single read, and nothing else. A portfolio of another client is not rejected here but by
   * the report, which resolves it against the tenant of the caller.
   */
  @Override
  public void validateTransient(JsonNode override) {
    JsonNode idPortfolio = override.path(ID_PORTFOLIO);
    if (override.size() != 1 || !(idPortfolio.isNull() || idPortfolio.isIntegralNumber())) {
      DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
    }
  }

  @Override
  public Payload load(User user, JsonNode config) {
    JsonNode idPortfolio = config.path(ID_PORTFOLIO);
    try {
      return new Payload(List.of(), mapper.valueToTree(performanceReport.getLastSessionsPerformance(user.getIdTenant(),
          idPortfolio.isIntegralNumber() ? idPortfolio.asInt() : null, config.path(DAYS).asInt(DEFAULT_DAYS))));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
