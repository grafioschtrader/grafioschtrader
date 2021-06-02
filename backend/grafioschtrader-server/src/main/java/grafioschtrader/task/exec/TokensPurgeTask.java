package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.repository.UserJpaRepository;

/**
 * Deletes all expired token of the registration process.
 *
 */
@Service
@Transactional
public class TokensPurgeTask {

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Scheduled(cron = "${gt.purge.cron.expression}", zone = GlobalConstants.TIME_ZONE)
  public void purgeExpired() {
    userJpaRepository.removeWithExpiredVerificationToken();
  }
}