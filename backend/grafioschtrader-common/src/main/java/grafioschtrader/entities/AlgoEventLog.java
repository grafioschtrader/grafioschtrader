package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.entities.TenantBaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Action/event tracking for simulation and alarm audit trail. Logs entry signals, exits, stop-loss triggers, rebalance
 * actions, and other events during simulation or live alarm evaluation. Each row captures a single event with its type,
 * date, optional security context, and structured JSON details.
 */
@Schema(description = """
    Event log entry for simulation and alarm audit trail. Captures entry signals, exits, stop-loss triggers,
    and other events with structured JSON details.""")
@Entity
@Table(name = AlgoEventLog.TABNAME)
public class AlgoEventLog extends TenantBaseID implements Serializable {

  public static final String TABNAME = "algo_event_log";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_algo_event")
  private Integer idAlgoEvent;

  @Basic(optional = false)
  @Column(name = "id_algo_top")
  private Integer idAlgoTop;

  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  @Basic(optional = false)
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Schema(description = """
      Event type identifier, e.g. ENTRY_SIGNAL, EXIT_SIGNAL, STOP_LOSS_TRIGGERED, REBALANCE_EXECUTED,
      ALARM_FIRED, SIMULATION_START, SIMULATION_END.""")
  @Basic(optional = false)
  @NotNull
  @Size(max = 50)
  @Column(name = "event_type")
  private String eventType;

  @Schema(description = "Business date on which the event occurred (simulation date or real calendar date)")
  @Basic(optional = false)
  @NotNull
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "event_date")
  private LocalDate eventDate;

  @Schema(description = """
      Structured JSON with event-specific data, e.g.
      {"price": 102.5, "qty": 10, "reason": "MA crossing triggered"} for an entry signal.""")
  @Column(name = "details", columnDefinition = "JSON")
  private String details;

  @Schema(description = "Auto-maintained row creation timestamp")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @JsonIgnore
  @Override
  public Integer getId() {
    return idAlgoEvent;
  }

  public Integer getIdAlgoEvent() {
    return idAlgoEvent;
  }

  public void setIdAlgoEvent(Integer idAlgoEvent) {
    this.idAlgoEvent = idAlgoEvent;
  }

  public Integer getIdAlgoTop() {
    return idAlgoTop;
  }

  public void setIdAlgoTop(Integer idAlgoTop) {
    this.idAlgoTop = idAlgoTop;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public LocalDate getEventDate() {
    return eventDate;
  }

  public void setEventDate(LocalDate eventDate) {
    this.eventDate = eventDate;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
