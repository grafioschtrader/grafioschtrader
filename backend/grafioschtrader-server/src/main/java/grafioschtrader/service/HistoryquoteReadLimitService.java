package grafioschtrader.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import grafiosch.common.UserAccessHelper;
import grafiosch.entities.User;
import grafiosch.entities.UserEntityChangeCount;
import grafiosch.entities.UserEntityChangeCount.UserEntityChangeCountId;
import grafiosch.entities.projection.UserCountLimit;
import grafiosch.error.LimitEntityTransactionError;
import grafiosch.exceptions.LimitEntityTransactionException;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.UserEntityChangeCountJpaRepository;
import grafiosch.service.UserService;
import grafiosch.types.OperationType;
import grafiosch.types.UserRightLimitCounter;
import grafioschtrader.GlobalParamKeyDefault;

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
  public static final String HISTORYQUOTE_READ = GlobalParamKeyDefault.ENTITY_NAME_HISTORYQUOTE_READ;

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
    if (UserAccessHelper.hasHigherPrivileges(user)) {
      return;
    }
    final LocalDate today = LocalDate.now();
    final UserDaySeen userDaySeen = seenByUser.compute(user.getIdUser(),
        (id, existing) -> existing == null || !existing.day.equals(today) ? new UserDaySeen(today) : existing);
    synchronized (userDaySeen) {
      if (userDaySeen.seenIds.contains(idSecuritycurrency)) {
        return;
      }
      checkLimitAndCount(user, idSecuritycurrency, userDaySeen, today);
    }
  }

  /**
   * Compares the persisted distinct-instrument count against the effective daily limit (per-user override or
   * globalparameter) and either registers the new instrument or escalates and rejects.
   *
   * @param idSecuritycurrency the instrument not yet seen today
   * @param userDaySeen        the caller-locked per-user dedup state for today
   * @param today              the day the access is accounted to
   */
  private void checkLimitAndCount(final User user, final Integer idSecuritycurrency, final UserDaySeen userDaySeen,
      final LocalDate today) {
    final Optional<UserCountLimit> userCountLimitOpt = userEntityChangeCountJpaRepository
        .getCudTransactionAndUserLimit(user.getIdUser(), HISTORYQUOTE_READ);
    final int count = userCountLimitOpt.map(UserCountLimit::getCudTrans).orElse(0);
    final Integer dayLimit = userCountLimitOpt.map(UserCountLimit::getDayLimit).orElse(null);
    final int limit = dayLimit != null ? dayLimit
        : globalparametersJpaRepository.getMaxValueByKey(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_HISTORYQUOTE_READ);
    if (count >= limit) {
      userService.incrementRightsLimitCount(user.getIdUser(), UserRightLimitCounter.LIMIT_EXCEEDED_TENANT_DATA);
      throw new LimitEntityTransactionException(new LimitEntityTransactionError(HISTORYQUOTE_READ, limit, count));
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
