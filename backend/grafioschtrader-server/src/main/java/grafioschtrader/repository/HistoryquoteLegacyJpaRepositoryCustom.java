package grafioschtrader.repository;

import grafiosch.entities.Auditable;
import grafiosch.entities.User;
import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.dto.UserAuditable;
import grafioschtrader.entities.HistoryquoteLegacy;

public interface HistoryquoteLegacyJpaRepositoryCustom extends BaseRepositoryCustom<HistoryquoteLegacy> {

  /**
   * Resolves the owning security or currency pair of an archived row, used as the auditable parent for rights checks in
   * the propose-change flow.
   *
   * @param user               the acting user (reserved for future per-tenant scoping)
   * @param idSecuritycurrency  the owning security/currencypair id of the archived row
   * @return the parent security or currency pair as an {@link Auditable}
   */
  Auditable getParentSecurityCurrency(User user, Integer idSecuritycurrency);

  /**
   * Returns the current user together with the owning security/currency pair, throwing a security exception when the
   * user lacks edit/delete rights. Used by the bulk legacy operations (split, delete-all, CSV import).
   *
   * @param idSecuritycurrency the owning security/currencypair id
   * @return the user paired with the auditable parent
   */
  UserAuditable getUserAndCheckSecurityAccess(Integer idSecuritycurrency);
}
