package grafioschtrader.task.exec.unofficial;

import java.time.Year;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.dto.CopyTradingDaysFromSourceToTarget;
import grafioschtrader.repository.TradingDaysMinusJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;


@Component
public class CreateTradingDaysMinusByIndexTask implements ITask {

  private final Logger log = LoggerFactory.getLogger(this.getClass());
  @PersistenceContext
  private EntityManager em;

  @Autowired
  TradingDaysMinusJpaRepository tradingDaysMinusJpaRepository;

 

  @Override
  public TaskType getTaskType() {
    return TaskType.UNOFFICIAL_CREATE_STOCK_EXCHANGE_CALENDAR;
  }

  @Override
  @Transactional
  public void doWork(Integer idEntity, String entity) {
    int[][] seSMapping = {
        { 236, 3692 }, // BER
        { 222, 3572 }, // BME
        { 239, 3757 }, // BMFBOVES
        { 234, 3647 }, // OMXC
        { 221, 3557 }, // Euronext
        { 220, 3495 }, // FSX
        { 228, 3559 }, // HKEX
        { 224, 3622 }, // LSE
        { 223, 3562 }, // MTA
        { 233, 3498 }, // NASDAQ
        { 218, 3532 }, // NYSE
        { 232, 3689 }, // OSE
        { 231, 3690 }, // OMX
        { 227, 3563 }, // JPX
        { 226, 3564 }, // VSE
        { 217, 3499 }, // SIX
        { 238, 3724 }  // XAMS 
    };

    String sqlDelete = "DELETE FROM trading_days_minus WHERE id_stockexchange = :id_se";
    String sqlCreate = "INSERT INTO trading_days_minus (id_stockexchange, trading_date_minus) "
        + "SELECT :id_se, tsp.trading_date AS trandingDate FROM trading_days_plus tsp LEFT JOIN "
        + "(SELECT DISTINCT hq.date AS datum, se.name AS name "
        + "FROM stockexchange se JOIN security s on se.id_stockexchange = s.id_stockexchange "
        + "JOIN assetclass ac ON s.id_asset_class = ac.id_asset_class "
        + "JOIN historyquote hq ON hq.id_securitycurrency = s.id_securitycurrency "
        + "WHERE se.id_stockexchange = :id_se AND s.id_securitycurrency = :id_s) AS a ON tsp.trading_date = a.datum "
        + "WHERE a.datum IS NULL AND tsp.trading_date < CURDATE() ORDER BY name, trandingDate";

    Query qSqlDelete = em.createNativeQuery(sqlDelete);
    Query qSqlCreate = em.createNativeQuery(sqlCreate);
    for (int i = 0; i < seSMapping.length; i++) {
      qSqlDelete.setParameter("id_se", seSMapping[i][0]);
      qSqlDelete.executeUpdate();
      qSqlCreate.setParameter("id_se", seSMapping[i][0]).setParameter("id_s", seSMapping[i][1]);
      qSqlCreate.executeUpdate();
      log.info("Done: Stockexchange-ID: {}", seSMapping[i][0]);
    }
    // Copy SIX to Primary exchange Switzerland
    for(int i = 2000; i < Year.now().getValue(); i++ ) {
      CopyTradingDaysFromSourceToTarget copyTradingDaysFromSourceToTarget = new CopyTradingDaysFromSourceToTarget(217,
        225, i, true);
      tradingDaysMinusJpaRepository.copyTradingDaysMinusToOtherStockexchange(copyTradingDaysFromSourceToTarget, true);
    }

    
  }
  
}
