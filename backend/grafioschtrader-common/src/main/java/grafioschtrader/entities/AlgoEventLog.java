package grafioschtrader.entities;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.entities.TenantBaseID;
import grafioschtrader.types.AlgoEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Schema(description = """
    One entry of the audit trail of a historical replay. The trail explains a run rather than summarising it: entries,
    exits, refused actions and days on which no decision was possible. The rationale is a bare message key such as
    MEAN_REVERSION_ENTRY or REBALANCE_BELOW_TARGET, which the client resolves in the reader's language.""")
@Entity
@Table(name = AlgoEventLog.TABNAME)
public class AlgoEventLog extends TenantBaseID {

  public static final String TABNAME = "algo_event_log";

  @Schema(description = "Auto-generated primary key")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_algo_event")
  private Integer idAlgoEvent;

  @Schema(description = "The run this entry belongs to")
  @Column(name = "id_simulation_result")
  private Integer idSimulationResult;

  @Schema(description = "Simulation tenant that owns this replay event")
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Schema(description = """
      Strategy that produced the entry. Empty for the run markers and for a rebalancing fill, which the ledger cannot
      carry a strategy assignment for.""")
  @Column(name = "id_algo_strategy")
  private Integer idAlgoStrategy;

  @Schema(description = "Instrument involved in the event; empty for run-wide events")
  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  @Schema(description = "Closing day the decision was taken on, not the day the row was written")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "event_date")
  private LocalDate eventDate;

  @Schema(description = "Kind of replay event")
  @Enumerated(EnumType.STRING)
  @Column(name = "event_type")
  private AlgoEventType eventType;

  @Schema(description = "Bare message key such as MEAN_REVERSION_ENTRY or REBALANCE_BELOW_TARGET; the client resolves it")
  @Column(name = "rationale")
  private String rationale;

  @Schema(description = "Units involved in the decision or fill; empty for events without a quantity")
  @Column(name = "units")
  private Double units;

  @Schema(description = "Closing price in the quotation units of the instrument")
  @Column(name = "price")
  private Double price;

  @Schema(description = "Amount of the decision or fill in tenant currency")
  @Column(name = "amount")
  private Double amount;

  @Schema(description = "Currency of the event amount")
  @Column(name = "currency")
  private String currency;

  @Schema(description = "Free text that carries what no other column can, such as the message of a failure")
  @Column(name = "details")
  private String details;

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

  public Integer getIdSimulationResult() {
    return idSimulationResult;
  }

  public void setIdSimulationResult(Integer idSimulationResult) {
    this.idSimulationResult = idSimulationResult;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public Integer getIdAlgoStrategy() {
    return idAlgoStrategy;
  }

  public void setIdAlgoStrategy(Integer idAlgoStrategy) {
    this.idAlgoStrategy = idAlgoStrategy;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public LocalDate getEventDate() {
    return eventDate;
  }

  public void setEventDate(LocalDate eventDate) {
    this.eventDate = eventDate;
  }

  public AlgoEventType getEventType() {
    return eventType;
  }

  public void setEventType(AlgoEventType eventType) {
    this.eventType = eventType;
  }

  public String getRationale() {
    return rationale;
  }

  public void setRationale(String rationale) {
    this.rationale = rationale;
  }

  public Double getUnits() {
    return units;
  }

  public void setUnits(Double units) {
    this.units = units;
  }

  public Double getPrice() {
    return price;
  }

  public void setPrice(Double price) {
    this.price = price;
  }

  public Double getAmount() {
    return amount;
  }

  public void setAmount(Double amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }
}
