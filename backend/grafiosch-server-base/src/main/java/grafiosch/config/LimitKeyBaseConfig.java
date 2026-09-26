package grafiosch.config;

import grafiosch.dto.LimitKey;
import grafiosch.entities.MailSendRecv;
import grafiosch.entities.MailSettingForward;
import grafiosch.entities.ProposeUserTask;
import grafiosch.entities.TenantAccess;
import grafiosch.entities.UDFMetadataGeneral;
import grafiosch.entities.User;
import grafiosch.limit.LimitCounters;
import grafiosch.limit.LimitKeyRegistration;
import grafiosch.limit.LimitKeyRegistry;
import grafiosch.types.OwnerScope;
import grafiosch.types.SendRecvType;
import grafiosch.types.TenantAccessLevel;

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
   * Pseudo entity name for the read-only accounts an owner may invite into their own tenant. It is not the {@code User}
   * entity itself: only the invited viewers are counted, never the owner and never a user of another tenant, and there
   * is no generic create path that could enforce a {@code User} cap.
   */
  public static final String ENTITY_NAME_SHARE_INVITE = "ShareInvite";

  /**
   * Pseudo entity name for the managed clients of an advisor. One managed client is an enabled user together with a
   * whole tenant, and neither the {@code User} nor the tenant entity can carry the cap: the client user is not counted
   * against its creator, and the client tenant has no {@code id_parent_tenant} - that column belongs to the simulation
   * environments, which the application counts under a key of its own.
   */
  public static final String ENTITY_NAME_MANAGED_CLIENT = "ManagedClient";

  /**
   * Bounds how many read-only viewer accounts one tenant may create through the share mapping. Every invite of an
   * e-mail without an account writes an enabled {@code user} row and sends an outbound mail; sharing with an existing
   * account writes only a {@code tenant_access} row, which is keyed per user and tenant and therefore bounded already.
   */
  public static final LimitKey KEY_SHARE_INVITE = LimitKey.max(ENTITY_NAME_SHARE_INVITE, OwnerScope.TENANT);

  /**
   * Bounds how many managed clients one advisor may create. Counted as the advisor's {@code MANAGE} grants in
   * {@code tenant_access}, which {@code createclient} writes for each client and the deletion of a client removes again.
   */
  public static final LimitKey KEY_MANAGED_CLIENT = LimitKey.max(ENTITY_NAME_MANAGED_CLIENT, OwnerScope.TENANT);

  // Lifetime caps on the library entities a user may create; each already had a daily budget, but no ceiling.
  public static final LimitKey KEY_PROPOSE_USER_TASK = LimitKey.max(ProposeUserTask.class.getSimpleName(),
      OwnerScope.CREATOR);
  public static final LimitKey KEY_UDF_METADATA_GENERAL = LimitKey.max(UDFMetadataGeneral.class.getSimpleName(),
      OwnerScope.CREATOR);
  public static final LimitKey KEY_MAIL_SETTING_FORWARD = LimitKey.max(MailSettingForward.class.getSimpleName(),
      OwnerScope.CREATOR);
  public static final LimitKey KEY_MAIL_SEND_RECV = LimitKey.max(MailSendRecv.class.getSimpleName(),
      OwnerScope.CREATOR);

  private static final String RULE_ACCOUNT_CAP = "min:1,max:1000";

  private LimitKeyBaseConfig() {
  }

  public static void initialize() {
    LimitKeyRegistry
        .register(
            new LimitKeyRegistration(KEY_SHARE_INVITE, User.class,
                (entityManager, user, _, _) -> user == null || user.getIdTenant() == null ? 0
                    : entityManager.createQuery(
                        "SELECT count(u) FROM User u WHERE u.idTenant = ?1 AND u.homeTenantReadOnly = true", Long.class)
                        .setParameter(1, user.getIdTenant()).getSingleResult().intValue(),
                20, RULE_ACCOUNT_CAP, "MAX_SHARE_INVITE", false));

    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_MANAGED_CLIENT, TenantAccess.class,
        (entityManager, user, _, _) -> user == null ? 0
            : entityManager
                .createQuery("SELECT count(a) FROM TenantAccess a WHERE a.idUser = ?1 AND a.accessLevel = ?2",
                    Long.class)
                .setParameter(1, user.getIdUser()).setParameter(2, TenantAccessLevel.MANAGE.getValue())
                .getSingleResult().intValue(),
        20, RULE_ACCOUNT_CAP, "MAX_MANAGED_CLIENT", false));

    registerUserOwnedCaps();
  }

  /**
   * Caps enforced by the generic create path. A {@code ProposeUserTask} is counted by {@code created_by}; the internal
   * {@code createReleaseLougout} path bypasses the check on purpose, because a locked out user must always be able to
   * reach an administrator. The other three have no {@code created_by} and are counted by their user column; a message
   * only by its {@code SEND} row, since every internal message is stored twice.
   */
  private static void registerUserOwnedCaps() {
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_PROPOSE_USER_TASK, ProposeUserTask.class,
        LimitCounters.creatorCount(ProposeUserTask.class.getSimpleName()), 100, null, "MAX_PROPOSE_USER_TASK", true));
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_UDF_METADATA_GENERAL, UDFMetadataGeneral.class,
        LimitCounters.userCount(UDFMetadataGeneral.class.getSimpleName(), "idUser"), 50, null,
        "MAX_UDF_METADATA_GENERAL", true));
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_MAIL_SETTING_FORWARD, MailSettingForward.class,
        LimitCounters.userCount(MailSettingForward.class.getSimpleName(), "idUser"), 50, null,
        "MAX_MAIL_SETTING_FORWARD", true));
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_MAIL_SEND_RECV, MailSendRecv.class,
        (entityManager, user, _, _) -> user == null ? 0
            : entityManager
                .createQuery("SELECT count(m) FROM MailSendRecv m WHERE m.idUserFrom = ?1 AND m.sendRecv = ?2",
                    Long.class)
                .setParameter(1, user.getIdUser()).setParameter(2, SendRecvType.SEND.getValue()).getSingleResult()
                .intValue(),
        2000, null, "MAX_MAIL_SEND_RECV", true));
  }
}
