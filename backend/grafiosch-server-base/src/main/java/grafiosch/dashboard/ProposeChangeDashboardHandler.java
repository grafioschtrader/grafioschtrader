package grafiosch.dashboard;

import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import grafiosch.dto.DashboardDtos.Payload;
import grafiosch.entities.User;
import grafiosch.repository.DashboardProposalRepository;
import tools.jackson.databind.JsonNode;

/** Separate attention and submitted counts; their overlap is intentional. */
@Component
@Order(20)
public class ProposeChangeDashboardHandler extends AbstractDashboardListHandler {
  private final DashboardProposalRepository repository;

  public ProposeChangeDashboardHandler(DashboardProposalRepository repository) {
    super("PROPOSE_CHANGE_OPEN");
    this.repository = repository;
  }

  @Override
  public Payload load(User user, JsonNode config) {
    int maxRows = config.path("maxRows").asInt();
    return new Payload(List.of(repository.proposals(user, false, maxRows), repository.proposals(user, true, maxRows)));
  }
}
