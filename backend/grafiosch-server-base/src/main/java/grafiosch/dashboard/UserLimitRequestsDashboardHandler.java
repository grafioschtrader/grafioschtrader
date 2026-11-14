package grafiosch.dashboard;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import grafiosch.dto.DashboardDtos.*;
import grafiosch.entities.ProposeChangeField;
import grafiosch.entities.ProposeUserTask;
import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.repository.DashboardSummaryJpaRepository;
import grafiosch.usertask.UserTaskType;
import tools.jackson.databind.JsonNode;

/**
 * Administrator-only summary of the requests user administration shows: the limit-change requests attached as
 * userChangeLimitProposeList and the release requests of locked-out users attached as userChangePropose. A release
 * request is listed with the violation counter the user exceeded in the entity column.
 */
@Component
@Order(30)
public class UserLimitRequestsDashboardHandler extends AbstractDashboardListHandler {
  private final DashboardSummaryJpaRepository repository;

  public UserLimitRequestsDashboardHandler(DashboardSummaryJpaRepository repository) {
    super("USER_LIMIT_REQUESTS");
    this.repository = repository;
  }

  @Override
  public boolean available(User user) {
    return Role.ROLE_ADMIN.equals(user.getMostPrivilegedRole());
  }

  @Override
  public Payload load(User user, JsonNode config) {
    var summaries = repository.limitRequests(config.path("maxRows").asInt());
    Map<Integer, ProposeUserTask> details = summaries.isEmpty() ? Map.of()
        : repository.limitRequestDetails(summaries.stream().map(DashboardSummaryJpaRepository.LimitRow::getId).toList())
            .stream().collect(Collectors.toMap(ProposeUserTask::getIdProposeRequest, p -> p));
    List<Row> rows = summaries.stream().map(r -> {
      ProposeUserTask p = details.get(r.getId());
      Object limit = field(p, "dayLimit");
      return new Row(r.getId(), r.getNickname(), null, entityText(p), p == null ? null : p.getNoteRequest(),
          r.getCreationTime(), limit instanceof Number n ? n.intValue() : null, text(field(p, "untilDate")));
    }).toList();
    return new Payload(List.of(
        new SummaryList("DASHBOARD_USER_LIMIT_REQUESTS", summaries.isEmpty() ? 0 : summaries.getFirst().getTotalCount(),
            summaries.isEmpty() ? 0L : summaries.getFirst().getUserCount(), "/mainview/useradmin", rows)));
  }

  /**
   * Entity column of a row. A limit-change request names the entity whose limit is requested. A release request has no
   * such entity; it names the violation counter that locked the user out, as an NLS key of its own.
   */
  private String entityText(ProposeUserTask p) {
    if (p == null) {
      return null;
    }
    if (p.getUserTaskType() != UserTaskType.RELEASE_LOGOUT) {
      return text(field(p, "entity"));
    }
    String violatedCounter = p.getProposeChangeFieldList().stream().map(ProposeChangeField::getField).findFirst()
        .orElse(null);
    return switch (violatedCounter) {
    case User.LIMIT_REQUEST_EXCEED_COUNT -> "TOO_MANY_VIOLATIONS_LIMIT_REQUEST_EXCEED_COUNT";
    case User.SECURITY_BREACH_COUNT -> "TOO_MANY_VIOLATIONS_SECURITY_BREACH_COUNT";
    case null, default -> "TOO_MANY_VIOLATIONS";
    };
  }

  private Object field(ProposeUserTask p, String name) {
    return p == null ? null
        : p.getProposeChangeFieldList().stream().filter(f -> name.equals(f.getField()))
            .map(f -> f.getValueDesarialized()).filter(java.util.Objects::nonNull).findFirst().orElse(null);
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }
}
