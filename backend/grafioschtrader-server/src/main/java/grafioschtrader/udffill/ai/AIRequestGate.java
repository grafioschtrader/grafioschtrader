package grafioschtrader.udffill.ai;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.service.DailyLimitService;
import grafiosch.types.OperationType;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.AIRequestLog;
import grafioschtrader.repository.AIRequestLogJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.AIKeySource;
import grafioschtrader.types.AIRequestPurpose;
import grafioschtrader.types.AIRequestStatus;
import grafioschtrader.udffill.ai.AIChatModelProvider.AIProviderChoice;

/**
 * Every round trip to a language model passes this gate. In one JVM-wide critical section — the application runs one
 * context per database — it deletes the log rows older than the retention, checks the daily budget of the user (DAY_CUD
 * pseudo entity {@code AIRequest}) and the budget of the whole instance for the current UTC day, and reserves: a log
 * row with status RESERVED is committed and the daily counter of the user is raised. A reservation counts whatever
 * happens afterwards, including a failed call or a crash, because its billing state is unknown.
 */
@Service
public class AIRequestGate {

  private static final Object LOCK = new Object();

  private final AIRequestLogJpaRepository aiRequestLogJpaRepository;
  private final DailyLimitService dailyLimitService;
  private final GlobalparametersService globalparametersService;
  private final TransactionTemplate transactionTemplate;

  public AIRequestGate(AIRequestLogJpaRepository aiRequestLogJpaRepository, DailyLimitService dailyLimitService,
      GlobalparametersService globalparametersService, PlatformTransactionManager transactionManager) {
    this.aiRequestLogJpaRepository = aiRequestLogJpaRepository;
    this.dailyLimitService = dailyLimitService;
    this.globalparametersService = globalparametersService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Reserves one round trip, or refuses it when a limit is used up. The daily limit the tenant set for its key is
   * checked first, so a key at its limit is skipped without counting against the daily budget of the user.
   *
   * @param user    the user on whose behalf the model is called
   * @param purpose what the call is for
   * @param choice  the provider, model and key source; for a tenant key also its row and daily limit
   * @return the ID of the reservation, to be completed with {@link #complete}
   * @throws AITenantLimitException                               when the daily limit of the tenant key is reached
   * @throws DataViolationException                               when the instance budget is used up or disabled
   * @throws grafiosch.exceptions.LimitEntityTransactionException when the daily budget of the user is used up
   */
  public Integer reserve(User user, AIRequestPurpose purpose, AIProviderChoice choice) {
    AIKeySource keySource = choice.keySource();
    synchronized (LOCK) {
      return transactionTemplate.execute(_ -> {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        aiRequestLogJpaRepository
            .deleteOlderThan(now.minusDays(globalparametersService.getAIRequestLogRetentionDays()));
        Integer idTenantAIProvider = keySource == AIKeySource.TENANT ? choice.idTenantAIProvider() : null;
        if (idTenantAIProvider != null && choice.dailyRequestLimit() != null
            && aiRequestLogJpaRepository.countByIdTenantAIProviderAndRequestTimeGreaterThanEqual(idTenantAIProvider,
                now.toLocalDate().atStartOfDay()) + 1 > choice.dailyRequestLimit()) {
          throw new AITenantLimitException(choice.dailyRequestLimit());
        }
        dailyLimitService.check(user, LimitKeyConfig.ENTITY_NAME_AI_REQUEST, 1);
        if (keySource == AIKeySource.INSTANCE) {
          int instanceLimit = globalparametersService.getAIDailyRequestsInstance();
          long today = aiRequestLogJpaRepository.countByKeySourceAndRequestTimeGreaterThanEqual(
              AIKeySource.INSTANCE.getValue(), now.toLocalDate().atStartOfDay());
          if (today + 1 > instanceLimit) {
            throw new DataViolationException("ai.request", "gt.ai.daily.requests.instance.exceeded",
                new Object[] { instanceLimit });
          }
        }
        AIRequestLog reservation = aiRequestLogJpaRepository
            .save(new AIRequestLog(user.getIdUser(), now, purpose, keySource, choice.provider(), idTenantAIProvider,
                truncate(choice.model(), 64)));
        dailyLimitService.log(user.getIdUser(), LimitKeyConfig.ENTITY_NAME_AI_REQUEST, OperationType.ADD, 1);
        return reservation.getIdAIRequestLog();
      });
    }
  }

  /**
   * Records the outcome of a reserved round trip; the reservation keeps counting either way.
   *
   * @param idAIRequestLog the reservation
   * @param status         SUCCEEDED or FAILED
   * @param inputTokens    reported input tokens, or null
   * @param outputTokens   reported output tokens, or null
   */
  public void complete(Integer idAIRequestLog, AIRequestStatus status, Integer inputTokens, Integer outputTokens) {
    transactionTemplate.executeWithoutResult(_ -> aiRequestLogJpaRepository.findById(idAIRequestLog).ifPresent(log -> {
      log.setStatus(status);
      log.setInputTokens(inputTokens);
      log.setOutputTokens(outputTokens);
      aiRequestLogJpaRepository.save(log);
    }));
  }

  private static String truncate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
