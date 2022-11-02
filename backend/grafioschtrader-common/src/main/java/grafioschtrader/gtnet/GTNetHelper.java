package grafioschtrader.gtnet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import grafioschtrader.dynamic.model.DynamicModelHelper;
import grafioschtrader.dynamic.model.FieldDescriptorInputAndShow;
import grafioschtrader.gtnet.model.msg.MaintenanceMsg;
import grafioschtrader.gtnet.model.msg.RequestMsg;

public abstract class GTNetHelper {

  private static Map<GTNetMessageCodeType, Class<?>> msgFormMap;

  static {
    msgFormMap = new HashMap<>();
    msgFormMap.put(GTNetMessageCodeType.GTNET_UPDATE_SERVERLIST_C, RequestMsg.class);
    msgFormMap.put(GTNetMessageCodeType.GTNET_MAINTENANCE_S, MaintenanceMsg.class);
  }

  public static Map<GTNetMessageCodeType, List<FieldDescriptorInputAndShow>> getAllFormDefinitions() {
    return msgFormMap.entrySet().stream().collect(
        Collectors.toMap(Map.Entry::getKey, e -> DynamicModelHelper.getFormDefinitionOfModelClass(e.getValue())));
  }
  
  public static Class<?> getMsgClassByMessageCode(GTNetMessageCodeType gtNetMessageCodeType) {
    return msgFormMap.get(gtNetMessageCodeType);
  }
}
