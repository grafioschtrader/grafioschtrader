package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.BaseConstants;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryCustom;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.RepositoryHelper;
import grafioschtrader.entities.BankruptSecurity;
import grafioschtrader.entities.Security;

/**
 * Custom-impl side of {@link BankruptSecurityJpaRepository}. The rules live here rather than in the resource, so that
 * every write path is bound by them and not only the REST one.
 *
 * <p>
 * Two rules are enforced. Only somebody who may edit the instrument itself may mark it, which for a user holding
 * ROLE_LIMIT_EDIT means the instruments that user created. And an instrument without a single closing price cannot be
 * marked, because the filling derives its prices from the last known one and would silently do nothing.
 * </p>
 *
 * <p>
 * Refusing outright is deliberate here, where an edit of the instrument itself would become a change proposal instead:
 * a proposal waits for approval, and while it waits the reports of everyone holding the instrument stay broken, which
 * is the very situation the marker exists to end.
 * </p>
 */
public class BankruptSecurityJpaRepositoryImpl extends BaseRepositoryImpl<BankruptSecurity>
    implements BaseRepositoryCustom<BankruptSecurity> {

  @Autowired
  private BankruptSecurityJpaRepository bankruptSecurityJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Override
  public BankruptSecurity saveOnlyAttributes(BankruptSecurity newEntity, BankruptSecurity existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    // The instrument of an existing marker is never taken from the request: it is the functional key of the row, and
    // letting an update move it would turn an edit of one instrument into the marking of another.
    if (existingEntity != null) {
      newEntity.setIdSecuritycurrency(existingEntity.getIdSecuritycurrency());
    }
    Security security = securityJpaRepository.findById(newEntity.getIdSecuritycurrency())
        .orElseThrow(() -> new SecurityException(BaseConstants.STEAL_DATA_SECURITY_BREACH));
    if (!UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, security)) {
      throw new SecurityException(BaseConstants.STEAL_DATA_SECURITY_BREACH);
    }
    if (existingEntity == null) {
      assertNotMarkedTwice(newEntity);
      assertHasAQuoteToStartFrom(newEntity);
    }
    return RepositoryHelper.saveOnlyAttributes(bankruptSecurityJpaRepository, newEntity, existingEntity,
        updatePropertyLevelClasses);
  }

  /**
   * The unique index would reject a second marker as well, but as a constraint violation the user cannot read. The
   * message instead says that the instrument is already marked, which is not an error the user has to fix.
   */
  private void assertNotMarkedTwice(BankruptSecurity entity) {
    Optional<BankruptSecurity> existing = bankruptSecurityJpaRepository
        .findByIdSecuritycurrency(entity.getIdSecuritycurrency());
    if (existing.isPresent()) {
      throw new DataViolationException("id.securitycurrency", "gt.bankrupt.already.marked", null);
    }
  }

  private void assertHasAQuoteToStartFrom(BankruptSecurity entity) {
    if (bankruptSecurityJpaRepository.countHistoryquotes(entity.getIdSecuritycurrency()) == 0) {
      throw new DataViolationException("id.securitycurrency", "gt.bankrupt.no.quote", null);
    }
  }
}
