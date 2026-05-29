package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.Auditable;
import grafiosch.entities.User;
import grafiosch.repository.BaseRepositoryImpl;
import grafioschtrader.dto.UserAuditable;
import grafioschtrader.entities.HistoryquoteLegacy;
import grafioschtrader.types.HistoryquoteCreateType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Custom repository operations for the {@code historyquote_legacy} shadow archive. The save path deliberately does
 * <b>not</b> run any of the live-table maintenance (weekend gap filling, derived-instrument recalculation, holdings
 * rebuild) that {@link HistoryquoteJpaRepositoryImpl} performs — an archived row is a frozen historical record, so a
 * manual edit only persists the row itself.
 */
public class HistoryquoteLegacyJpaRepositoryImpl extends BaseRepositoryImpl<HistoryquoteLegacy>
    implements HistoryquoteLegacyJpaRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private HistoryquoteLegacyJpaRepository historyquoteLegacyJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Override
  @Transactional
  public HistoryquoteLegacy saveOnlyAttributes(final HistoryquoteLegacy historyquoteLegacy,
      final HistoryquoteLegacy existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws Exception {
    if (existingEntity != null) {
      // The trading date and the archival metadata of an archived row are immutable; only the prices may change.
      if (!historyquoteLegacy.getDate().equals(existingEntity.getDate())) {
        throw new SecurityException(BaseConstants.FILED_EDIT_SECURITY_BREACH);
      }
      historyquoteLegacy.setIdSecuritycurrency(existingEntity.getIdSecuritycurrency());
      historyquoteLegacy.setTransferDate(existingEntity.getTransferDate());
    } else if (historyquoteLegacy.getTransferDate() == null) {
      historyquoteLegacy.setTransferDate(LocalDate.now());
    }
    historyquoteLegacy.setCreateType(HistoryquoteCreateType.ADD_MODIFIED_USER);
    return historyquoteLegacyJpaRepository.save(historyquoteLegacy);
  }

  @Override
  public Auditable getParentSecurityCurrency(final User user, final Integer idSecuritycurrency) {
    return securityJpaRepository.findById(idSecuritycurrency).map(security -> (Auditable) security)
        .orElseGet(() -> currencypairJpaRepository.getReferenceById(idSecuritycurrency));
  }

  @Override
  public UserAuditable getUserAndCheckSecurityAccess(Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Auditable auditable = getParentSecurityCurrency(user, idSecuritycurrency);
    if (!UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, auditable)) {
      throw new SecurityException(BaseConstants.LIMIT_SECURITY_BREACH);
    }
    return new UserAuditable(auditable, user);
  }

}
