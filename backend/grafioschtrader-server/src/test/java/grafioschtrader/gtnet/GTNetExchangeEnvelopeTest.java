package grafioschtrader.gtnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNet;
import grafiosch.gtnet.GTNetEnvelopeValidator;
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.gtnet.GTNetProtocolDescriptor;
import grafiosch.gtnet.GTNetProtocolLimits;
import grafiosch.gtnet.MessageCategory;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetMessageJpaRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Every payload request this application sends must survive the peer's own envelope validator.
 *
 * <p>
 * The peer suites drive these codes through a synthetic peer that fills in every envelope field itself, so they cannot
 * see what the production senders leave out. That is how a release shipped whose every intraday price request,
 * historyquote exchange, coverage query, metadata lookup and exchange sync was refused with {@code ENVELOPE_INVALID}:
 * the senders name no sender-local message, and the validator demanded one from every code. Here the envelope is built
 * by the same factory the senders use and handed to the same validator the receiver runs.
 * </p>
 */
class GTNetExchangeEnvelopeTest {

  private final GTNetMessageCodeRegistry registry = fullRegistry();

  private final GTNetEnvelopeValidator validator = new GTNetEnvelopeValidator(
      new GTNetProtocolLimits(4194304, 32, 2097152, 5, 7), registry, new ObjectMapper(),
      mock(GTNetMessageJpaRepository.class));

  @Test
  void everyRequestASenderBuildsItselfIsAcceptedByThePeer() {
    ObjectMapper mapper = new ObjectMapper();
    int checked = 0;
    for (GTNetProtocolDescriptor descriptor : registry.getAllDescriptors()) {
      if (descriptor.category() != MessageCategory.REQUEST || descriptor.senderPersists()) {
        continue;
      }
      MessageEnvelope request = MessageEnvelope.forExchange(sender(), descriptor.value(),
          mapper.valueToTree(List.of()));

      assertThat(validator.validate(request, peer())).as("%s as it is put on the wire", descriptor.name()).isEmpty();
      checked++;
    }
    // The ping plus the eight payload requests; without this the loop would pass by iterating over nothing.
    assertThat(checked).isGreaterThanOrEqualTo(9);
  }

  @Test
  void theApplicationProtocolIsSentWithoutASenderLocalMessage() {
    // Nothing below code 60 may be swept into this by accident: those codes are conversations and keep their row.
    assertThat(GTProtocolDescriptors.all())
        .allSatisfy(descriptor -> assertThat(descriptor.senderPersists()).as("%s", descriptor.name()).isFalse());
  }

  private static GTNetMessageCodeRegistry fullRegistry() {
    GTNetMessageCodeRegistry codeRegistry = new GTNetMessageCodeRegistry();
    GTProtocolDescriptors.all().forEach(codeRegistry::register);
    return codeRegistry;
  }

  private static GTNet sender() {
    GTNet sender = new GTNet();
    sender.setIdGtNet(1);
    sender.setDomainRemoteName("http://192.0.2.10:8080");
    return sender;
  }

  private static GTNet peer() {
    GTNet peer = new GTNet();
    peer.setIdGtNet(2);
    return peer;
  }
}
