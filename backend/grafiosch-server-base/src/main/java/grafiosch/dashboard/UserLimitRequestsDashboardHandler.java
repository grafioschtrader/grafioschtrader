package grafiosch.dashboard;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import grafiosch.dto.DashboardDtos.*;
import grafiosch.entities.ProposeUserTask;
import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.repository.DashboardSummaryJpaRepository;
import tools.jackson.databind.JsonNode;

/** Administrator-only summary of the same requests attached as userChangeLimitProposeList. */
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
      return new Row(r.getId(), r.getNickname(), null, text(field(p, "entity")), p == null ? null : p.getNoteRequest(),
          r.getCreationTime(), limit instanceof Number n ? n.intValue() : null, text(field(p, "untilDate")));
    }).toList();
    return new Payload(List.of(
        new SummaryList("DASHBOARD_USER_LIMIT_REQUESTS", summaries.isEmpty() ? 0 : summaries.getFirst().getTotalCount(),
            summaries.isEmpty() ? 0L : summaries.getFirst().getUserCount(), "/mainview/useradmin", rows)));
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
