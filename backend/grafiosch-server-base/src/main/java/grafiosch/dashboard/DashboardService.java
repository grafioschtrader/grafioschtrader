package grafiosch.dashboard;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import grafiosch.dto.DashboardDtos.*;
import grafiosch.entities.User;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Aggregates independent read-only handlers on the authenticated request thread. No cross-user cache. */
@Service
public class DashboardService {
  private static final Logger LOG = LoggerFactory.getLogger(DashboardService.class);
  private final DashboardLayoutRepository layouts;
  private final DashboardRegistry registry;

  public DashboardService(DashboardLayoutRepository layouts, DashboardRegistry registry) {
    this.layouts = layouts;
    this.registry = registry;
  }

  public Dashboard get(User user, boolean data) {
    var layout = layouts.read(user);
    List<JsonNode> widgets = layout.schemaVersion() == 1 ? layouts.eligible(layout, user) : List.of();
    List<Result> results = new ArrayList<>();
    Map<String, Result> memo = new HashMap<>();
    if (data)
      for (JsonNode w : widgets) {
        String key = w.path("type").asString() + ":" + w.path("config");
        Result r = memo.computeIfAbsent(key, _ -> load(w, user));
        results.add(new Result(w.path("instanceId").asString(), r.type(), r.status(), r.payload(), r.dataAsOf(),
            r.errorCode()));
      }
    return new Dashboard(layout.persisted(), layout.revision(), layout.schemaVersion(), user.getIdTenant(),
        LocalDateTime.now(), widgets, results,
        Math.max(0, DashboardLayoutRepository.MAX_WIDGETS - layout.all().size()));
  }

  public Catalogue catalogue(User user) {
    var layout = layouts.read(user);
    return new Catalogue(registry.eligible(user).stream().map(DashboardWidgetHandler::descriptor).toList(),
        Math.max(0, DashboardLayoutRepository.MAX_WIDGETS - layout.all().size()), registry.defaults(user));
  }

  /**
   * Loads one widget of the caller's layout, optionally with a transient configuration override.
   *
   * <p>
   * The override exists for settings a card changes while it is being read - a chosen date, for instance - which must
   * not become part of the saved layout. It is merged over the stored configuration and the merged object is put
   * through the handler's own {@code validate}, so an override can reach no further than a saved setting could. Nothing
   * is written.
   * </p>
   *
   * @param user     the authenticated caller, whose saved layout resolves the instance
   * @param id       instance identifier from the caller's eligible layout
   * @param override configuration properties replacing the stored ones for this read only, or null
   * @return the single widget result, carrying its own status and error code
   */
  public Result widget(User user, String id, JsonNode override) {
    var layout = layouts.read(user);
    if (layout.schemaVersion() != 1)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "DASHBOARD_SCHEMA");
    JsonNode widget = layouts.eligible(layout, user).stream().filter(w -> id.equals(w.path("instanceId").asString()))
        .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return load(widget, user, override);
  }

  private Result load(JsonNode widget, User user) {
    return load(widget, user, null);
  }

  private Result load(JsonNode widget, User user, JsonNode override) {
    String id = widget.path("instanceId").asString(), type = widget.path("type").asString();
    long started = System.nanoTime();
    try {
      var handler = registry.handler(type, user);
      if (handler == null)
        return new Result(id, type, "UNAVAILABLE", null, LocalDateTime.now(), "DASHBOARD_UNAVAILABLE");
      handler.validate(widget.path("config"));
      if (override != null)
        handler.validateTransient(override);
      Payload payload = handler.load(user, merge(widget.path("config"), override));
      return new Result(id, type, payload.isEmpty() ? "EMPTY" : "OK", payload, LocalDateTime.now(), null);
    } catch (RuntimeException ex) {
      LOG.warn("Dashboard widget {} failed ({})", type, ex.getClass().getSimpleName());
      return new Result(id, type, "ERROR", null, LocalDateTime.now(), "DASHBOARD_LOAD_ERROR");
    } finally {
      LOG.debug("Dashboard widget {} completed in {} ms", type, (System.nanoTime() - started) / 1_000_000);
    }
  }

  /**
   * Copies the stored configuration and replaces the properties the override names, leaving the saved layout untouched.
   */
  private JsonNode merge(JsonNode config, JsonNode override) {
    if (override == null || !override.isObject())
      return config;
    if (!config.isObject())
      return override;
    ObjectNode merged = ((ObjectNode) config).deepCopy();
    override.propertyNames().forEach(name -> merged.set(name, override.get(name)));
    return merged;
  }
}
