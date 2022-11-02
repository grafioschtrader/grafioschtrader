package grafioschtrader.gtnet.model;

import java.util.List;
import java.util.Map;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;

public class GTNetWithMessages {
  public List<GTNet> gtNetList;
  public Map<Integer, List<GTNetMessage>> gtNetMessageMap;
  public Integer gtNetMyEntryId;
  
  public GTNetWithMessages(List<GTNet> gtNetList, Map<Integer, List<GTNetMessage>> gtNetMessageMap,
      Integer gtNetMyEntryId) {
    this.gtNetList = gtNetList;
    this.gtNetMessageMap = gtNetMessageMap;
    this.gtNetMyEntryId = gtNetMyEntryId;
  }
    
}
