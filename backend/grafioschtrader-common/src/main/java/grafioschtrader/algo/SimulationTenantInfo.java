package grafioschtrader.algo;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.SimulationInitializationMode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for listing simulation tenants associated with an AlgoTop strategy.
 */
@Schema(description = "Information about a simulation tenant linked to an AlgoTop strategy.")
public class SimulationTenantInfo {

  private Integer idTenant;
  private String tenantName;
  private Integer idAlgoTop;
  private String algoTopName;
  private boolean hasTransactions;
  @Schema(description = "Immutable end-of-day date whose closing state the environment opens with")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  private LocalDate simulationStartDate;

  @Schema(description = "How the opening ledger was established")
  private SimulationInitializationMode initializationMode;

  @Schema(description = """
      True while a replay of this environment is queued or executing. It can then neither be entered nor deleted, so
      the client offers neither.""")
  private boolean active;

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public LocalDate getSimulationStartDate() {
    return simulationStartDate;
  }

  public void setSimulationStartDate(LocalDate date) {
    simulationStartDate = date;
  }

  public SimulationInitializationMode getInitializationMode() {
    return initializationMode;
  }

  public void setInitializationMode(SimulationInitializationMode mode) {
    initializationMode = mode;
  }

  @Schema(description = """
      True for an environment created before the opening definition existed. It has neither a date nor a mode, so its
      opening state cannot be reproduced and a historical replay needs a newly created environment.""")
  public boolean isRequiresRecreation() {
    return simulationStartDate == null || initializationMode == null;
  }

  public SimulationTenantInfo() {
  }

  public SimulationTenantInfo(Integer idTenant, String tenantName, Integer idAlgoTop, String algoTopName,
      boolean hasTransactions) {
    this.idTenant = idTenant;
    this.tenantName = tenantName;
    this.idAlgoTop = idAlgoTop;
    this.algoTopName = algoTopName;
    this.hasTransactions = hasTransactions;
  }

  public Integer getIdTenant() {
    return idTenant;
  }

  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public String getTenantName() {
    return tenantName;
  }

  public void setTenantName(String tenantName) {
    this.tenantName = tenantName;
  }

  public Integer getIdAlgoTop() {
    return idAlgoTop;
  }

  public void setIdAlgoTop(Integer idAlgoTop) {
    this.idAlgoTop = idAlgoTop;
  }

  public String getAlgoTopName() {
    return algoTopName;
  }

  public void setAlgoTopName(String algoTopName) {
    this.algoTopName = algoTopName;
  }

  public boolean isHasTransactions() {
    return hasTransactions;
  }

  public void setHasTransactions(boolean hasTransactions) {
    this.hasTransactions = hasTransactions;
  }
}
