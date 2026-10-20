package grafioschtrader.mcp;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import grafiosch.entities.User;
import grafiosch.repository.UserEntityChangeCountJpaRepository;
import grafiosch.service.EntityLimitService;
import grafioschtrader.config.LimitKeyConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * The three limits a tool call passes before its report runs: the request bucket of the user, the single running heavy
 * report per user, and the daily budget of tool calls.
 *
 * <p>
 * None of them raises a rights counter of the user. A client that repeats a call is not a misuse by the user, and the
 * browser access of the same user is never affected: the bucket here is separate from the one of the browser filter.
 * </p>
 */
@Service
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class McpToolCallLimitService {

  /** Tool calls per minute and user. */
  public static final int MINUTE_BUCKET_SIZE = 10;
  /** Tool calls per hour and user. */
  public static final int HOUR_BUCKET_SIZE = 100;

  private final EntityLimitService entityLimitService;
  private final UserEntityChangeCountJpaRepository userEntityChangeCountJpaRepository;
  private final boolean limitRequest;

  private final Map<Integer, Bucket> bucketByUser = new ConcurrentHashMap<>();
  private final Set<Integer> usersWithHeavyReport = ConcurrentHashMap.newKeySet();

  public McpToolCallLimitService(EntityLimitService entityLimitService,
      UserEntityChangeCountJpaRepository userEntityChangeCountJpaRepository,
      @Value("${gt.limit.request}") boolean limitRequest) {
    this.entityLimitService = entityLimitService;
    this.userEntityChangeCountJpaRepository = userEntityChangeCountJpaRepository;
    this.limitRequest = limitRequest;
  }

  /**
   * Takes one unit from the user's request bucket. Switched off together with the browser bucket by
   * {@code gt.limit.request=false}.
   *
   * @throws McpToolException {@link McpErrorCode#RATE_LIMITED} when the bucket is empty
   */
  public void consumeRequest(Integer idUser) {
    if (!limitRequest) {
      return;
    }
    ConsumptionProbe probe = bucketByUser.computeIfAbsent(idUser, _ -> createBucket()).tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
      throw new McpToolException(McpErrorCode.RATE_LIMITED, "gt.mcp.rate.limited", retryAfterSeconds)
          .with("retryAfterSeconds", retryAfterSeconds);
    }
  }

  /**
   * Marks a heavy report of the user as running. A second one is refused at once instead of waiting.
   *
   * @return true when the user had no heavy report running; the caller must then call {@link #leaveHeavy}
   */
  public boolean tryEnterHeavy(Integer idUser) {
    return usersWithHeavyReport.add(idUser);
  }

  public void leaveHeavy(Integer idUser) {
    usersWithHeavyReport.remove(idUser);
  }

  /**
   * Reserves one unit of today's (UTC) budget of tool calls before the report runs. The row of the day is created with
   * a zero counter first; the reservation itself is a conditional increment, decided under the row lock, so concurrent
   * calls at the last free unit cannot both succeed and the counter never exceeds the limit. A refused attempt does not
   * raise the counter. Without a configured limit nothing is counted.
   *
   * <p>
   * The two statements run in separate transactions on purpose, each committed by its repository method. Within one
   * transaction the {@code INSERT IGNORE} on an existing row keeps a shared lock that the following update has to
   * upgrade, and two concurrent calls then deadlock each other. Committing the insert first releases that lock; the
   * decision is still taken by the single conditional update.
   * </p>
   *
   * @param user the authenticated user; only read, never kept
   * @throws McpToolException {@link McpErrorCode#QUOTA_EXCEEDED} when today's budget is used up
   */
  public void reserveDailyCall(User user) {
    Optional<Integer> limit = entityLimitService.resolve(user, LimitKeyConfig.KEY_DAY_MCP_TOOL_CALL);
    if (limit.isEmpty()) {
      return;
    }
    LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
    userEntityChangeCountJpaRepository.insertIgnoreZeroCount(user.getIdUser(), todayUtc,
        LimitKeyConfig.ENTITY_NAME_MCP_TOOL_CALL);
    if (userEntityChangeCountJpaRepository.incrementInsertBelowLimit(user.getIdUser(), todayUtc,
        LimitKeyConfig.ENTITY_NAME_MCP_TOOL_CALL, limit.get()) == 0) {
      String resetAt = todayUtc.plusDays(1).atStartOfDay().toString() + "Z";
      throw new McpToolException(McpErrorCode.QUOTA_EXCEEDED, "gt.mcp.quota.exceeded", limit.get(), resetAt)
          .with("resetAt", resetAt);
    }
  }

  private Bucket createBucket() {
    return Bucket.builder()
        .addLimit(Bandwidth.builder().capacity(MINUTE_BUCKET_SIZE)
            .refillGreedy(MINUTE_BUCKET_SIZE, Duration.ofMinutes(1)).build())
        .addLimit(
            Bandwidth.builder().capacity(HOUR_BUCKET_SIZE).refillGreedy(HOUR_BUCKET_SIZE, Duration.ofHours(1)).build())
        .build();
  }
}
