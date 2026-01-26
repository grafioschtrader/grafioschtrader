package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.entities.BaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Logs the history of stock exchange price updates performed by the QuoteBackgroundUpdateWorker.
 * Each record represents one update attempt for a specific stock exchange, tracking when the update
 * was performed, how many securities were involved, and whether it succeeded or failed.
 * 
 * SELECT s.name, hl.* FROM historyquote_update_log hl JOIN stockexchange s ON hl.id_stockexchange = s.id_stockexchange ORDER BY hl.update_timestamp DESC;
 */
@Schema(description = """
    Logs historical price update operations per stock exchange. Tracks when each exchange was queried for
    price data, how many securities were updated, and the status of each update attempt. Used for monitoring
    and debugging the exchange-specific historical price update mechanism.""")
@Entity
@Table(name = HistoryquoteUpdateLog.TABNAME)
public class HistoryquoteUpdateLog extends BaseID<Integer> implements Serializable {

  public static final String TABNAME = "historyquote_update_log";

  private static final long serialVersionUID = 1L;

  public static final byte STATUS_STARTED = 0;
  public static final byte STATUS_SUCCESS = 1;
  public static final byte STATUS_PARTIAL = 2;
  public static final byte STATUS_FAILED = 3;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_hq_update_log")
  private Integer idHqUpdateLog;

  @Schema(description = "ID of the stock exchange that was updated")
  @Column(name = "id_stockexchange")
  private Integer idStockexchange;

  @Schema(description = "Timestamp when the update was performed")
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME)
  @Column(name = "update_timestamp")
  private LocalDateTime updateTimestamp;

  @Schema(description = "Number of minutes since the exchange closed when the update was triggered")
  @Column(name = "minutes_since_close")
  private Integer minutesSinceClose;

  @Schema(description = "Total number of securities eligible for update from this exchange")
  @Column(name = "securities_count")
  private Integer securitiesCount;

  @Schema(description = "Number of securities that were successfully updated")
  @Column(name = "securities_updated")
  private Integer securitiesUpdated;

  @Schema(description = "Update status: 0=started, 1=success, 2=partial, 3=failed")
  @Column(name = "update_status")
  private byte updateStatus;

  @Schema(description = "Error message if the update failed, null otherwise")
  @Column(name = "error_message")
  private String errorMessage;

  public HistoryquoteUpdateLog() {
  }

  public HistoryquoteUpdateLog(Integer idStockexchange, Integer minutesSinceClose, Integer securitiesCount) {
    this.idStockexchange = idStockexchange;
    this.updateTimestamp = LocalDateTime.now();
    this.minutesSinceClose = minutesSinceClose;
    this.securitiesCount = securitiesCount;
    this.securitiesUpdated = 0;
    this.updateStatus = STATUS_STARTED;
  }

  public Integer getIdHqUpdateLog() {
    return idHqUpdateLog;
  }

  public void setIdHqUpdateLog(Integer idHqUpdateLog) {
    this.idHqUpdateLog = idHqUpdateLog;
  }

  public Integer getIdStockexchange() {
    return idStockexchange;
  }

  public void setIdStockexchange(Integer idStockexchange) {
    this.idStockexchange = idStockexchange;
  }

  public LocalDateTime getUpdateTimestamp() {
    return updateTimestamp;
  }

  public void setUpdateTimestamp(LocalDateTime updateTimestamp) {
    this.updateTimestamp = updateTimestamp;
  }

  public Integer getMinutesSinceClose() {
    return minutesSinceClose;
  }

  public void setMinutesSinceClose(Integer minutesSinceClose) {
    this.minutesSinceClose = minutesSinceClose;
  }

  public Integer getSecuritiesCount() {
    return securitiesCount;
  }

  public void setSecuritiesCount(Integer securitiesCount) {
    this.securitiesCount = securitiesCount;
  }

  public Integer getSecuritiesUpdated() {
    return securitiesUpdated;
  }

  public void setSecuritiesUpdated(Integer securitiesUpdated) {
    this.securitiesUpdated = securitiesUpdated;
  }

  public byte getUpdateStatus() {
    return updateStatus;
  }

  public void setUpdateStatus(byte updateStatus) {
    this.updateStatus = updateStatus;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void markSuccess(int securitiesUpdated) {
    this.securitiesUpdated = securitiesUpdated;
    this.updateStatus = STATUS_SUCCESS;
  }

  public void markPartial(int securitiesUpdated, String errorMessage) {
    this.securitiesUpdated = securitiesUpdated;
    this.updateStatus = STATUS_PARTIAL;
    this.errorMessage = errorMessage;
  }

  public void markFailed(String errorMessage) {
    this.updateStatus = STATUS_FAILED;
    this.errorMessage = errorMessage;
  }

  @Override
  public Integer getId() {
    return this.getIdHqUpdateLog();
  }
}
