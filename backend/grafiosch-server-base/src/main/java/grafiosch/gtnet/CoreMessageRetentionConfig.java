package grafiosch.gtnet;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the retention of the core GTNet message codes that every application using the library receives, for the
 * scheduled cleanup task. Application specific message codes are registered by the application itself.
 *
 * <p>
 * Server status announcements are the bulk of {@code gt_net_message}: an offline announcement is sent to every peer
 * on each shutdown. Neither it nor a settings update is read again once its handler has processed it. Maintenance and
 * discontinuation announcements are deliberately not part of this group, because an open one is looked up through its
 * message row and {@code gt_net_maintenance_window} is deleted along with it.
 * </p>
 */
@Configuration
public class CoreMessageRetentionConfig {

  @Bean
  IMessageRetentionProvider serverStatusRetention() {
    return new IMessageRetentionProvider() {
      @Override
      public String getConfigKey() {
        return "SS";
      }

      @Override
      public List<Byte> getMessageCodes() {
        return Arrays.asList(GNetCoreMessageCode.GT_NET_OFFLINE_ALL_C.getValue(),
            GNetCoreMessageCode.GT_NET_SETTINGS_UPDATED_ALL_C.getValue());
      }

      @Override
      public int getDefaultRetentionDays() {
        return 10;
      }
    };
  }
}
