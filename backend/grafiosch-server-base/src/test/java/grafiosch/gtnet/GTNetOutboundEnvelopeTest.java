package grafiosch.gtnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNet;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.m2m.GTNetMessageHelper;
import grafiosch.repository.GTNetMessageJpaRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * What a sender actually puts on the wire has to survive the receiver's own validator.
 *
 * <p>
 * The peer tests build their envelopes by hand and fill in every field, so they cannot notice a sender that leaves one
 * out. That is how a release shipped in which the receiver refused the liveness ping - and with it every payload
 * exchange - as an invalid envelope: the ping message is never persisted, so it names no sender-local message, and the
 * validator demanded one from every code.
 * </p>
 */
class GTNetOutboundEnvelopeTest {

  private final GTNetEnvelopeValidator validator = new GTNetEnvelopeValidator(
      new GTNetProtocolLimits(4194304, 32, 2097152, 5, 7), new GTNetMessageCodeRegistry(), new ObjectMapper(),
      mock(GTNetMessageJpaRepository.class));

  @Test
  void thePingEnvelopeAsItIsSentPassesTheReceiversValidator() {
    MessageEnvelope ping = GTNetMessageHelper.buildPingEnvelope(sender());

    assertThat(ping.idSourceGtNetMessage).as("the ping is never persisted, so it names no message").isNull();
    assertThat(validator.validate(ping, peer())).isEmpty();
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
