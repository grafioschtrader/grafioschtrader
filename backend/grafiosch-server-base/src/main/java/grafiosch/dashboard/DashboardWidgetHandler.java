package grafiosch.dashboard;

import grafiosch.dto.DashboardDtos.Descriptor;
import grafiosch.dto.DashboardDtos.Payload;
import grafiosch.entities.User;
import tools.jackson.databind.JsonNode;

/** Extension point for application supplied, authorized and read-only widget loaders. */
public interface DashboardWidgetHandler {
  Descriptor descriptor();

  boolean available(User user);

  /** Checks the configuration that may be saved into a layout. Anything not accepted here can never be persisted. */
  void validate(JsonNode config);

  /**
   * Checks settings a card changes for a single read - a chosen date, for instance - which are merged over the saved
   * configuration and then discarded.
   *
   * <p>
   * This is deliberately separate from {@link #validate}: the save path runs that one, so a property accepted there
   * becomes storable. A widget with no such settings inherits the default and refuses every override.
   * </p>
   *
   * @param override the properties of this read alone, never null and always an object
   */
  default void validateTransient(JsonNode override) {
    DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
  }

  Payload load(User user, JsonNode config);
}
