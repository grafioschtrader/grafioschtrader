package grafioschtrader.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetExchangeLogPeriodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = GTNetExchangeLog.TABNAME)
@Schema(description = """
    Statistics log for GTNet data exchanges. Records entity counts for both supplier and consumer roles,
    supporting configurable aggregation periods (individual, daily, weekly, monthly, yearly).""")
public class GTNetExchangeLog {
  public static final String TABNAME = "gt_net_exchange_log";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net_exchange_log")
  private Integer idGtNetExchangeLog;

  @Schema(description = "Reference to the remote GTNet domain")
  @Column(name = "id_gt_net")
  @NotNull
  private Integer idGtNet;

  @Schema(description = "Type of data exchanged: LAST_PRICE (0) or HISTORICAL_PRICES (1)")
  @Column(name = "entity_kind")
  @NotNull
  private byte entityKind;

  @Schema(description = "Role in the exchange: true = Supplier (receiver), false = Consumer (requester)")
  @Column(name = "log_as_supplier")
  @NotNull
  private boolean logAsSupplier;

  @Schema(description = "Aggregation period type: INDIVIDUAL (0), DAILY (1), WEEKLY (2), MONTHLY (3), YEARLY (4)")
  @Column(name = "period_type")
  @NotNull
  private byte periodType;

  @Schema(description = "Start date of the aggregation period. For INDIVIDUAL, equals the date of timestamp.")
  @Column(name = "period_start")
  @NotNull
  private LocalDate periodStart;

  @Schema(description = "Timestamp of the log entry. For aggregates, this is the last update time.")
  @Column(name = "timestamp")
  @NotNull
  private LocalDateTime timestamp;

  @Schema(description = "Number of entities sent (Consumer) or received (Supplier)")
  @Column(name = "entities_sent")
  @NotNull
  private int entitiesSent;

  @Schema(description = "Number of entities successfully updated from response (Consumer) or with newer data sent back (Supplier)")
  @Column(name = "entities_updated")
  @NotNull
  private int entitiesUpdated;

  @Schema(description = "Number of entities in response")
  @Column(name = "entities_in_response")
  @NotNull
  private int entitiesInResponse;

  @Schema(description = "Number of requests aggregated into this entry. 1 for INDIVIDUAL records.")
  @Column(name = "request_count")
  @NotNull
  private int requestCount = 1;

  public GTNetExchangeLog() {
  }

  public GTNetExchangeLog(Integer idGtNet, GTNetExchangeKindType entityKind, boolean logAsSupplier,
      int entitiesSent, int entitiesUpdated, int entitiesInResponse) {
    this.idGtNet = idGtNet;
    this.entityKind = entityKind.getValue();
    this.logAsSupplier = logAsSupplier;
    this.periodType = GTNetExchangeLogPeriodType.INDIVIDUAL.getValue();
    this.periodStart = LocalDate.now();
    this.timestamp = LocalDateTime.now();
    this.entitiesSent = entitiesSent;
    this.entitiesUpdated = entitiesUpdated;
    this.entitiesInResponse = entitiesInResponse;
    this.requestCount = 1;
  }

  public Integer getIdGtNetExchangeLog() {
    return idGtNetExchangeLog;
  }

  public void setIdGtNetExchangeLog(Integer idGtNetExchangeLog) {
    this.idGtNetExchangeLog = idGtNetExchangeLog;
  }

  public Integer getIdGtNet() {
    return idGtNet;
  }

  public void setIdGtNet(Integer idGtNet) {
    this.idGtNet = idGtNet;
  }

  public GTNetExchangeKindType getEntityKind() {
    return GTNetExchangeKindType.getGTNetExchangeKindType(entityKind);
  }

  public void setEntityKind(GTNetExchangeKindType entityKind) {
    this.entityKind = entityKind.getValue();
  }

  public byte getEntityKindValue() {
    return entityKind;
  }

  public boolean isLogAsSupplier() {
    return logAsSupplier;
  }

  public void setLogAsSupplier(boolean logAsSupplier) {
    this.logAsSupplier = logAsSupplier;
  }

  public GTNetExchangeLogPeriodType getPeriodType() {
    return GTNetExchangeLogPeriodType.getGTNetExchangeLogPeriodType(periodType);
  }

  public void setPeriodType(GTNetExchangeLogPeriodType periodType) {
    this.periodType = periodType.getValue();
  }

  public byte getPeriodTypeValue() {
    return periodType;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public void setPeriodStart(LocalDate periodStart) {
    this.periodStart = periodStart;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public int getEntitiesSent() {
    return entitiesSent;
  }

  public void setEntitiesSent(int entitiesSent) {
    this.entitiesSent = entitiesSent;
  }

  public int getEntitiesUpdated() {
    return entitiesUpdated;
  }

  public void setEntitiesUpdated(int entitiesUpdated) {
    this.entitiesUpdated = entitiesUpdated;
  }

  public int getEntitiesInResponse() {
    return entitiesInResponse;
  }

  public void setEntitiesInResponse(int entitiesInResponse) {
    this.entitiesInResponse = entitiesInResponse;
  }

  public int getRequestCount() {
    return requestCount;
  }

  public void setRequestCount(int requestCount) {
    this.requestCount = requestCount;
  }

  /**
   * Aggregates another log entry into this one by summing the counters.
   */
  public void aggregate(GTNetExchangeLog other) {
    this.entitiesSent += other.entitiesSent;
    this.entitiesUpdated += other.entitiesUpdated;
    this.entitiesInResponse += other.entitiesInResponse;
    this.requestCount += other.requestCount;
    if (other.timestamp.isAfter(this.timestamp)) {
      this.timestamp = other.timestamp;
    }
  }
}
