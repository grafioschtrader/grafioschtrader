package grafiosch.dashboard;

import java.util.Map;

import grafiosch.dto.DashboardDtos.Descriptor;
import grafiosch.dto.DashboardListConfig;
import grafiosch.dynamic.model.DynamicModelHelper;
import grafiosch.entities.User;
import tools.jackson.databind.JsonNode;

/** Common form metadata and strict settings validation for bounded attention lists. */
public abstract class AbstractDashboardListHandler implements DashboardWidgetHandler {
  private final String type;

  protected AbstractDashboardListHandler(String type) {
    this.type = type;
  }

  @Override
  public Descriptor descriptor() {
    return new Descriptor(type, "DASHBOARD_" + type, "DASHBOARD_" + type + "_HELP", "HALF", Map.of("maxRows", 5),
        DynamicModelHelper.getFormDefinitionOfEntityClass(DashboardListConfig.class, 1));
  }

  @Override
  public boolean available(User user) {
    return true;
  }

  @Override
  public void validate(JsonNode config) {
    if (!config.isObject() || config.size() != 1 || !config.path("maxRows").isIntegralNumber()
        || !config.path("maxRows").canConvertToInt() || config.path("maxRows").asInt() < 1
        || config.path("maxRows").asInt() > 20)
      DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
  }
}
