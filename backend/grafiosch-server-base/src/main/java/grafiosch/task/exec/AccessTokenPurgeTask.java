package grafiosch.task.exec;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.config.BaseFeatureConfig;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.OAuthAuthorizationJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.repository.UserAccessTokenJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;

/**
 * Deletes personal access tokens 30 days after they expired. Until then an expired token stays visible in the token
 * dialog, so the user can see which client stopped working and why. It also deletes the OAuth families that were
 * revoked or have ended, together with their tokens, and the expired OAuth tokens that were never consumed.
 *
 * <p>
 * Runs daily at a configurable time (default 23:40 UTC, see {@code g.purge.access.token.cron}) while personal access
 * tokens are switched on.
 * </p>
 */
@Component
public class AccessTokenPurgeTask implements ITask {

  /** Days an expired token is kept before it is deleted. */
  public static final int KEEP_EXPIRED_DAYS = 30;

  private static final Logger log = LoggerFactory.getLogger(AccessTokenPurgeTask.class);

  @Autowired
  private UserAccessTokenJpaRepository userAccessTokenJpaRepository;

  @Autowired
  private OAuthAuthorizationJpaRepository oAuthAuthorizationJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  private BaseFeatureConfig baseFeatureConfig;

  @Scheduled(cron = "${g.purge.access.token.cron:0 40 23 * * ?}", zone = BaseConstants.TIME_ZONE)
  public void purgeExpiredAccessTokens() {
    if (baseFeatureConfig.isAccesstoken()) {
      taskDataChangeRepository.save(new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_LOW));
    }
  }

  @Override
  public ITaskType getTaskType() {
    return TaskTypeBase.PURGE_EXPIRED_ACCESS_TOKEN;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
    int deleted = userAccessTokenJpaRepository.deleteExpiredBefore(nowUtc.minusDays(KEEP_EXPIRED_DAYS));
    int families = oAuthAuthorizationJpaRepository.deleteRevokedOrEnded(nowUtc);
    int oauthTokens = userAccessTokenJpaRepository.deleteExpiredOAuthTokens(nowUtc);
    if (deleted + families + oauthTokens > 0) {
      log.info("Purged {} expired personal access tokens, {} OAuth authorizations and {} expired OAuth tokens", deleted,
          families, oauthTokens);
    }
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }
}
