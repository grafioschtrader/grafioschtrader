package grafioschtrader.gtnet;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import grafioschtrader.dynamic.model.ClassDescriptorInputAndShow;
import grafioschtrader.dynamic.model.DynamicModelHelper;
import grafioschtrader.gtnet.model.msg.EntityExchangeRequestMsg;
import grafioschtrader.gtnet.model.msg.FirstHandshakeMsg;
import grafioschtrader.gtnet.model.msg.MaintenanceMsg;
import grafioschtrader.gtnet.model.msg.UpdateServerlistRequestMsg;

public abstract class GTNetModelHelper {

  private static Map<GTNetMessageCodeType, GTNetMsgRequest> msgFormMap;

  static {
    msgFormMap = new HashMap<>();
    msgFormMap.put(GTNetMessageCodeType.GTNET_FIRST_HANDSHAKE_S,
        new GTNetMsgRequest(FirstHandshakeMsg.class, false, true));
    msgFormMap.put(GTNetMessageCodeType.GTNET_UPDATE_SERVERLIST_C,
        new GTNetMsgRequest(UpdateServerlistRequestMsg.class, true, true));
    msgFormMap.put(GTNetMessageCodeType.GTNET_ENTITY_REQUEST_C,
        new GTNetMsgRequest(EntityExchangeRequestMsg.class, true, true));
    msgFormMap.put(GTNetMessageCodeType.GTNET_MAINTENANCE_C,
        new GTNetMsgRequest(MaintenanceMsg.class, true, true));
  }

  public static Map<GTNetMessageCodeType, ClassDescriptorInputAndShow> getAllFormDefinitionsWithClass() {
    return msgFormMap.entrySet().stream().filter(e -> e.getValue().userRequest).collect(
        Collectors.toMap(Map.Entry::getKey, e -> DynamicModelHelper.getFormDefinitionOfModelClass(e.getValue().model)));
  }

  public static GTNetMsgRequest getMsgClassByMessageCode(GTNetMessageCodeType gtNetMessageCodeType) {
    return msgFormMap.get(gtNetMessageCodeType);
  }

  public static class GTNetMsgRequest {
    public Class<?> model;
    public boolean userRequest;
    public boolean responseExpected;
    

    public GTNetMsgRequest(Class<?> model, boolean userRequest, boolean responseExpected) {
      this.model = model;
      this.userRequest = userRequest;
      this.responseExpected = responseExpected;
    }
  }

}
