package grafioschtrader.websocket;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import grafioschtrader.entities.User;
import grafioschtrader.reports.WatchlistReport;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyGroup;
import grafioschtrader.security.UserAuthentication;;

@Controller
@ConditionalOnExpression("${gt.use.websocket:false}")
public class WebSocketController {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  WatchlistReport watchlistReport;

  @Autowired
  private SimpMessageSendingOperations messagingTemplate;

  @MessageMapping("/ws/watchlist")
  @SendToUser("/queue/reply")
  public String processMessageFromClient(Principal principal, QueryParam queryParam) throws Exception {
    log.info("Websocket - idWachtlist: {}", queryParam);
    final User user = (User) ((UserAuthentication) principal).getDetails();
    SecuritycurrencyGroup securitycurrencyGroup = watchlistReport
        .getWatchlistwithPeriodPerformance(queryParam.idWatchlist, user.getIdTenant(), queryParam.daysFrameDate);
    messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/security", securitycurrencyGroup);
    return null;
  }

}

class QueryParam {
  public Integer idWatchlist;
  public Integer daysFrameDate;

  @Override
  public String toString() {
    return "QueryParam [idWatchlist=" + idWatchlist + ", daysFrameDate=" + daysFrameDate + "]";
  }

}