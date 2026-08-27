package grafiosch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.MessageVisibility;
import grafiosch.gtnet.SendReceivedType;

/**
 * Guards that a private conversation stays private.
 *
 * <p>
 * Inheritance used to look only at the immediate parent, which is enough while every row is written through this same
 * chain. It is not enough for a row that arrives over the wire: an inbound reply is stored with the visibility byte the
 * peer chose, and the peer has no reason to know that the thread it answers is admin only. Resolving the root instead
 * makes the rule hold wherever the row came from.
 * </p>
 */
class GTNetMessageVisibilityInheritanceTest {

  private static final byte ADMIN_MESSAGE = GNetCoreMessageCode.GT_NET_ADMIN_MESSAGE_SEL_C.getValue();
  private static final Integer ID_PEER = 7;

  private final Map<Integer, GTNetMessage> stored = new HashMap<>();
  private final GTNetMessageJpaRepository repository = mock(GTNetMessageJpaRepository.class);
  private final GTNetMessageJpaRepositoryImpl repositoryImpl = new GTNetMessageJpaRepositoryImpl();

  GTNetMessageVisibilityInheritanceTest() {
    when(repository.findByIdGtNetMessage(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(this::storedRow);
    when(repository.save(org.mockito.ArgumentMatchers.any(GTNetMessage.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ReflectionTestUtils.setField(repositoryImpl, "gtNetMessageJpaRepository", repository);
  }

  @Test
  @DisplayName("A reply into an admin-only thread is stored admin only")
  void aReplyInheritsFromItsParent() {
    row(1, null, MessageVisibility.ADMIN_ONLY);

    GTNetMessage reply = message(2, 1, MessageVisibility.ALL_USERS);
    assertThat(repositoryImpl.saveMsg(reply).getVisibility()).isEqualTo(MessageVisibility.ADMIN_ONLY);
  }

  @Test
  @DisplayName("The root of the thread decides, not the message directly above")
  void inheritanceFollowsTheThreadToItsRoot() {
    // The middle row is what an inbound reply looked like before the rule applied to it at all: public, in the middle
    // of a private conversation. Reading only the immediate parent would let the next reply stay public too.
    row(1, null, MessageVisibility.ADMIN_ONLY);
    row(2, 1, MessageVisibility.ALL_USERS);

    GTNetMessage reply = message(3, 2, MessageVisibility.ALL_USERS);
    assertThat(repositoryImpl.saveMsg(reply).getVisibility()).isEqualTo(MessageVisibility.ADMIN_ONLY);
  }

  @Test
  @DisplayName("A thread everyone may see leaves the reply its own choice")
  void aPublicThreadDoesNotForceAnything() {
    row(1, null, MessageVisibility.ALL_USERS);

    GTNetMessage reply = message(2, 1, MessageVisibility.ALL_USERS);
    assertThat(repositoryImpl.saveMsg(reply).getVisibility()).isEqualTo(MessageVisibility.ALL_USERS);
  }

  @Test
  @DisplayName("A cycle in reply_to ends the walk instead of hanging the save")
  void aCycleIsSurvived() {
    // reply_to is an ordinary column with no constraint that forbids this.
    row(1, 2, MessageVisibility.ALL_USERS);
    row(2, 1, MessageVisibility.ALL_USERS);

    GTNetMessage reply = message(3, 1, MessageVisibility.ALL_USERS);
    assertThat(repositoryImpl.saveMsg(reply).getVisibility()).isEqualTo(MessageVisibility.ALL_USERS);
  }

  @Test
  @DisplayName("A root message keeps the visibility it was given")
  void aRootMessageIsNotTouched() {
    GTNetMessage root = message(1, null, MessageVisibility.ADMIN_ONLY);
    assertThat(repositoryImpl.saveMsg(root).getVisibility()).isEqualTo(MessageVisibility.ADMIN_ONLY);
  }

  private GTNetMessage storedRow(InvocationOnMock invocation) {
    return stored.get(invocation.<Integer>getArgument(0));
  }

  private void row(Integer id, Integer replyTo, MessageVisibility visibility) {
    stored.put(id, message(id, replyTo, visibility));
  }

  private static GTNetMessage message(Integer id, Integer replyTo, MessageVisibility visibility) {
    GTNetMessage message = new GTNetMessage(ID_PEER, LocalDateTime.now(), SendReceivedType.RECEIVED.getValue(), replyTo,
        ADMIN_MESSAGE, "text", null);
    message.setIdGtNetMessage(id);
    message.setVisibility(visibility);
    return message;
  }
}
