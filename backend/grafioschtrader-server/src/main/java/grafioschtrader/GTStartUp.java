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

/**
 * Application startup component responsible for performing critical initialization tasks when the GrafioschTrader
 * application boots up. This component ensures that all global configurations, type registries, and system-wide
 * settings are properly established before the application begins normal operation.
 * 
 * 
 * <h3>Execution Timing</h3>
 * <p>
 * This component uses Spring's @PostConstruct mechanism to ensure initialization occurs after Spring dependency
 * injection is complete but before the application starts serving requests.
 */
@Component
public class GTStartUp {

  /**
   * Performs comprehensive application initialization tasks after Spring context setup. This method is automatically
   * called by Spring after dependency injection is complete and ensures that all global configurations and type
   * registries are properly initialized.
   * 
   * <p>
   * The initialization sequence includes:
   * </p>
   * <ol>
   * <li><strong>Timezone Setup:</strong> Configures the JVM default timezone to ensure consistent date/time handling
   * across all application components</li>
   * <li><strong>Global Parameters:</strong> Initializes application-wide parameter defaults and configuration
   * values</li>
   * <li><strong>Mail Configuration:</strong> Sets up default mail sending and forwarding behavior for notification and
   * communication systems</li>
   * <li><strong>Parameter Prefixes:</strong> Registers GT-specific parameter prefixes for configuration management</li>
   * <li><strong>Tenant Setup:</strong> Initializes multi-tenant configuration support</li>
   * <li><strong>Type Registries:</strong> Populates various type registries with GT-specific enumeration values for
   * dynamic type handling</li>
   * <li><strong>Export Definitions:</strong> Registers data export and import definitions for the data management
   * system</li>
   * </ol>
   * 
   * <p>
   * <strong>Type Registry Initialization:</strong>
   * </p>
   * <ul>
   * <li><code>TaskDataChange.TASK_TYPES_REGISTRY</code> - Extended task type definitions for background processing and
   * data change tracking</li>
   * <li><code>UDFMetadata.UDF_SPECIAL_TYPE_REGISTRY</code> - Special type definitions for user-defined fields and
   * custom data structures</li>
   * <li><code>ConnectorApiKey.SUBSCRIPTION_REGISTRY</code> - Subscription type definitions for API connector services
   * and access levels</li>
   * <li><code>MailEntity.MESSAGE_COM_TYPES_REGISTRY</code> - Communication type definitions for mail and messaging
   * systems</li>
   * </ul>
   * 
   * <p>
   * <strong>Critical Dependencies:</strong> This method depends on various global constants and configuration classes
   * being available on the classpath. Any missing dependencies will cause application startup to fail.
   * </p>
   * 
   * @throws RuntimeException if any initialization step fails, preventing application startup
   * 
   * @see PostConstruct
   * @see BaseConstants#TIME_ZONE
   * @see GlobalConstants#GT_PREFIX
   * @see TenantConfig#initialzie()
   * @see MailSendForwardDefault#initialize()
   * @see GlobalParamKeyDefault
   */
  @PostConstruct
  void started() {
    TimeZone.setDefault(TimeZone.getTimeZone(BaseConstants.TIME_ZONE));
    new GlobalParamKeyDefault();
    MailSendForwardDefault.initialize();
    BaseConstants.PREFIXES_PARAM.add(GlobalConstants.GT_PREFIX);
    TenantConfig.initialzie();
    TaskDataChange.TASK_TYPES_REGISTRY.addTypes(TaskTypeExtended.values());
    UDFMetadata.UDF_SPECIAL_TYPE_REGISTRY.addTypes(UDFSpecialGTType.values());
    ConnectorApiKey.SUBSCRIPTION_REGISTRY.addTypes(SubscriptionType.values());
    MailEntity.MESSAGE_COM_TYPES_REGISTRY.addTypes(MessageGTComType.values());
    ExportDeleteHelper.addExportDefinitions(MyDataExportDeleteDefinition.exportDefinitions);
  }

}
