package grafiosch.limit;

import grafiosch.dto.LimitKey;
import grafiosch.dto.LimitKeyDefinition;

/**
 * One registered {@link grafiosch.types.LimitType#MAX} limit key with everything that cannot be derived from an entity
 * name: which entity it guards, how existing rows are counted, what a fresh installation seeds, how an admin edit is
 * validated, and which message key the REST limit contracts return for it.
 *
 * @param limitKey              the key tuple
 * @param entityClass           entity the key guards. Matching is by assignability, so a subclass is counted against
 *                              its parent's cap
 * @param counter               answers how many rows already exist
 * @param defaultValue          value a fresh installation seeds. The value of an existing installation comes from the
 *                              migrated {@code globalparameters} row, never from here
 * @param inputRule             validation rule for the limit value in the {@code globalparameters.input_rule} DSL, or
 *                              {@code null}. It belongs to the key, so a per-role or per-user override is validated
 *                              exactly like the {@code ALL} default row
 * @param msgKey                message key the limit REST contracts return, for example {@code MAX_CASH_ACCOUNT}.
 *                              Carried explicitly because it cannot be derived: the entity name would give
 *                              {@code MAX_CASHACCOUNT}
 * @param checkedOnGenericCreate whether the generic REST create path enforces this key on its own. It is {@code false}
 *                              for every key that already has a hand-written call site, so that the caller's specific
 *                              and translated error survives instead of being pre-empted by the generic one
 */
public record LimitKeyRegistration(LimitKey limitKey, Class<?> entityClass, EntityLimitCounter counter,
    int defaultValue, String inputRule, String msgKey, boolean checkedOnGenericCreate) {

  public LimitKeyRegistration {
    if (limitKey == null || entityClass == null || counter == null) {
      throw new IllegalArgumentException("A MAX limit key registration requires a key, an entity class and a counter");
    }
  }

  public LimitKeyDefinition toDefinition() {
    return new LimitKeyDefinition(limitKey, LimitKeyRegistry.toLabelKey(limitKey.entityName()), inputRule, defaultValue,
        msgKey, true, LimitKeyRegistry.isSharedData(entityClass));
  }
}
