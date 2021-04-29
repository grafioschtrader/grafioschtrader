package grafioschtrader.task.exec;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.TaskType;

/**
 * Normally called when the dividend data connector is changed or thru dividend
 * calendar calendar a possible update was detected.
 *
 */
@Component
public class UpdateDividendForSecurityTask extends UpdateDividendSplitForSecurity<Dividend> implements ITask {

  @Autowired
  private DividendJpaRepository dividendJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Override
  public TaskType getTaskType() {
    return TaskType.SECURTY_DIVIDEND_UPDATE_FOR_SECURITY;
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) {
    Security security = securityJpaRepository.getOne(taskDataChange.getIdEntity());
    if (security.getIdConnectorDividend() != null) {
      List<String> errorMessages = loadDividendData(security);
      if (!errorMessages.isEmpty()) {
        throw new TaskBackgroundException("gt.dividend.connector.failure", errorMessages);
      }
    } else {
      throw new TaskBackgroundException("gt.dividend.connector.notfound");
    }
  }

  private List<String> loadDividendData(Security security) {
    List<String> errorMessages = new ArrayList<>();
    short retryDividendLoad = security.getRetryDividendLoad();
    try {
      IFeedConnector connector = ConnectorHelper.getConnectorByConnectorId(feedConnectors,
          security.getIdConnectorDividend(), IFeedConnector.FeedSupport.DIVIDEND);
      List<Dividend> dividendsRead = connector.getDividendHistory(security,
          LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY));
      updateDividendData(security, dividendsRead);
      retryDividendLoad = 0;
    } catch (ParseException pe) {
      log.error(pe.getMessage() + "Offset: " + pe.getErrorOffset(), pe);
      errorMessages.add(pe.getMessage());
    } catch (final Exception ex) {
      retryDividendLoad++;
      log.error(ex.getMessage() + " " + security, ex);
      errorMessages.add(ex.getMessage());
    }
    security.setRetryDividendLoad(retryDividendLoad);
    securityJpaRepository.save(security);
    return errorMessages;
  }

  private void updateDividendData(Security security, List<Dividend> dividendsRead) {
    dividendJpaRepository.deleteByIdSecuritycurrencyAndCreateType(security.getIdSecuritycurrency(),
        CreateType.CONNECTOR_CREATED.getValue());
    List<Dividend> existingDividends = dividendJpaRepository
        .findByIdSecuritycurrencyOrderByExDateAsc(security.getIdSecuritycurrency());
    updateDividendSplitData(security, dividendsRead, existingDividends, this.dividendJpaRepository);

  }

}
