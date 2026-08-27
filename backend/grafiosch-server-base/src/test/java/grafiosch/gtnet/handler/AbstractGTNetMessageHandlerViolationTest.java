package grafiosch.gtnet.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.MessageCategory;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GlobalparametersJpaRepository;

/**
 * Guards the request-violation budget of {@link AbstractGTNetMessageHandler#isBlockedByRequestViolations}. The counter
 * itself saturates at 99 in {@link GTNetConfig}, so the threshold and the saturation are checked together.
 */
class AbstractGTNetMessageHandlerViolationTest {

  private static final int THRESHOLD = 20;

  private final GlobalparametersJpaRepository globalparameters = mock(GlobalparametersJpaRepository.class);
  private final TestHandler handler = new TestHandler();

  AbstractGTNetMessageHandlerViolationTest() {
    when(globalparameters.getMaxLimitExceededCount()).thenReturn(THRESHOLD);
    handler.globalparametersJpaRepository = globalparameters;
  }

  @Test
  void servesARemoteBelowTheThreshold() {
    assertThat(handler.isBlockedByRequestViolations(context(remoteWithViolations((byte) (THRESHOLD - 1))))).isFalse();
  }

  @Test
  void refusesARemoteThatReachedTheThreshold() {
    assertThat(handler.isBlockedByRequestViolations(context(remoteWithViolations((byte) THRESHOLD)))).isTrue();
  }

  @Test
  void treatsAnUnknownRemoteOrMissingConfigAsUnblocked() {
    assertThat(handler.isBlockedByRequestViolations(context(null))).isFalse();
    assertThat(handler.isBlockedByRequestViolations(context(new GTNet()))).isFalse();
  }

  @Test
  void keepsRefusingOnceTheCounterSaturatesAt99() {
    GTNetConfig config = new GTNetConfig();
    config.setRequestViolationCount((byte) 98);
    for (int i = 0; i < 5; i++) {
      config.incrementRequestViolationCount();
    }
    assertThat(config.getRequestViolationCount()).isEqualTo((byte) 99);

    GTNet remote = new GTNet();
    remote.setGtNetConfig(config);
    assertThat(handler.isBlockedByRequestViolations(context(remote))).isTrue();
  }

  private GTNet remoteWithViolations(byte violations) {
    GTNetConfig config = new GTNetConfig();
    config.setRequestViolationCount(violations);
    GTNet remote = new GTNet();
    remote.setGtNetConfig(config);
    return remote;
  }

  private GTNetMessageContext context(GTNet remote) {
    return new GTNetMessageContext(new GTNet(), remote, new MessageEnvelope(), List.of(), null);
  }

  /** Minimal concrete handler; only the inherited guard is under test. */
  private static final class TestHandler extends AbstractGTNetMessageHandler {

    @Override
    public GTNetMessageCode getSupportedMessageCode() {
      return null;
    }

    @Override
    public MessageCategory getCategory() {
      return MessageCategory.REQUEST;
    }

    @Override
    public HandlerResult<GTNetMessage, MessageEnvelope> handle(GTNetMessageContext context) {
      return new HandlerResult.NoResponseNeeded<>();
    }
  }
}
