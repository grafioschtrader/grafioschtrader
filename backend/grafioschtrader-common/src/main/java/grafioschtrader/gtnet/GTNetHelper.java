package grafioschtrader.gtnet;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import grafioschtrader.dynamic.model.ClassDescriptorInputAndShow;
import grafioschtrader.dynamic.model.DynamicModelHelper;
import grafioschtrader.gtnet.model.msg.EntityExchangeRequestMsg;
import grafioschtrader.gtnet.model.msg.MaintenanceMsg;
import grafioschtrader.gtnet.model.msg.UpdateServerlistRequestMsg;

public abstract class GTNetHelper {

  private static Map<GTNetMessageCodeType, Class<?>> msgFormMap;

  static {
    msgFormMap = new HashMap<>();
    msgFormMap.put(GTNetMessageCodeType.GTNET_UPDATE_SERVERLIST_C, UpdateServerlistRequestMsg.class);
    msgFormMap.put(GTNetMessageCodeType.GTNET_ENTITY_REQUEST_C, EntityExchangeRequestMsg.class);
    msgFormMap.put(GTNetMessageCodeType.GTNET_MAINTENANCE_C, MaintenanceMsg.class);
  }

  
  public static Map<GTNetMessageCodeType, ClassDescriptorInputAndShow> getAllFormDefinitionsWithClass() {
    return msgFormMap.entrySet().stream().collect(
        Collectors.toMap(Map.Entry::getKey, e -> DynamicModelHelper.getFormDefinitionOfModelClass(e.getValue())));
  }
  
  
  public static Class<?> getMsgClassByMessageCode(GTNetMessageCodeType gtNetMessageCodeType) {
    return msgFormMap.get(gtNetMessageCodeType);
  }
}
