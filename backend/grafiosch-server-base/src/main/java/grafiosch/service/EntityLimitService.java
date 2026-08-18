package grafiosch.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import grafiosch.dto.LimitKey;
import grafiosch.entities.EntityLimit;
import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.limit.EntityLimitCache;
import grafiosch.limit.LimitKeyRegistration;
import grafiosch.limit.LimitKeyRegistry;
import grafiosch.repository.EntityLimitJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Resolves the configured value of a limit key for a user, and counts what already exists against it.
 *
 * <p>
 * Resolution has exactly four steps and no compiled fallback behind them:
 * </p>
 * <ol>
 * <li>the row for this user;</li>
 * <li>the row for the user's most privileged role;</li>
 * <li>the {@code ALL} row, which has neither a role nor a user;</li>
 * <li>no row at all, which means <b>unlimited</b>.</li>
 * </ol>
 *
 * <p>
 * Only the highest ranked role is consulted, never the numeric maximum over the user's roles. Roles are cumulative —
 * {@code User.setMostPrivilegedRole} materialises {@code ADMIN} as {@code {ADMIN, ALLEDIT, USER}} — so taking a maximum
 * would let a limit written for {@code ROLE_USER} apply to every administrator.
 * </p>
 *
 * <p>
 * Step 4 is reachable for a daily key nobody has written a row for, or for a {@code MAX} key registered in a later
 * release before its seed row exists. It is not reachable by deleting a mandatory row, because the {@code ALL} row of a
 * registered {@code MAX} key cannot be deleted.
 * </p>
 */
@Service
public class EntityLimitService {

  private static final Logger log = LoggerFactory.getLogger(EntityLimitService.class);

  @PersistenceContext
  private EntityManager entityManager;

  private final EntityLimitJpaRepository entityLimitJpaRepository;

  private final EntityLimitCache entityLimitCache;

  public EntityLimitService(EntityLimitJpaRepository entityLimitJpaRepository, EntityLimitCache entityLimitCache) {
    this.entityLimitJpaRepository = entityLimitJpaRepository;
    this.entityLimitCache = entityLimitCache;
  }

  /**
   * Resolves the configured limit for a user.
   *
   * @param user     the acting user, or {@code null} in a background context with no authenticated user. A
   *                 {@code null} user skips the user and role steps and lands on the {@code ALL} row
   * @param limitKey the key to resolve
   * @return the configured cap, or empty when nothing is configured, which means unlimited
   */
  public Optional<Integer> resolve(User user, LimitKey limitKey) {
    return entityLimitCache.get(user == null ? null : user.getIdUser(), limitKey, () -> resolveUncached(user, limitKey));
  }

  /** Resolves for the user of the current security context. */
  public Optional<Integer> resolveForCurrentUser(LimitKey limitKey) {
    return resolve(getCurrentUserOrNull(), limitKey);
  }

  private Optional<Integer> resolveUncached(User user, LimitKey limitKey) {
    List<EntityLimit> rows = entityLimitJpaRepository
        .findByLimitTypeAndEntityName(limitKey.limitType().getValue(), limitKey.entityName()).stream()
        .filter(row -> limitKey.equals(row.getLimitKey())).filter(this::isNotExpired).toList();

    if (user != null) {
      Optional<Integer> byUser = pick(rows, row -> user.getIdUser().equals(row.getIdUser()));
      if (byUser.isPresent()) {
        return byUser;
      }
      Integer idMostPrivilegedRole = getMostPrivilegedRoleId(user);
      if (idMostPrivilegedRole != null) {
        Optional<Integer> byRole = pick(rows, row -> idMostPrivilegedRole.equals(row.getIdRole()));
        if (byRole.isPresent()) {
          return byRole;
        }
      }
    }
    return pick(rows, row -> row.getIdRole() == null && row.getIdUser() == null);
  }

  private Optional<Integer> pick(List<EntityLimit> rows, Predicate<EntityLimit> matcher) {
    return rows.stream().filter(matcher).findFirst().map(EntityLimit::getLimitValue);
  }

  /** An expired row is invisible to the resolver and falls through to the next step; it is not purged here. */
  private boolean isNotExpired(EntityLimit entityLimit) {
    return entityLimit.getValidUntil() == null || !entityLimit.getValidUntil().isBefore(LocalDate.now());
  }

  /**
   * Maps the user's most privileged role name back to its id. The role entities are eagerly loaded on the user, so no
   * additional query is needed.
   */
  private Integer getMostPrivilegedRoleId(User user) {
    String rolename = user.getMostPrivilegedRole();
    if (rolename == null || user.getRoles() == null) {
      return null;
    }
    return user.getRoles().stream().filter(role -> rolename.equals(role.getRolename())).map(Role::getIdRole).findFirst()
        .orElse(null);
  }

  /**
   * Counts what already exists against a registered {@code MAX} key.
   *
   * @param user     the acting user, or {@code null} in a background context
   * @param limitKey a registered {@code MAX} key
   * @param parentId the parent the new element goes into, required for {@code countScope = SINGLE}
   * @return the number of existing rows counted against the limit
   * @throws IllegalArgumentException when the key is not registered, so a counter cannot be found
   */
  public int count(User user, LimitKey limitKey, Integer parentId) {
    LimitKeyRegistration registration = LimitKeyRegistry.find(limitKey).orElseThrow(
        () -> new IllegalArgumentException("No counter registered for limit key " + limitKey.keyId()));
    return registration.counter().count(entityManager, user, limitKey, parentId);
  }

  /**
   * Checks whether a number of additional rows still fits under a {@code MAX} cap.
   *
   * <p>
   * Enforcement is deliberately not serialized: the count is read and the write follows without a lock, a reservation
   * row or a raised isolation level, so two concurrent requests can both observe room and a cap can be overshot by at
   * most the number of requests in flight. The next request is then rejected. These are anti-flooding thresholds, not
   * accounting, and the alternative would put a lock on hot paths such as transaction create.
   * </p>
   *
   * @param user       the acting user, or {@code null} in a background context
   * @param limitKey   a registered {@code MAX} key
   * @param parentId   the parent the new elements go into, required for {@code countScope = SINGLE}
   * @param additional how many rows are about to be created
   * @return true when the cap is unlimited or the additional rows still fit
   */
  public boolean fitsWithinLimit(User user, LimitKey limitKey, Integer parentId, int additional) {
    Optional<Integer> limit = resolve(user, limitKey);
    return limit.isEmpty() || count(user, limitKey, parentId) + additional <= limit.get();
  }

  /**
   * Returns the acting user, or {@code null} when the call runs without an authenticated user, as it does in scheduled
   * tasks. Callers must tolerate {@code null}; {@link #resolve(User, LimitKey)} does.
   */
  public User getCurrentUserOrNull() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getDetails() instanceof User user ? user : null;
  }

  /** Drops the resolver cache for one user. Called when that user's roles change. */
  public void evictUser(Integer idUser) {
    entityLimitCache.evictUser(idUser);
  }

  /**
   * Warns about every registered {@code MAX} key without an {@code ALL} row. Such a key is unlimited, which is never
   * intended for a mandatory cap and normally means the seed statement of a newly registered key is missing.
   */
  public void logMaxKeysWithoutDefaultRow() {
    for (LimitKeyRegistration registration : LimitKeyRegistry.getMaxRegistrations()) {
      LimitKey limitKey = registration.limitKey();
      boolean hasAllRow = entityLimitJpaRepository
          .findByLimitTypeAndEntityName(limitKey.limitType().getValue(), limitKey.entityName()).stream()
          .anyMatch(row -> limitKey.equals(row.getLimitKey()) && row.getIdRole() == null && row.getIdUser() == null);
      if (!hasAllRow) {
        log.warn("Registered limit key '{}' has no default row in {}; it is currently unlimited.", limitKey.keyId(),
            EntityLimit.TABNAME);
      }
    }
  }
}
