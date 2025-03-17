package grafioschtrader.gtnet;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import grafiosch.dynamic.model.ClassDescriptorInputAndShow;
import grafiosch.dynamic.model.DynamicModelHelper;
import grafioschtrader.gtnet.model.msg.EntityExchangeRequestMsg;
import grafioschtrader.gtnet.model.msg.FirstHandshakeMsg;
import grafioschtrader.gtnet.model.msg.MaintenanceMsg;
import grafioschtrader.gtnet.model.msg.RevokeMsg;
import grafioschtrader.gtnet.model.msg.UpdateServerlistRequestMsg;

public abstract class GTNetModelHelper {

  public final static String MESSAGE_TO_ALL = "_ALL_";
  public final static String IS_USER_REQUEST_STR = "_C";

  private static Map<GTNetMessageCodeType, GTNetMsgRequest> msgFormMap;

  static {
    msgFormMap = new HashMap<>();
    msgFormMap.put(GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_S, new GTNetMsgRequest(FirstHandshakeMsg.class, true));
    msgFormMap.put(GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_SEL_C,
        new GTNetMsgRequest(UpdateServerlistRequestMsg.class, true));
    msgFormMap.put(GTNetMessageCodeType.GT_NET_ENTITY_REQUEST_SEL_C,
        new GTNetMsgRequest(EntityExchangeRequestMsg.class, true));
    msgFormMap.put(GTNetMessageCodeType.GT_NET_MAINTENANCE_ALL_C, new GTNetMsgRequest(MaintenanceMsg.class, true));

    msgFormMap.put(GTNetMessageCodeType.GT_NET_BOTH_REQUEST_SEL_C, new GTNetMsgRequest(null, false));

    msgFormMap.put(GTNetMessageCodeType.GT_NET_BOTH_REVOKE_SEL_C, new GTNetMsgRequest(RevokeMsg.class, false));

  }

  public static Map<GTNetMessageCodeType, ClassDescriptorInputAndShow> getAllFormDefinitionsWithClass() {
    return msgFormMap.entrySet().stream()
        .filter(e -> e.getKey().name().endsWith(IS_USER_REQUEST_STR) && e.getValue().model != null).collect(Collectors
            .toMap(Map.Entry::getKey, e -> DynamicModelHelper.getFormDefinitionOfModelClass(e.getValue().model)));
  }

  public static GTNetMsgRequest getMsgClassByMessageCode(GTNetMessageCodeType gtNetMessageCodeType) {
    return msgFormMap.get(gtNetMessageCodeType);
  }

  public static class GTNetMsgRequest {
    public Class<?> model;
    public boolean responseExpected;

    public GTNetMsgRequest(Class<?> model, boolean responseExpected) {
      this.model = model;
      this.responseExpected = responseExpected;
    }
  }

}
