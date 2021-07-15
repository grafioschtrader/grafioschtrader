package grafioschtrader.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;

@Component
public class SecurityServiceAsyncExectuion<S extends Securitycurrency<S>, U extends SecuritycurrencyPositionSummary<S>> {

  @Autowired
  PlatformTransactionManager platformTransactionManager;

  @Transactional
  @Modifying
  @Async
  public void asyncLoadHistoryIntraData(final SecuritycurrencyService<S, U> securitycurrencyService,
      final S securitycurrency, final boolean withDeletion, final short maxIntraRetry,
      final int scIntradayUpdateTimeout) {
    final TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {

      @Override
      protected void doInTransactionWithoutResult(final TransactionStatus transactionStatus) {
        try {
          S sc = securitycurrency;

          if (sc.getIdSecuritycurrency() != null && withDeletion) {
            securitycurrencyService.historyquoteJpaRepository.removeAllSecurityHistoryquote(sc.getIdSecuritycurrency());
            sc = securitycurrencyService.getJpaRepository().getById(sc.getIdSecuritycurrency());
          }
          sc = securitycurrencyService.updateLastPriceSecurityCurrency(sc, maxIntraRetry, scIntradayUpdateTimeout);
          securitycurrencyService.createWithHistoryQuote(sc);
          securitycurrencyService.afterFullLoad(sc);

        } catch (final Exception e) {
          e.printStackTrace();
          transactionStatus.setRollbackOnly();
        }

      }
    });
  }

}
