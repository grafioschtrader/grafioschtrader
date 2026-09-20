package grafioschtrader.algo;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.common.DynamicFormField;
import grafiosch.dynamic.model.DynamicFormPropertyHelps;
import grafioschtrader.types.SimulationInitializationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a simulation tenant from an AlgoTop strategy.
 */
@Schema(description = "Request body for creating a simulation tenant from an AlgoTop strategy.")
public class SimulationTenantCreateDTO {

  @Schema(description = "ID of the AlgoTop strategy to share with the simulation tenant.")
  @NotNull
  private Integer idAlgoTop;

  @Schema(description = "User-defined name for the simulation tenant.")
  @NotBlank
  @Size(max = 40)
  @DynamicFormField(uiOrder = "1.1", labelKey = "NAME")
  private String tenantName;

  @Schema(description = """
      Defines whether source transactions, manual cash or liquidated historical cash establish the opening state.
      Manual cash is not permitted when the strategy has a portfolio reference date.""")
  @NotNull
  @DynamicFormField(uiOrder = "1.3", helps = { DynamicFormPropertyHelps.SELECT_OPTIONS })
  private SimulationInitializationMode initializationMode;

  @Schema(description = """
      Completed end-of-day date whose closing state the environment opens with. It must be yesterday or earlier, and
      for the two modes that read the source portfolio also on or after the source tenant's first transaction.
      A strategy with a portfolio reference date must open exactly one calendar day after that reference date.""")
  @NotNull
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @DynamicFormField(uiOrder = "1.2")
  private LocalDate simulationStartDate;

  @Schema(description = "Unresolved source position keys mapped to destination source cash account IDs")
  private Map<String, Integer> liquidationAssignments;

  @Schema(description = """
      Initial cash balances per cash account (key = idSecuritycashAccount, value = balance amount).
      Used only in MANUAL_CASH mode to create opening deposits.""")
  private Map<Integer, Double> cashBalances;

  public Integer getIdAlgoTop() {
    return idAlgoTop;
  }

  public void setIdAlgoTop(Integer idAlgoTop) {
    this.idAlgoTop = idAlgoTop;
  }

  public String getTenantName() {
    return tenantName;
  }

  public void setTenantName(String tenantName) {
    this.tenantName = tenantName;
  }

  public SimulationInitializationMode getInitializationMode() {
    return initializationMode;
  }

  public void setInitializationMode(SimulationInitializationMode initializationMode) {
    this.initializationMode = initializationMode;
  }

  public LocalDate getSimulationStartDate() {
    return simulationStartDate;
  }

  public void setSimulationStartDate(LocalDate date) {
    simulationStartDate = date;
  }

  public Map<String, Integer> getLiquidationAssignments() {
    return liquidationAssignments;
  }

  public void setLiquidationAssignments(Map<String, Integer> assignments) {
    liquidationAssignments = assignments;
  }

  public Map<Integer, Double> getCashBalances() {
    return cashBalances;
  }

  public void setCashBalances(Map<Integer, Double> cashBalances) {
    this.cashBalances = cashBalances;
  }
}
