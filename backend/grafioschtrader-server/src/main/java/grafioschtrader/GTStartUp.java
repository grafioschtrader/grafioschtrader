package grafioschtrader;

import java.util.TimeZone;

import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.ConnectorApiKey;
import grafiosch.entities.MailEntity;
import grafiosch.entities.TaskDataChange;
import grafiosch.entities.UDFMetadata;
import grafiosch.exportdelete.ExportDeleteHelper;
import grafioschtrader.config.TenantConfig;
import grafioschtrader.exportdelete.MyDataExportDeleteDefinition;
import grafioschtrader.types.MailSendForwardDefault;
import grafioschtrader.types.MessageGTComType;
import grafioschtrader.types.SubscriptionType;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.types.UDFSpecialGTType;
import jakarta.annotation.PostConstruct;

@Component
public class GTStartUp {

  @PostConstruct
  void started() {
    TimeZone.setDefault(TimeZone.getTimeZone(BaseConstants.TIME_ZONE));
    new GlobalParamKeyDefault();
    MailSendForwardDefault.initialize();
    TenantConfig.initialzie();
    TaskDataChange.TASK_TYPES_REGISTRY.addTypes(TaskTypeExtended.values());
    UDFMetadata.UDF_SPECIAL_TYPE_REGISTRY.addTypes(UDFSpecialGTType.values());
    ConnectorApiKey.SUBSCRIPTION_REGISTRY.addTypes(SubscriptionType.values());
    MailEntity.MESSAGE_COM_TYPES_REGISTRY.addTypes(MessageGTComType.values());
    ExportDeleteHelper.addExportDefinitions(MyDataExportDeleteDefinition.exportDefinitions);
  }

}
