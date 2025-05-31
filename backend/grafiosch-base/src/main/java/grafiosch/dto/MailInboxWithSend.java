package grafiosch.dto;

import java.util.List;
import java.util.Map;

public class MailInboxWithSend {

  public List<MailSendRecvDTO> mailSendRecvList;
  public Map<Integer, Integer> countMsgMap;

  public MailInboxWithSend(List<MailSendRecvDTO> mailSendRecvList, Map<Integer, Integer> countMsgMap) {
    this.mailSendRecvList = mailSendRecvList;
    this.countMsgMap = countMsgMap;
  }

}
