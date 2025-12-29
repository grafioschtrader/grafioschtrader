package grafioschtrader.task.exec;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.TaskDataChange;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Background task that resets retry counters (retry_history_load and retry_intra_load) for connector(s) on active
 * instruments. This task is used to recover from situations where connectors have exceeded their retry limits due to
 * temporary issues like network outages.
 *
 * <p>
 * The task can reset counters for:
 * </p>
 * <ul>
 * <li><strong>Single connector:</strong> When idEntity is set to a connector's idNumber (shortId.hashCode())</li>
 * <li><strong>All connectors:</strong> When idEntity is null</li>
 * </ul>
 *
 * <p>
 * The earliestStartTime field is used as the reference date to determine which securities are considered active
 * (activeToDate >= earliestStartTime). Currency pairs are always considered active and are reset regardless of date.
 * </p>
 *
 * @see IFeedConnector#getIdNumber()
 * @see ConnectorHelper#getConnectorByIdNumber(List, int)
 */
@Component
public class ResetConnectorRetryCountersTask implements ITask {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private List<IFeedConnector> feedConnectors;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.RESET_CONNECTOR_RETRY_COUNTERS;
  }

  @Override
  public List<String> getAllowedEntities() {
    // Empty string allows null/no entity (all connectors)
    // "IFeedConnector" allows specifying a connector by idNumber
    return Arrays.asList("", IFeedConnector.class.getSimpleName());
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) {
    // Get connector ID from idEntity (hashCode -> full ID string)
    Integer connectorIdNumber = taskDataChange.getIdEntity();
    String connectorId = null;
    if (connectorIdNumber != null) {
      IFeedConnector connector = ConnectorHelper.getConnectorByIdNumber(feedConnectors, connectorIdNumber);
      if (connector != null) {
        connectorId = connector.getID(); // e.g., "gt.datafeed.yahoo"
      }
    }

    // Convert earliestStartTime to Date for active check
    Date checkDate = Date.from(taskDataChange.getEarliestStartTime().atZone(ZoneId.systemDefault()).toInstant());

    // Reset counters (only retry_history_load and retry_intra_load)
    // Currency pairs first (no date filter - always active)
    currencypairJpaRepository.resetRetryCountersByConnector(connectorId);
    // Securities with date filter (activeToDate >= checkDate)
    securityJpaRepository.resetRetryCountersByConnector(connectorId, checkDate);
  }

}
