package grafioschtrader.task.exec;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(MonitorPriceData.class);

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
      IFeedConnector ifeedConnector = ConnectorHelper
          .getConnectorByConnectorId(securityJpaRepository.getFeedConnectors(), mfc.getConnector(), feedSupport);
      String name;
      if (ifeedConnector != null) {
        name = ifeedConnector.getReadableName();
      } else {
        name = mfc.getConnector() == null ? "[unknown connector]" : "[unregistered] " + mfc.getConnector();
        log.warn("Failed-connector monitor: no registered IFeedConnector for id='{}' feedSupport={}; "
            + "row likely from a stale securitycurrency.{} reference",
            mfc.getConnector(), feedSupport,
            feedSupport == FeedSupport.FS_HISTORY ? "id_connector_history" : "id_connector_intra");
      }
      messageArg += String.format("%-30s %6d %6d %3d" + BaseConstants.RETURN_AND_NEW_LINE,
          name, mfc.getTotal(), mfc.getFailed(), mfc.getPercentageFailed());
    }
    applicationEventPublisher.publishEvent(new AlertEvent(this, alertType, messageArg));
  }
}
