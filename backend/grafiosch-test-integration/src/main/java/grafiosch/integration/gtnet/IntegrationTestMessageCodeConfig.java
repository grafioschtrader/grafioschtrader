package grafiosch.integration.gtnet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.gtnet.GTNetProtocolDescriptor;
import jakarta.annotation.PostConstruct;

/**
 * Registers {@link IntegrationTestMessageCode} with the message code registry, under the {@code e2e} profile only.
 *
 * Registration makes the code known so that {@code getMsgResponse} names it instead of answering
 * {@code UNKNOWN_MESSAGE_CODE}; no handler bean is provided, which is what lets the two-peer suite assert the
 * {@code NO_HANDLER} branch. A developer or production start never loads this configuration.
 */
@Configuration
@Profile("e2e")
public class IntegrationTestMessageCodeConfig {

  @Autowired
  private GTNetMessageCodeRegistry gtNetMessageCodeRegistry;

  @PostConstruct
  public void registerTestMessageCodes() {
    for (IntegrationTestMessageCode code : IntegrationTestMessageCode.values()) {
      // Declared without inbound dispatch, so the start-up validator accepts a code that deliberately has no handler.
      // For the same reason it needs no NLS key: it never reaches a stored message row.
      gtNetMessageCodeRegistry.register(GTNetProtocolDescriptor.request(code).noInboundDispatch().build());
    }
  }
}
