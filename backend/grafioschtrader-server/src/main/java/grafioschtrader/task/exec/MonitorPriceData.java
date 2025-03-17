package grafioschtrader.task.exec;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import grafiosch.BaseConstants;
import grafiosch.alert.AlertEvent;
import grafiosch.alert.IAlertType;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository.MonitorFailedConnector;
import grafioschtrader.service.GlobalparametersService;

abstract class MonitorPriceData {

  @Autowired
  protected SecurityJpaRepository securityJpaRepository;

  @Autowired
  protected GlobalparametersService globalparametersService;

  @Autowired
  protected TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  protected ApplicationEventPublisher applicationEventPublisher;

  protected void generateMessageAndPublishAlert(IAlertType alertType,
      List<MonitorFailedConnector> monitorFaliedConnectors, FeedSupport feedSupport) {
    String messageArg = String.format(
        BaseConstants.RETURN_AND_NEW_LINE + "%-30s %6s %6s %3s" + BaseConstants.RETURN_AND_NEW_LINE, "Connector",
        "Total", "Error", "%");
    for (MonitorFailedConnector mfc : monitorFaliedConnectors) {
      IFeedConnector ifeedConnector = ConnectorHelper.getConnectorByConnectorId(
          securityJpaRepository.getFeedConnectors(), mfc.getConnector(), feedSupport);
      messageArg += String.format("%-30s %6d %6d %3d" + BaseConstants.RETURN_AND_NEW_LINE,
          ifeedConnector.getReadableName(), mfc.getTotal(), mfc.getFailed(), mfc.getPercentageFailed());
    }
    applicationEventPublisher.publishEvent(new AlertEvent(this, alertType, messageArg));
  }
}
