package grafiosch.dashboard;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import grafiosch.dto.DashboardDtos.Save;
import grafiosch.entities.User;
import grafiosch.entities.UserDashboard;
import grafiosch.repository.UserDashboardJpaRepository;
import grafiosch.service.DailyLimitService;
import grafiosch.types.OperationType;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Owns all layout writes and their validation. Locking the existing user row also serializes competing first saves. */
@Repository
public class DashboardLayoutRepository {
  public static final int MAX_WIDGETS = 24;
  public static final int MAX_BYTES = 65536;
  private final UserDashboardJpaRepository repository;
  private final DashboardRegistry registry;
  private final ObjectMapper mapper;
  private final EntityManager em;
  private final DailyLimitService limits;

  public record Layout(boolean persisted, Long revision, int schemaVersion, List<JsonNode> all) {
  }

  public DashboardLayoutRepository(UserDashboardJpaRepository repository, DashboardRegistry registry,
      ObjectMapper mapper, EntityManager em, DailyLimitService limits) {
    this.repository = repository;
    this.registry = registry;
    this.mapper = mapper;
    this.em = em;
    this.limits = limits;
  }

  public Layout read(User user) {
    registry.checkEnabled();
    return repository.findById(user.getIdUser()).map(this::decode)
        .orElseGet(() -> new Layout(false, null, 1, registry.defaults(user)));
  }

  private Layout decode(UserDashboard row) {
    JsonNode document = mapper.readTree(row.getLayout());
    List<JsonNode> widgets = new ArrayList<>();
    document.path("widgets").forEach(widgets::add);
    return new Layout(true, row.getRevision(), document.path("schemaVersion").asInt(), widgets);
  }

  public List<JsonNode> eligible(Layout layout, User user) {
    return layout.all().stream().filter(w -> available(w, user)).toList();
  }

  private boolean available(JsonNode widget, User user) {
    return registry.handler(widget.path("type").asString(), user) != null;
  }

  @Transactional
  public Layout save(User user, Save request) {
    registry.checkEnabled();
    // Lock a stable parent, including when there is no dashboard yet. Do not load or modify the tenant-bearing User.
    em.createNativeQuery("SELECT id_user FROM user WHERE id_user = ?1 FOR UPDATE", Integer.class)
        .setParameter(1, user.getIdUser()).getSingleResult();
    Layout previous = read(user);
    if (!Objects.equals(previous.revision(), request.expectedRevision())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "DASHBOARD_CONFLICT");
    }
    if (previous.schemaVersion() != 1 || request.schemaVersion() != 1)
      invalid("DASHBOARD_SCHEMA");
    if (request.widgets() == null || request.widgets().size() > MAX_WIDGETS)
      invalid("DASHBOARD_INVALID");
    List<JsonNode> widgets;
    if (request.resetToDefault()) {
      if (!request.widgets().isEmpty())
        invalid("DASHBOARD_INVALID");
      widgets = registry.defaults(user);
    } else {
      validateSubmitted(request.widgets(), user);
      widgets = merge(previous.all(), request.widgets(), user);
    }
    String json = encode(widgets);
    limits.check(user, UserDashboard.class.getSimpleName(), 1);
    UserDashboard row;
    if (previous.persisted()) {
      if (repository.compareAndSave(user.getIdUser(), json, previous.revision()) != 1) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "DASHBOARD_CONFLICT");
      }
      row = repository.findById(user.getIdUser()).orElseThrow();
      em.refresh(row);
    } else {
      row = new UserDashboard();
      row.setIdUser(user.getIdUser());
      row.setLayout(json);
      repository.saveAndFlush(row);
    }
    limits.log(user.getIdUser(), UserDashboard.class.getSimpleName(),
        previous.persisted() ? OperationType.UPDATE : OperationType.ADD, 1);
    return decode(row);
  }

  private void validateSubmitted(List<JsonNode> widgets, User user) {
    Set<String> ids = new HashSet<>();
    Set<String> types = new HashSet<>();
    for (JsonNode w : widgets) {
      if (w == null || !w.isObject() || !Set.of("instanceId", "type", "width", "config").containsAll(w.propertyNames()))
        invalid("DASHBOARD_INVALID");
      String id = w.path("instanceId").asString();
      try {
        if (!UUID.fromString(id).toString().equals(id))
          invalid("DASHBOARD_INVALID");
      } catch (IllegalArgumentException _) {
        invalid("DASHBOARD_INVALID");
      }
      if (!ids.add(id) || !Set.of("THIRD", "HALF", "TWO_THIRDS", "FULL").contains(w.path("width").asString()))
        invalid("DASHBOARD_INVALID");
      if (!types.add(w.path("type").asString()))
        invalid("DASHBOARD_INVALID");
      DashboardWidgetHandler handler = registry.handler(w.path("type").asString(), user);
      if (handler == null)
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "DASHBOARD_UNAVAILABLE");
      handler.validate(w.path("config"));
    }
  }

  private List<JsonNode> merge(List<JsonNode> saved, List<JsonNode> submitted, User user) {
    List<JsonNode> result = new ArrayList<>();
    int index = 0;
    for (JsonNode old : saved) {
      if (!available(old, user))
        result.add(old);
      else if (index < submitted.size())
        result.add(submitted.get(index++));
    }
    result.addAll(submitted.subList(index, submitted.size()));
    return result;
  }

  String encode(List<JsonNode> widgets) {
    Set<String> ids = new HashSet<>();
    Set<String> types = new HashSet<>();
    if (widgets.size() > MAX_WIDGETS || widgets.stream().anyMatch(w -> !ids.add(w.path("instanceId").asString())))
      invalid("DASHBOARD_CAPACITY");
    // One instance per type, including any preserved widget whose handler is currently unavailable.
    if (widgets.stream().anyMatch(w -> !types.add(w.path("type").asString())))
      invalid("DASHBOARD_INVALID");
    String json = mapper.writeValueAsString(Map.of("schemaVersion", 1, "widgets", widgets));
    if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
      invalid("DASHBOARD_CAPACITY");
    return json;
  }

  public static void invalid(String code) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
  }
}
