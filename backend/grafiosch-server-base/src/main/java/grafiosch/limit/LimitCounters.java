package grafiosch.limit;

import grafiosch.types.OwnerScope;

/**
 * Ready-made {@link EntityLimitCounter} implementations for the flat cases, where the cap simply bounds the number of
 * rows of one table. Keys that guard the elements of a nested collection register their own lambda, because those need
 * a join the generic form cannot express.
 */
public abstract class LimitCounters {

  private LimitCounters() {
  }

  /**
   * Counts the rows of one entity that belong to the acting user's tenant. This is the counter behind every
   * {@link OwnerScope#TENANT} cap.
   *
   * <p>
   * The JPQL {@code count} is polymorphic, so a subclass row is counted against its parent's cap — a
   * {@code Securitycashaccount} counts against the {@code Cashaccount} key.
   * </p>
   *
   * @param jpqlEntityName JPA entity name to count
   * @return counter returning 0 when there is no acting user
   */
  public static EntityLimitCounter tenantCount(String jpqlEntityName) {
    return (entityManager, user, _, _) -> user == null || user.getIdTenant() == null ? 0
        : entityManager.createQuery("SELECT count(t) FROM " + jpqlEntityName + " t WHERE t.idTenant = ?1", Long.class)
            .setParameter(1, user.getIdTenant()).getSingleResult().intValue();
  }

  /**
   * Counts the rows of one entity whose current {@code created_by} is the acting user. This is the counter behind
   * every {@link OwnerScope#CREATOR} cap on shared data.
   *
   * @param jpqlEntityName JPA entity name to count
   * @return counter returning 0 when there is no acting user
   */
  public static EntityLimitCounter creatorCount(String jpqlEntityName) {
    return (entityManager, user, _, _) -> user == null ? 0
        : entityManager.createQuery("SELECT count(t) FROM " + jpqlEntityName + " t WHERE t.createdBy = ?1", Long.class)
            .setParameter(1, user.getIdUser()).getSingleResult().intValue();
  }

  /**
   * Counts every row of one entity, regardless of who owns it. This is the counter behind an {@link OwnerScope#GLOBAL}
   * cap.
   *
   * @param jpqlEntityName JPA entity name to count
   * @return counter over the whole table
   */
  public static EntityLimitCounter globalCount(String jpqlEntityName) {
    return (entityManager, _, _, _) -> entityManager
        .createQuery("SELECT count(t) FROM " + jpqlEntityName + " t", Long.class).getSingleResult().intValue();
  }
}
