package grafiosch.dashboard;

import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import grafiosch.dto.DashboardDtos.*;
import grafiosch.entities.User;
import grafiosch.repository.DashboardSummaryJpaRepository;
import tools.jackson.databind.JsonNode;

/** Inbox projection only: never calls the mailbox read-state write path. */
@Component
@Order(10)
public class UnreadMailDashboardHandler extends AbstractDashboardListHandler {
  private final DashboardSummaryJpaRepository repository;

  public UnreadMailDashboardHandler(DashboardSummaryJpaRepository repository) {
    super("UNREAD_MAIL");
    this.repository = repository;
  }

  @Override
  public Payload load(User user, JsonNode config) {
    var rows = repository.unreadMail(user.getIdUser(), config.path("maxRows").asInt());
    return new Payload(List.of(new SummaryList("DASHBOARD_UNREAD_MAIL",
        rows.isEmpty() ? 0 : rows.getFirst().getTotalCount(), null, "/mainview/usermessage/mailsendrecv",
        rows.stream()
            .map(r -> new Row(r.getId(), r.getNickname(), r.getSubject(), null, null, r.getCreationTime(), null, null))
            .toList())));
  }
}
