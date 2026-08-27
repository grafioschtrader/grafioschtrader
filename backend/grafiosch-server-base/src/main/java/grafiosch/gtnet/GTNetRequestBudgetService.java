package grafiosch.gtnet;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.gtnet.handler.GTNetMessageHandlerRegistry;
import grafiosch.repository.GTNetConfigJpaRepository;

/**
 * Owns the per-peer daily request budget published as {@link GTNet#getDailyRequestLimit()}.
 *
 * The budget is charged in both directions and both counters live on the peer's {@link GTNetConfig}:
 * <ul>
 * <li><b>inbound</b> — every request another instance sends us raises {@code dailyRequestLimitCount} and is refused
 * with {@link GNetCoreMessageCode#GT_NET_DAILY_REQUEST_LIMIT_EXCEEDED_S} once our own limit is reached</li>
 * <li><b>outbound</b> — every request we send raises {@code dailyRequestLimitRemoteCount} and is suppressed once the
 * limit that peer published to us is reached, so we back off before the peer has to refuse us</li>
 * </ul>
 *
 * Only messages whose handler reports {@link MessageCategory#REQUEST} are charged. Responses are answers to requests
 * that were already charged on the way out, and announcements are one-way notifications. Three request codes are exempt
 * as well, because a peer must never be able to spend itself out of connectivity or recovery: ping, first handshake and
 * token refresh.
 *
 * There is no reset job. Both counters carry the UTC day they belong to in {@code dailyRequestLimitDate}, and the
 * charging statement rolls them over when it meets a request of a new day — which stays correct across a restart or a
 * downtime spanning midnight.
 *
 * @see GTNetConfigJpaRepository#chargeIncomingRequest(Integer, LocalDate, int)
 */
@Component
public class GTNetRequestBudgetService {

  private static final Logger log = LoggerFactory.getLogger(GTNetRequestBudgetService.class);

  /**
   * Request codes that are never charged and never refused. Ping keeps the mutual online status usable, and the two
   * handshake-related codes have to stay reachable so a peer whose token expired can obtain a new one.
   */
  private static final Set<Byte> EXEMPT_CODES = Set.of(GNetCoreMessageCode.GT_NET_PING.getValue(),
      GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S.getValue(),
      GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_SEL_RR_C.getValue());

  private final GTNetConfigJpaRepository gtNetConfigJpaRepository;
  private final GTNetMessageHandlerRegistry handlerRegistry;

  public GTNetRequestBudgetService(GTNetConfigJpaRepository gtNetConfigJpaRepository,
      @Lazy GTNetMessageHandlerRegistry handlerRegistry) {
    this.gtNetConfigJpaRepository = gtNetConfigJpaRepository;
    this.handlerRegistry = handlerRegistry;
  }

  /**
   * Charges an incoming request of the given remote against the budget this server grants it.
   *
   * On success the freshly stored count is copied back into the in-memory {@link GTNetConfig}, so an auto-answer rule
   * comparing {@code dailyCount} sees the request it is deciding about rather than the state before it arrived.
   *
   * @param remoteGTNet the sender, null for a first handshake from an unknown domain
   * @param myGTNet     the local entry, whose {@code dailyRequestLimit} is the budget
   * @param messageCode the request's message code
   * @return true when the request may be served, false when the remote has used up its allowance for the day
   */
  public boolean chargeIncoming(GTNet remoteGTNet, GTNet myGTNet, byte messageCode) {
    GTNetConfig gtNetConfig = chargeableConfig(remoteGTNet, messageCode);
    if (gtNetConfig == null) {
      return true;
    }
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    int limit = limitOrUnlimited(myGTNet);
    if (gtNetConfigJpaRepository.chargeIncomingRequest(gtNetConfig.getIdGtNet(), today, limit) == 0) {
      log.warn("Refusing message code {} from {}: daily request limit of {} reached", messageCode,
          remoteGTNet.getDomainRemoteName(), limit);
      return false;
    }
    Integer charged = gtNetConfigJpaRepository.findChargedIncomingCount(gtNetConfig.getIdGtNet(), today);
    if (charged != null) {
      gtNetConfig.setDailyRequestLimitCount(charged);
      gtNetConfig.setDailyRequestLimitDate(today);
    }
    return true;
  }

  /**
   * Charges a request this server is about to send against the budget the target published to us.
   *
   * @param targetGTNet the remote we want to send to
   * @param messageCode the request's message code
   * @return true when the request may be sent, false when we have used up what that peer grants us today
   */
  public boolean chargeOutgoing(GTNet targetGTNet, byte messageCode) {
    GTNetConfig gtNetConfig = chargeableConfig(targetGTNet, messageCode);
    if (gtNetConfig == null) {
      return true;
    }
    int limit = limitOrUnlimited(targetGTNet);
    if (gtNetConfigJpaRepository.chargeOutgoingRequest(gtNetConfig.getIdGtNet(), LocalDate.now(ZoneOffset.UTC),
        limit) == 0) {
      log.info("Not sending message code {} to {}: its daily request limit of {} is used up for today", messageCode,
          targetGTNet.getDomainRemoteName(), limit);
      return false;
    }
    return true;
  }

  /**
   * Returns the configuration row to charge, or null when this message does not participate in the budget at all — an
   * exempt code, a non-request category, an unknown peer or one without a configuration row (no handshake yet).
   */
  private GTNetConfig chargeableConfig(GTNet gtNet, byte messageCode) {
    if (gtNet == null || gtNet.getGtNetConfig() == null || EXEMPT_CODES.contains(messageCode)) {
      return null;
    }
    return handlerRegistry.getCategory(messageCode) == MessageCategory.REQUEST ? gtNet.getGtNetConfig() : null;
  }

  /** A missing limit means unlimited; Integer.MAX_VALUE keeps the SQL free of null handling. */
  private int limitOrUnlimited(GTNet gtNet) {
    Integer limit = gtNet == null ? null : gtNet.getDailyRequestLimit();
    return limit == null ? Integer.MAX_VALUE : limit;
  }
}
