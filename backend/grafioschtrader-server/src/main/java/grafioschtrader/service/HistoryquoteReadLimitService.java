package grafioschtrader.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import grafiosch.entities.User;
import grafiosch.entities.UserEntityChangeCount;
import grafiosch.entities.UserEntityChangeCount.UserEntityChangeCountId;
import grafiosch.error.LimitEntityTransactionError;
import grafiosch.exceptions.LimitEntityTransactionException;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.UserEntityChangeCountJpaRepository;
import grafiosch.service.EntityLimitService;
import grafiosch.service.UserService;
import grafiosch.types.OperationType;
import grafiosch.types.UserRightLimitCounter;
import grafioschtrader.config.LimitKeyConfig;

/**
 * Guards the historyquote read endpoints against mass downloading so GT cannot be abused as a free data provider
 * (issue #53). The discriminator between a regular user and a scraper is the number of <em>distinct</em> instruments
 * whose price history is requested per day: the UI loads full history only when a chart or the historyquote table is
 * opened for a specific instrument, while a scraper must enumerate thousands of distinct ids.
 * <p>
 * An instrument already requested today is always free — only the first request for a new distinct
 * {@code idSecuritycurrency} counts against the daily budget. Users with higher privileges (ADMIN, ALLEDIT) are
 * exempt. The budget comes from the globalparameter {@code gt.limit.day.HistoryquoteRead}; per-user exceptions are
 * possible through a {@code user_entity_change_limit} row with the pseudo entity name {@code HistoryquoteRead}.
 * <p>
 * Counting is hybrid: the distinct-id set per user lives in memory with a daily rollover, while the count is
 * persisted in {@code user_entity_change_count}. A server restart therefore loses only the deduplication (re-opened
 * instruments count once more — harmless with a generous budget) but never resets a scraper's consumed budget.
 * Requests blocked over the limit additionally increment {@code User.limitRequestExceedCount}, so persistent abusers
 * are locked out by the existing mechanism in {@code UserServiceImpl.checkUserLimits}.
 */
@Service
public class HistoryquoteReadLimitService {

  /** Pseudo entity name used in user_entity_change_count / user_entity_change_limit and the globalparameter key. */
  public static final String HISTORYQUOTE_READ = LimitKeyConfig.ENTITY_NAME_HISTORYQUOTE_READ;

  @Autowired
  private EntityLimitService entityLimitService;

  @Autowired
  private UserEntityChangeCountJpaRepository userEntityChangeCountJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private UserService userService;

  /** Distinct instrument ids requested today, per user. Rolls over when the date changes. */
  private final ConcurrentHashMap<Integer, UserDaySeen> seenByUser = new ConcurrentHashMap<>();

  /**
   * Checks whether the current user may read the price history of the given instrument and records the access.
   * Re-reading an instrument already requested today is always allowed and never counted. When the daily budget of
   * distinct instruments is exhausted, the user's limit violation counter is incremented and the request is rejected.
   *
   * @param idSecuritycurrency the security or currency pair whose history is requested
   * @throws LimitEntityTransactionException if the daily budget of distinct instruments is exhausted
   */
  public void assertReadAllowed(final Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final LocalDate today = LocalDate.now();
    final UserDaySeen userDaySeen = seenByUser.compute(user.getIdUser(),
        (_, existing) -> existing == null || !existing.day.equals(today) ? new UserDaySeen(today) : existing);
    synchronized (userDaySeen) {
      if (userDaySeen.seenIds.contains(idSecuritycurrency)) {
        return;
      }
      checkLimitAndCount(user, idSecuritycurrency, userDaySeen, today);
    }
  }

  /**
   * Compares the persisted distinct-instrument count against the resolved daily read budget and either registers the
   * new instrument or escalates and rejects.
   *
   * <p>
   * Which users are bounded is entirely a matter of configuration now; the former blanket exemption for
   * {@code ALLEDIT} and {@code ADMIN} is gone. Out of the box only a {@code ROLE_LIMITEDIT} row is seeded, so the
   * effective behaviour is unchanged until an administrator adds a row for another role. A key with no row at all
   * means unlimited and nothing is counted.
   * </p>
   *
   * <p>
   * This is the only limit family that increments the lockout counter on rejection: reading beyond the budget is what
   * the guard against Grafioschtrader being used as a data provider exists for, whereas exhausting an ordinary daily
   * editing budget is normal usage.
   * </p>
   *
   * @param idSecuritycurrency the instrument not yet seen today
   * @param userDaySeen        the caller-locked per-user dedup state for today
   * @param today              the day the access is accounted to
   */
  private void checkLimitAndCount(final User user, final Integer idSecuritycurrency, final UserDaySeen userDaySeen,
      final LocalDate today) {
    final Optional<Integer> limitOpt = entityLimitService.resolve(user, LimitKeyConfig.KEY_DAY_HISTORYQUOTE_READ);
    if (limitOpt.isEmpty()) {
      return;
    }
    final int count = userEntityChangeCountJpaRepository.getCudTransactionCount(user.getIdUser(), HISTORYQUOTE_READ);
    if (count >= limitOpt.get()) {
      userService.incrementRightsLimitCount(user.getIdUser(), UserRightLimitCounter.LIMIT_EXCEEDED_TENANT_DATA);
      throw new LimitEntityTransactionException(
          new LimitEntityTransactionError(HISTORYQUOTE_READ, limitOpt.get(), count));
    }
    final UserEntityChangeCount userEntityChangeCount = userEntityChangeCountJpaRepository
        .findById(new UserEntityChangeCountId(user.getIdUser(), today, HISTORYQUOTE_READ))
        .orElse(new UserEntityChangeCount(new UserEntityChangeCountId(user.getIdUser(), today, HISTORYQUOTE_READ)));
    userEntityChangeCount.incrementCounter(OperationType.ADD);
    userEntityChangeCountJpaRepository.save(userEntityChangeCount);
    userDaySeen.seenIds.add(idSecuritycurrency);
  }

  /** Instruments a user has already requested on a given day; replaced on date rollover. */
  private static final class UserDaySeen {
    private final LocalDate day;
    private final Set<Integer> seenIds = ConcurrentHashMap.newKeySet();

    private UserDaySeen(LocalDate day) {
      this.day = day;
    }
  }
}
