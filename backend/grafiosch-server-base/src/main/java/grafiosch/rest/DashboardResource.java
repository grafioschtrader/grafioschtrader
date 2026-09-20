package grafiosch.rest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import grafiosch.dashboard.DashboardLayoutRepository;
import grafiosch.dashboard.DashboardService;
import grafiosch.dto.DashboardDtos.*;
import grafiosch.entities.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Personal dashboard endpoints. Ownership always comes from authentication. */
@RestController
@Tag(name = "Dashboard", description = "Personal layout and read-only attention summaries")
public class DashboardResource {
  /**
   * Upper bound of the transient configuration override, generous for a handful of settings and far below a payload.
   */
  private static final int MAX_OVERRIDE_BYTES = 2048;
  private final DashboardService service;
  private final DashboardLayoutRepository layouts;
  private final ObjectMapper mapper;

  public DashboardResource(DashboardService service, DashboardLayoutRepository layouts, ObjectMapper mapper) {
    this.service = service;
    this.layouts = layouts;
    this.mapper = mapper;
  }

  private User user() {
    return (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
  }

  /** Preserve dashboard validation/conflict statuses instead of the global generic exception mapping. */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> status(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode())
        .body(Map.of("errorCode", error.getReason() == null ? "DASHBOARD_UNAVAILABLE" : error.getReason()));
  }

  @GetMapping("/api/dashboard")
  public Dashboard get() {
    return service.get(user(), true);
  }

  @GetMapping("/api/dashboard/catalogue")
  public Catalogue catalogue() {
    return service.catalogue(user());
  }

  /**
   * Reloads a single widget, optionally with settings that apply to this read alone.
   *
   * @param instanceId instance identifier from the caller's own layout
   * @param config     JSON object of configuration properties valid for this read only; never persisted
   */
  @GetMapping("/api/dashboard/widget/{instanceId}")
  public Result widget(@PathVariable String instanceId, @RequestParam(required = false) String config) {
    return service.widget(user(), instanceId, override(config));
  }

  /** Parses the transient override, rejecting anything that is not a bounded JSON object. */
  private JsonNode override(String config) {
    if (config == null || config.isBlank())
      return null;
    if (config.getBytes(StandardCharsets.UTF_8).length > MAX_OVERRIDE_BYTES)
      DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
    JsonNode node;
    try {
      node = mapper.readTree(config);
    } catch (JacksonException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DASHBOARD_INVALID");
    }
    if (!node.isObject())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DASHBOARD_INVALID");
    return node;
  }

  @PutMapping("/api/userdashboard")
  public Dashboard save(@RequestBody Save request) {
    User user = user();
    layouts.save(user, request);
    return service.get(user, false);
  }
}
