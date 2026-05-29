package grafioschtrader.rest;

import org.springframework.http.ResponseEntity;

import grafiosch.common.UserAccessHelper;
import grafiosch.entities.Auditable;
import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteAudit;
import grafiosch.types.OperationType;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.BaseHistoryquote;

/**
 * Shared REST behaviour for the live {@link grafioschtrader.entities.Historyquote} and the archived
 * {@link grafioschtrader.entities.HistoryquoteLegacy} quote tables. Both are public, shared data owned by a parent
 * security or currency pair, so edits by a user without direct rights are routed through the propose-change approval
 * flow ({@link #updateSpecialEntity}); rights are evaluated against the parent rather than the quote row itself.
 *
 * @param <T> the concrete quote entity type
 */
public abstract class HistoryquoteResourceBase<T extends BaseHistoryquote> extends UpdateCreateDeleteAudit<T> {

  @Override
  protected ResponseEntity<T> updateSpecialEntity(User user, T entity) throws Exception {
    Auditable parentEntity = getParentSecurityCurrency(user, entity.getIdSecuritycurrency());
    ResponseEntity<T> result = checkProposeChangeAndSave(user, entity, parentEntity,
        UserAccessHelper.hasHigherPrivileges(user));
    // Count this individual-record edit/proposal against the user's daily CUD limit. Unlike the Auditable branch in
    // UpdateCreate.updateEntity, this special-entity path is not logged by the framework, so without this call the
    // daily limit guard never sees a counter row and a limited user could create unbounded propose-change rows.
    logAddUpdDel(user.getIdUser(), entity, OperationType.UPDATE);
    return result;
  }

  @Override
  protected boolean hasRightsForEditingEntity(User user, T newEntity, T existingEntity, Auditable parentEntity) {
    if (!isImmutableCreateType(existingEntity)) {
      return UserAccessHelper.hasRightsForEditingOrDeleteOnEntity(user, parentEntity);
    }
    return false;
  }

  @Override
  protected boolean hasRightsForDeleteEntity(User user, T entity) {
    if (!isImmutableCreateType(entity)) {
      Auditable parentEntity = getParentSecurityCurrency(user, entity.getIdSecuritycurrency());
      return UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, parentEntity);
    }
    return false;
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

  /**
   * Resolves the owning security or currency pair of a quote row, used as the auditable parent for rights checks.
   *
   * @param user               the acting user
   * @param idSecuritycurrency  the owning security/currencypair id
   * @return the parent as an {@link Auditable}
   */
  protected abstract Auditable getParentSecurityCurrency(User user, Integer idSecuritycurrency);

  /**
   * Whether the given row's creation type forbids manual editing/deletion. The live table forbids editing CALCULATED
   * rows; the archive has no such restriction.
   *
   * @param entity the quote row to test
   * @return {@code true} if the row must not be edited or deleted
   */
  protected abstract boolean isImmutableCreateType(T entity);

}
