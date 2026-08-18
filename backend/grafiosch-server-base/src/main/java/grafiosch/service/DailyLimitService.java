package grafiosch.service;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;

import grafiosch.dto.LimitKey;
import grafiosch.entities.User;
import grafiosch.entities.UserEntityChangeCount;
import grafiosch.entities.UserEntityChangeCount.UserEntityChangeCountId;
import grafiosch.error.LimitEntityTransactionError;
import grafiosch.exceptions.LimitEntityTransactionException;
import grafiosch.repository.UserEntityChangeCountJpaRepository;
import grafiosch.types.OperationType;

/**
 * Enforces and books the daily CUD budget of an entity or pseudo entity name.
 *
 * <p>
 * It holds what {@link grafiosch.rest.DailyLimitUpdCreateLogger} used to implement itself. The logic moved here because
 * the budget is not only consumed by REST controllers: bulk write paths live in repository implementations and
 * services, which cannot reach the protected methods of an abstract controller base class. The controller base class
 * now delegates, so a single-row create through {@code UpdateCreate} and a bulk upload are accounted the same way.
 * </p>
 *
 * <p>
 * Which users a budget applies to is entirely a matter of configuration. {@link EntityLimitService} resolves a row
 * written for the user, then one for the user's most privileged role, then the default row of the key; no row at all
 * means unlimited. There is no hardcoded restriction to {@code ROLE_LIMITEDIT}.
 * </p>
 */
@Service
public class DailyLimitService {

  private final UserEntityChangeCountJpaRepository userEntityChangeCountJpaRepository;

  private final EntityLimitService entityLimitService;

  public DailyLimitService(UserEntityChangeCountJpaRepository userEntityChangeCountJpaRepository,
      EntityLimitService entityLimitService) {
    this.userEntityChangeCountJpaRepository = userEntityChangeCountJpaRepository;
    this.entityLimitService = entityLimitService;
  }

  /**
   * Checks whether a number of further operations still fits into today's budget of an entity, and throws before the
   * first row is written when it does not.
   *
   * <p>
   * The whole request is rejected as one unit: a bulk upload that would cross the budget writes nothing at all rather
   * than stopping halfway through. The limit is resolved independently of the counter row, so a missing counter row
   * counts as 0 rather than skipping the check.
   * </p>
   *
   * @param user       the user performing the operation, whose id the counter is kept under
   * @param entityName entity or pseudo entity name the budget is configured for
   * @param additional how many units this operation costs; a value below one is treated as one
   * @throws LimitEntityTransactionException when the budget is configured and would be exceeded
   */
  public void check(User user, String entityName, int additional) {
    Optional<Integer> limitOpt = entityLimitService.resolve(user, LimitKey.dayCud(entityName));
    if (limitOpt.isPresent()) {
      int cudTransaction = userEntityChangeCountJpaRepository.getCudTransactionCount(user.getIdUser(), entityName);
      if (cudTransaction + Math.max(additional, 1) > limitOpt.get()) {
        throw new LimitEntityTransactionException(
            new LimitEntityTransactionError(entityName, limitOpt.get(), cudTransaction));
      }
    }
  }

  /**
   * Books a number of performed operations on today's counter of an entity.
   *
   * @param idUser        the user who performed the operation
   * @param entityName    entity or pseudo entity name the counter is kept under
   * @param operationType which of the three counters is raised
   * @param count         how many units to book; a value below one is treated as one
   */
  public void log(Integer idUser, String entityName, OperationType operationType, int count) {
    UserEntityChangeCountId id = new UserEntityChangeCountId(idUser, LocalDate.now(), entityName);
    UserEntityChangeCount userEntityChangeCount = userEntityChangeCountJpaRepository.findById(id)
        .orElse(new UserEntityChangeCount(id));
    userEntityChangeCount.incrementCounter(operationType, count);
    userEntityChangeCountJpaRepository.save(userEntityChangeCount);
  }
}
