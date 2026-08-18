package grafiosch.config;

import grafiosch.dto.LimitKey;
import grafiosch.entities.User;
import grafiosch.limit.LimitKeyRegistration;
import grafiosch.limit.LimitKeyRegistry;
import grafiosch.types.OwnerScope;

/**
 * Declares the lifetime ({@code MAX}) limit keys the reusable library enforces itself, so that a key checked by
 * {@code grafiosch-server-base} does not have to be registered by every application on top of it.
 *
 * <p>
 * It is the library counterpart of the application's own limit key configuration and has to be called from it; the
 * application layer stays the place that decides the whole set of caps, because it is also the only layer that can seed
 * their default rows in a migration.
 * </p>
 *
 * <p>
 * A key with no {@code entity_limit} row resolves as unlimited, so registering one here is only half the work - the
 * default has to be seeded with the same literal, which the seed guard of the application asserts.
 * </p>
 */
public abstract class LimitKeyBaseConfig {

  /**
   * Pseudo entity name for the read-only accounts an owner may invite into their own tenant. It is not the
   * {@code User} entity itself: only the invited viewers are counted, never the owner and never a user of another
   * tenant, and there is no generic create path that could enforce a {@code User} cap.
   */
  public static final String ENTITY_NAME_SHARE_INVITE = "ShareInvite";

  /**
   * Bounds how many read-only viewer accounts one tenant may create through the share mapping. Every invite of an
   * e-mail without an account writes an enabled {@code user} row and sends an outbound mail; sharing with an existing
   * account writes only a {@code tenant_access} row, which is keyed per user and tenant and therefore bounded already.
   */
  public static final LimitKey KEY_SHARE_INVITE = LimitKey.max(ENTITY_NAME_SHARE_INVITE, OwnerScope.TENANT);

  private LimitKeyBaseConfig() {
  }

  public static void initialize() {
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_SHARE_INVITE, User.class,
        (entityManager, user, _, _) -> user == null || user.getIdTenant() == null ? 0
            : entityManager
                .createQuery("SELECT count(u) FROM User u WHERE u.idTenant = ?1 AND u.homeTenantReadOnly = true",
                    Long.class)
                .setParameter(1, user.getIdTenant()).getSingleResult().intValue(),
        20, "min:1,max:1000", "MAX_SHARE_INVITE", false));
  }
}
