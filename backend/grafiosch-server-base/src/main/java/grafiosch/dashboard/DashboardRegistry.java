package grafiosch.dashboard;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import grafiosch.config.BaseFeatureConfig;
import grafiosch.entities.User;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Resolves only registered handlers; feature and role checks are repeated on every request. */
@Component
public class DashboardRegistry {
  private final Map<String, DashboardWidgetHandler> handlers = new LinkedHashMap<>();
  private final BaseFeatureConfig features;
  private final ObjectMapper mapper;
  private final Set<String> unknownTypes = ConcurrentHashMap.newKeySet();

  public DashboardRegistry(List<DashboardWidgetHandler> handlers, BaseFeatureConfig features, ObjectMapper mapper) {
    handlers.forEach(handler -> {
      if (this.handlers.putIfAbsent(handler.descriptor().type(), handler) != null) {
        throw new IllegalStateException("Duplicate dashboard widget type");
      }
    });
    this.features = features;
    this.mapper = mapper;
  }

  public void checkEnabled() {
    if (!features.isDashboard())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "DASHBOARD_DISABLED");
  }

  public DashboardWidgetHandler handler(String type, User user) {
    DashboardWidgetHandler handler = handlers.get(type);
    if (handler == null && type != null && unknownTypes.add(type)) {
      LoggerFactory.getLogger(DashboardRegistry.class).warn("Unrecognized stored dashboard widget type");
    }
    return handler != null && handler.available(user) ? handler : null;
  }

  public List<DashboardWidgetHandler> eligible(User user) {
    return handlers.values().stream().filter(h -> h.available(user)).toList();
  }

  public List<JsonNode> defaults(User user) {
    return eligible(user).stream().map(h -> {
      var d = h.descriptor();
      String id = UUID
          .nameUUIDFromBytes((user.getIdUser() + ":" + d.type() + ":default").getBytes(StandardCharsets.UTF_8))
          .toString();
      return (JsonNode) mapper.valueToTree(
          Map.of("instanceId", id, "type", d.type(), "width", d.defaultWidth(), "config", d.defaultConfig()));
    }).limit(DashboardLayoutRepository.MAX_WIDGETS).toList();
  }
}
