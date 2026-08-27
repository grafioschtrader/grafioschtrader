package grafiosch.gtnet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNetConfig;

/**
 * Guards the overlap rule of {@link GTNetTokenRotationService}. The window exists so that a peer whose token refresh
 * response was lost can still reach us, which only holds as long as a retry cannot evict the token that peer is still
 * using.
 */
class GTNetTokenRotationServiceTest {

  private static final int OVERLAP_DAYS = 7;

  private final GTNetTokenRotationService rotationService = new GTNetTokenRotationService(
      new GTNetProtocolLimits(4194304, 32, 2097152, 5, OVERLAP_DAYS));

  @Test
  void movesTheReplacedTokenIntoTheOverlapWindow() {
    GTNetConfig config = configWith("first", null, null);

    rotationService.rotateTokenThis(config, "second");

    assertThat(config.getTokenThis()).isEqualTo("second");
    assertThat(config.getTokenThisPrevious()).isEqualTo("first");
    assertThat(config.isPreviousTokenValid(LocalDateTime.now(ZoneOffset.UTC))).isTrue();
  }

  @Test
  void keepsTheOldestStillValidPredecessorWhenRotatedAgainInsideTheWindow() {
    GTNetConfig config = configWith("first", null, null);
    rotationService.rotateTokenThis(config, "second");

    rotationService.rotateTokenThis(config, "third");

    // "first" is the token a peer that never saw either response is still presenting. Replacing it with "second"
    // would lock that peer out for good, which is the very situation the overlap exists to prevent.
    assertThat(config.getTokenThis()).isEqualTo("third");
    assertThat(config.getTokenThisPrevious()).isEqualTo("first");
  }

  @Test
  void takesOverTheReplacedTokenAgainOnceTheWindowHasPassed() {
    GTNetConfig config = configWith("second", "first", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));

    rotationService.rotateTokenThis(config, "third");

    assertThat(config.getTokenThisPrevious()).isEqualTo("second");
    assertThat(config.getTokenThisPreviousValidUntil())
        .isAfter(LocalDateTime.now(ZoneOffset.UTC).plusDays(OVERLAP_DAYS - 1L));
  }

  @Test
  void reportsAnExpiredPredecessorAsNoLongerValid() {
    GTNetConfig config = configWith("second", "first", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));

    assertThat(config.isPreviousTokenValid(LocalDateTime.now(ZoneOffset.UTC))).isFalse();
  }

  @Test
  void clearingTheOverlapEndsIt() {
    GTNetConfig config = configWith("second", "first", LocalDateTime.now(ZoneOffset.UTC).plusDays(1));

    rotationService.clearOverlap(config);

    assertThat(config.getTokenThisPrevious()).isNull();
    assertThat(config.isPreviousTokenValid(LocalDateTime.now(ZoneOffset.UTC))).isFalse();
  }

  private static GTNetConfig configWith(String tokenThis, String previous, LocalDateTime validUntil) {
    GTNetConfig config = new GTNetConfig();
    config.setIdGtNet(7);
    config.setTokenThis(tokenThis);
    config.setTokenThisPrevious(previous);
    config.setTokenThisPreviousValidUntil(validUntil);
    return config;
  }
}
