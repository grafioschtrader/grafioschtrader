package grafioschtrader.gtnet;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import grafiosch.gtnet.IMessageRetentionProvider;

/**
 * Configuration for GTNet message retention providers.
 * Registers message code groups with their retention defaults for the scheduled cleanup task.
 */
@Configuration
public class GTNetMessageRetentionConfig {

  @Bean
  public IMessageRetentionProvider lastPriceRetention() {
    return new IMessageRetentionProvider() {
      @Override
      public String getConfigKey() {
        return "LP";
      }

      @Override
      public List<Byte> getMessageCodes() {
        return Arrays.asList(
            GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C.getValue(),
            GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S.getValue());
      }

      @Override
      public int getDefaultRetentionDays() {
        return 1;
      }
    };
  }

  @Bean
  public IMessageRetentionProvider historyPriceRetention() {
    return new IMessageRetentionProvider() {
      @Override
      public String getConfigKey() {
        return "HP";
      }

      @Override
      public List<Byte> getMessageCodes() {
        return Arrays.asList(
            GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C.getValue(),
            GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_RESPONSE_S.getValue());
      }

      @Override
      public int getDefaultRetentionDays() {
        return 5;
      }
    };
  }

  @Bean
  public IMessageRetentionProvider securityLookupRetention() {
    return new IMessageRetentionProvider() {
      @Override
      public String getConfigKey() {
        return "SL";
      }

      @Override
      public List<Byte> getMessageCodes() {
        return Arrays.asList(
            GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_SEL_C.getValue(),
            GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_RESPONSE_S.getValue(),
            GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_NOT_FOUND_S.getValue(),
            GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_REJECTED_S.getValue(),
            GTNetMessageCodeType.GT_NET_SECURITY_BATCH_LOOKUP_SEL_C.getValue(),
            GTNetMessageCodeType.GT_NET_SECURITY_BATCH_LOOKUP_RESPONSE_S.getValue());
      }

      @Override
      public int getDefaultRetentionDays() {
        return 5;
      }
    };
  }
}
