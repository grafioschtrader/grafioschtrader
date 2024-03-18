package grafioschtrader.task.exec;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import grafioschtrader.GlobalConstants;
import grafioschtrader.alert.AlertEvent;
import grafioschtrader.alert.AlertType;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository.MonitorFailedConnector;
import grafioschtrader.repository.TaskDataChangeJpaRepository;

abstract class MonitorPriceData {

  @Autowired
  protected SecurityJpaRepository securityJpaRepository;

  @Autowired
  protected GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  protected TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  protected ApplicationEventPublisher applicationEventPublisher;

  protected void generateMessageAndPublishAlert(AlertType alertType,
      List<MonitorFailedConnector> monitorFaliedConnectors, FeedSupport feedSupport) {
    String messageArg = String.format(
        GlobalConstants.RETURN_AND_NEW_LINE + "%-30s %6s %6s %3s" + GlobalConstants.RETURN_AND_NEW_LINE, "Connector",
        "Total", "Error", "%");
    for (MonitorFailedConnector mfc : monitorFaliedConnectors) {
      IFeedConnector ifeedConnector = ConnectorHelper.getConnectorByConnectorId(
          securityJpaRepository.getFeedConnectors(), mfc.getConnector(), feedSupport);
      messageArg += String.format("%-30s %6d %6d %3d" + GlobalConstants.RETURN_AND_NEW_LINE,
          ifeedConnector.getReadableName(), mfc.getTotal(), mfc.getFailed(), mfc.getPercentageFailed());
    }
    applicationEventPublisher.publishEvent(new AlertEvent(this, alertType, messageArg));
  }
}
