package grafiosch.limit;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import grafiosch.dto.LimitKey;
import grafiosch.entities.TenantBaseID;
import grafiosch.entities.UserBaseID;
import grafiosch.types.LimitType;

/**
 * The catalogue of limit keys. It replaces the three static registries the limit configuration used to be spread over:
 * {@code Globalparameters.defaultLimitMap}, {@code TenantLimitsHelper.GLOBAL_LIMIT_KEY_TO_ENTITY_MAP} and
 * {@code UserEntityChangeLimit.ADDITIONAL_LIMIT_ENTITY_NAMES}.
 *
 * <p>
 * The key space has two halves, because the two limit families need different things.
 * </p>
 *
 * <p>
 * <b>{@link LimitType#MAX} keys are registered explicitly.</b> A counter, a parent relation, two scopes and a message
 * key cannot be derived from an entity name, so an application registers each of them at startup. Saving a row against
 * an unregistered {@code MAX} key is rejected.
 * </p>
 *
 * <p>
 * <b>{@link LimitType#DAY_CUD} and {@link LimitType#DAY_READ} keys are derived, not registered.</b> Their counter is
 * always {@code user_entity_change_count} and they have neither relation nor scopes, so the only thing to validate is
 * the entity name. The valid set is every concrete, non-abstract {@code BaseID} entity that is neither tenant-owned nor
 * an {@code AdminEntity}, taken from the JPA metamodel, plus the pseudo names registered here. This matches the generic
 * daily-check path and lets a per-user row target an entity that has no configured default at all.
 * </p>
 *
 * <p>
 * Registration happens once at startup from the application layer, so that {@code grafiosch-base} and
 * {@code grafiosch-server-base} name no application class.
 * </p>
 */
public abstract class LimitKeyRegistry {

  /** Registered MAX keys, by {@link LimitKey#keyId()}. Insertion ordered so the admin picker has a stable order. */
  private static final Map<String, LimitKeyRegistration> MAX_REGISTRATIONS = new LinkedHashMap<>();

  /**
   * Pseudo entity names, with no JPA entity behind them, that may carry a daily CUD limit, each mapped to the entity
   * whose rows it counts. The class is not used for counting — the counter of a daily key is always
   * {@code user_entity_change_count} — but it is the only way to say whether the name stands for private or for
   * shared data, see {@link #isSharedData(Class)}.
   */
  private static final Map<String, Class<?>> CUD_PSEUDO_ENTITY_NAMES = new LinkedHashMap<>();

  /** Pseudo entity names that carry a daily read limit, with their backing entity. Currently only
   * {@code HistoryquoteRead}. */
  private static final Map<String, Class<?>> READ_PSEUDO_ENTITY_NAMES = new LinkedHashMap<>();

  private LimitKeyRegistry() {
  }

  /**
   * Registers one MAX key. A second registration of the same key replaces the first, which keeps repeated startups in
   * the same JVM — as they happen in tests — idempotent.
   *
   * @param registration the key and everything that belongs to it
   */
  public static void register(LimitKeyRegistration registration) {
    if (registration.limitKey().limitType() != LimitType.MAX) {
      throw new IllegalArgumentException(
          "Only MAX keys are registered; daily keys are derived: " + registration.limitKey().keyId());
    }
    MAX_REGISTRATIONS.put(registration.limitKey().keyId(), registration);
  }

  /**
   * Registers a pseudo entity name that has no JPA entity but may carry a daily CUD limit, so the admin picker offers
   * it.
   *
   * @param entityName         the pseudo name, for example {@code GTNetSecurityImport}
   * @param backingEntityClass the entity whose rows the name stands for, for example {@code Security}. It decides
   *                           whether the administration UI shows the name as private or as shared data
   */
  public static void registerCudPseudoEntityName(String entityName, Class<?> backingEntityClass) {
    CUD_PSEUDO_ENTITY_NAMES.put(entityName, backingEntityClass);
  }

  /**
   * Registers a pseudo entity name for the daily <i>read</i> family. A name registered here resolves as
   * {@link LimitType#DAY_READ} and never as {@link LimitType#DAY_CUD}.
   *
   * @param entityName         the pseudo name, for example {@code HistoryquoteRead}
   * @param backingEntityClass the entity whose rows the name stands for, for example {@code Historyquote}
   */
  public static void registerReadPseudoEntityName(String entityName, Class<?> backingEntityClass) {
    READ_PSEUDO_ENTITY_NAMES.put(entityName, backingEntityClass);
  }

  public static Collection<LimitKeyRegistration> getMaxRegistrations() {
    return MAX_REGISTRATIONS.values();
  }

  public static Set<String> getCudPseudoEntityNames() {
    return CUD_PSEUDO_ENTITY_NAMES.keySet();
  }

  public static Set<String> getReadPseudoEntityNames() {
    return READ_PSEUDO_ENTITY_NAMES.keySet();
  }

  public static Map<String, Class<?>> getCudPseudoEntityClasses() {
    return CUD_PSEUDO_ENTITY_NAMES;
  }

  public static Map<String, Class<?>> getReadPseudoEntityClasses() {
    return READ_PSEUDO_ENTITY_NAMES;
  }

  /**
   * Says whether an entity holds data shared between all tenants or data private to one client or one user. The
   * administration UI shows this per row, because the limit key itself only carries it for the {@code MAX} family, in
   * {@link grafiosch.types.OwnerScope}; the daily families have no scope at all.
   *
   * <p>
   * The test is deliberately negative. Being {@code Auditable} is not what makes data shared: {@code Historyquote} and
   * {@code Securitysplit} extend plain {@code BaseID} and are shared data all the same. What marks data as not shared
   * is that it belongs to exactly one owner, which is what {@link TenantBaseID} and {@link UserBaseID} express.
   * </p>
   *
   * @param entityClass the entity behind a limit key
   * @return true when the entity holds shared data, false when it belongs to one client or one user
   */
  public static boolean isSharedData(Class<?> entityClass) {
    return !TenantBaseID.class.isAssignableFrom(entityClass) && !UserBaseID.class.isAssignableFrom(entityClass);
  }

  public static boolean isReadPseudoEntityName(String entityName) {
    return READ_PSEUDO_ENTITY_NAMES.containsKey(entityName);
  }

  public static Optional<LimitKeyRegistration> find(LimitKey limitKey) {
    return Optional.ofNullable(MAX_REGISTRATIONS.get(limitKey.keyId()));
  }

  public static Optional<LimitKeyRegistration> findByKeyId(String keyId) {
    return Optional.ofNullable(MAX_REGISTRATIONS.get(keyId));
  }

  /**
   * Finds the registration whose message key matches, for the REST contracts that address a limit by its message key.
   *
   * @param msgKey message key such as {@code MAX_CASH_ACCOUNT}
   * @return the registration, or empty when no key carries that message key
   */
  public static Optional<LimitKeyRegistration> findByMsgKey(String msgKey) {
    return MAX_REGISTRATIONS.values().stream().filter(r -> msgKey.equals(r.msgKey())).findFirst();
  }

  /**
   * Finds the lifetime cap the generic REST create path enforces for an entity.
   *
   * <p>
   * Three kinds of registration are excluded. Keys guarding a nested collection, because their entity class is the
   * parent and only the explicit call site knows the parent id. Keys under a pseudo entity name such as
   * {@code SimulationTenant}, which count a subset of a table and would otherwise fire on every ordinary create of
   * that table. And keys whose {@link LimitKeyRegistration#checkedOnGenericCreate()} is false, which already have a
   * hand-written call site with a more specific error.
   * </p>
   *
   * <p>
   * Matching is by assignability rather than class identity, and the most specific registration wins. That is what
   * makes a {@code Securitycashaccount} count against the {@code Cashaccount} cap, which class identity did not.
   * </p>
   *
   * @param entityClass the class of the entity about to be created
   * @return the registration guarding it, or empty when the generic path enforces no cap for this entity
   */
  public static Optional<LimitKeyRegistration> findFlatMaxForEntity(Class<?> entityClass) {
    return MAX_REGISTRATIONS.values().stream()
        .filter(r -> r.checkedOnGenericCreate() && r.limitKey().relationEntityName() == null
            && r.limitKey().entityName().equals(r.entityClass().getSimpleName())
            && r.entityClass().isAssignableFrom(entityClass))
        .reduce((left, right) -> right.entityClass().isAssignableFrom(left.entityClass()) ? left : right);
  }

  /**
   * Converts an entity or pseudo entity name into the {@code UPPER_SNAKE_CASE} translation key under which it is
   * displayed, for example {@code UDFMetadataSecurity} to {@code UDF_METADATA_SECURITY}.
   *
   * @param entityName entity or pseudo entity name
   * @return the display translation key
   */
  public static String toLabelKey(String entityName) {
    return entityName.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }

  /** Clears the registry. Only for tests that need to build an isolated key space. */
  public static void clear() {
    MAX_REGISTRATIONS.clear();
    CUD_PSEUDO_ENTITY_NAMES.clear();
    READ_PSEUDO_ENTITY_NAMES.clear();
  }
}
