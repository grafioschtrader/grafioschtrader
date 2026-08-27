package grafiosch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.gtnet.GTNetProtocolDescriptor;
import grafiosch.gtnet.SendReceivedType;

/**
 * Guards that a request whose answer is still outstanding cannot be deleted.
 *
 * <p>
 * The check used to resolve the code through the core enum, which knows nothing above 54, so every application code
 * resolved to null and fell through to "deletable" — a pending exchange-sync request could be removed while the peer
 * was still going to answer it. It now reads {@code blocksDeletion} from the protocol descriptor, which the application
 * fills for its own codes at start-up.
 * </p>
 */
class GTNetMessageDeleteProtectionTest {

  /** An application-band request that waits for an answer, registered the way {@code GTStartUp} registers it. */
  private static final byte EXCHANGE_SYNC = 70;

  private static final Integer ID_PEER = 7;
  private static final Integer ID_MESSAGE = 500;

  private final GTNetMessageCodeRegistry messageCodeRegistry = new GTNetMessageCodeRegistry();
  private final GTNetMessageJpaRepositoryImpl repositoryImpl = new GTNetMessageJpaRepositoryImpl();

  GTNetMessageDeleteProtectionTest() {
    messageCodeRegistry.register(GTNetProtocolDescriptor.request(AppCode.GT_NET_EXCHANGE_SYNC_SEL_RR_C)
        .requiresResponse().responses(AppCode.GT_NET_EXCHANGE_SYNC_RESPONSE_S).build());
    messageCodeRegistry.register(GTNetProtocolDescriptor.response(AppCode.GT_NET_EXCHANGE_SYNC_RESPONSE_S).build());
    ReflectionTestUtils.setField(repositoryImpl, "gtNetMessageJpaRepository", mock(GTNetMessageJpaRepository.class));
    ReflectionTestUtils.setField(repositoryImpl, "messageCodeRegistry", messageCodeRegistry);
  }

  @Test
  @DisplayName("A pending exchange-sync request is not deletable")
  void aPendingApplicationRequestIsProtected() {
    GTNetMessage request = sentRequest(EXCHANGE_SYNC);
    repositoryImpl.computeCanDeleteFlags(List.of(request), Set.of(ID_MESSAGE), Set.of());
    assertThat(request.isCanDelete()).isFalse();
  }

  @Test
  @DisplayName("An exchange-sync request that has been answered is deletable again")
  void anAnsweredRequestIsReleased() {
    GTNetMessage request = sentRequest(EXCHANGE_SYNC);
    repositoryImpl.computeCanDeleteFlags(List.of(request), Set.of(), Set.of());
    assertThat(request.isCanDelete()).isTrue();
  }

  @Test
  @DisplayName("A pending handshake stays protected, as it always was")
  void aPendingCoreRequestIsStillProtected() {
    GTNetMessage request = sentRequest(GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S.getValue());
    repositoryImpl.computeCanDeleteFlags(List.of(request), Set.of(ID_MESSAGE), Set.of());
    assertThat(request.isCanDelete()).isFalse();
  }

  @Test
  @DisplayName("An announcement is deletable even while it sits in a pending set")
  void anAnnouncementIsNotProtected() {
    GTNetMessage announcement = sentRequest(GNetCoreMessageCode.GT_NET_OFFLINE_ALL_C.getValue());
    repositoryImpl.computeCanDeleteFlags(List.of(announcement), Set.of(ID_MESSAGE), Set.of());
    assertThat(announcement.isCanDelete()).isTrue();
  }

  private static GTNetMessage sentRequest(byte messageCode) {
    GTNetMessage message = new GTNetMessage(ID_PEER, LocalDateTime.now(), SendReceivedType.SEND.getValue(), null,
        messageCode, null, null);
    message.setIdGtNetMessage(ID_MESSAGE);
    return message;
  }

  /** Stands in for the application enum, which this module cannot see. */
  private enum AppCode implements GTNetMessageCode {

    GT_NET_EXCHANGE_SYNC_SEL_RR_C(EXCHANGE_SYNC), GT_NET_EXCHANGE_SYNC_RESPONSE_S((byte) 71);

    private final byte value;

    AppCode(byte value) {
      this.value = value;
    }

    @Override
    public byte getValue() {
      return value;
    }
  }
}
