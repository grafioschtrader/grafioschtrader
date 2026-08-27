package grafiosch.gtnet;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import grafiosch.entities.GTNetConfig;

/**
 * Rotates the token a peer uses to authenticate against this instance, keeping the superseded one usable for a bounded
 * window.
 *
 * <p>
 * A token refresh has an unavoidable gap: the answerer commits the replacement while it is still handling the request,
 * and the initiator only learns of it when the response arrives. If that response is lost, the initiator keeps calling
 * with a token this instance no longer recognizes, and the refresh cannot be repeated either because it is itself
 * authenticated. The overlap closes that gap without weakening anything else: the old token stays valid only for
 * {@code g.gnet.token.overlap.days} and only for the peer it was issued to.
 * </p>
 */
@Service
public class GTNetTokenRotationService {

  private static final Logger log = LoggerFactory.getLogger(GTNetTokenRotationService.class);

  private final GTNetProtocolLimits protocolLimits;

  public GTNetTokenRotationService(GTNetProtocolLimits protocolLimits) {
    this.protocolLimits = protocolLimits;
  }

  /**
   * Installs a new inbound token, moving the one it replaces into the overlap window.
   *
   * <p>
   * An existing, still valid overlap is deliberately left alone. A second retry inside the same window would otherwise
   * push the <em>first</em> rotation's token into the overlap slot and evict the token the stuck initiator is actually
   * still using — turning the mechanism against the very case it exists for. Keeping the oldest still valid predecessor
   * means every retry inside the window authenticates.
   * </p>
   *
   * @param gtNetConfig the peer's configuration
   * @param newToken    the token the peer is to use from now on
   */
  public void rotateTokenThis(GTNetConfig gtNetConfig, String newToken) {
    LocalDateTime now = GTNetTime.now();
    if (!gtNetConfig.isPreviousTokenValid(now)) {
      gtNetConfig.setTokenThisPrevious(gtNetConfig.getTokenThis());
      gtNetConfig.setTokenThisPreviousValidUntil(now.plusDays(protocolLimits.getTokenOverlapDays()));
    } else {
      log.info("Peer {} rotated again inside the overlap window, keeping the older predecessor acceptable",
          gtNetConfig.getIdGtNet());
    }
    gtNetConfig.setTokenThis(newToken);
  }

  /**
   * Ends the overlap because the peer has demonstrably adopted the current token.
   *
   * @param gtNetConfig the peer's configuration
   */
  public void clearOverlap(GTNetConfig gtNetConfig) {
    gtNetConfig.setTokenThisPrevious(null);
    gtNetConfig.setTokenThisPreviousValidUntil(null);
  }
}
