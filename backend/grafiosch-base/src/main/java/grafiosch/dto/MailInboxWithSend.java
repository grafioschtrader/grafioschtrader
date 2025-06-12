package grafiosch.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
Contains the complete mail inbox data for a user, including both individual messages and conversation thread 
statistics. This combines the user's mail messages with metadata about role-based conversation activity.""")
public class MailInboxWithSend {

  @Schema(description = """
      List of mail messages visible to the current user. This includes direct messages sent to or from the user, 
      role-based messages for roles the user belongs to, and excludes messages marked as hidden/deleted by the user. 
      Messages are ordered by message ID for consistent display.
      """)
  public List<MailSendRecvDTO> mailSendRecvList;
 
  @Schema(description = """
      Map containing reply counts for role-based conversation threads. The key is the conversation thread ID 
      (idReplyToLocal) and the value is the number of sent replies in that thread. This helps track activity and 
      provide notification counts for role-based discussions that the user participates in.""")
  public Map<Integer, Integer> countMsgMap;

  public MailInboxWithSend(List<MailSendRecvDTO> mailSendRecvList, Map<Integer, Integer> countMsgMap) {
    this.mailSendRecvList = mailSendRecvList;
    this.countMsgMap = countMsgMap;
  }

}
