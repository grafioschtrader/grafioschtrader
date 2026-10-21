package grafiosch.gtnet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the proof with which an instance recognizes its own GTNet entry. A false positive would let an instance take a
 * peer's entry for its own identity, a false negative leaves {@code g.gnet.my.entry.id} unset after the own entry was
 * saved.
 */
class GTNetInstanceIdentityTest {

  @Test
  @DisplayName("The answer of the same process is accepted")
  void ownAnswerIsAccepted() {
    GTNetInstanceIdentity identity = new GTNetInstanceIdentity();
    String challenge = identity.newChallenge();
    assertThat(identity.isWellFormedChallenge(challenge)).isTrue();
    assertThat(identity.isOwnAnswer(challenge, identity.answer(challenge))).isTrue();
  }

  @Test
  @DisplayName("Another instance answering the same challenge is rejected, as are an echo and no answer")
  void foreignAnswerIsRejected() {
    GTNetInstanceIdentity identity = new GTNetInstanceIdentity();
    GTNetInstanceIdentity peer = new GTNetInstanceIdentity();
    String challenge = identity.newChallenge();
    assertThat(identity.isOwnAnswer(challenge, peer.answer(challenge))).isFalse();
    assertThat(identity.isOwnAnswer(challenge, challenge)).isFalse();
    assertThat(identity.isOwnAnswer(challenge, null)).isFalse();
  }

  @Test
  @DisplayName("Challenges outside Base64url of 32 to 128 characters are not answered")
  void malformedChallengeIsRefused() {
    GTNetInstanceIdentity identity = new GTNetInstanceIdentity();
    assertThat(identity.isWellFormedChallenge(null)).isFalse();
    assertThat(identity.isWellFormedChallenge("short")).isFalse();
    assertThat(identity.isWellFormedChallenge("a".repeat(129))).isFalse();
    assertThat(identity.isWellFormedChallenge("a".repeat(31) + "/")).isFalse();
  }
}
