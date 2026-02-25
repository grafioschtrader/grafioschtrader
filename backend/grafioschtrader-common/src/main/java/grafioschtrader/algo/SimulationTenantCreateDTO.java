package grafioschtrader.algo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for creating a simulation tenant from an AlgoTop strategy.
 */
@Schema(description = "Request body for creating a simulation tenant from an AlgoTop strategy.")
public class SimulationTenantCreateDTO {

  @Schema(description = "ID of the AlgoTop strategy to share with the simulation tenant.")
  @NotNull
  private Integer idAlgoTop;

  @Schema(description = "User-defined name for the simulation tenant.")
  @NotNull
  private String tenantName;

  @Schema(description = """
      Whether to copy transactions up to the AlgoTop's reference date.
      Only valid when the AlgoTop has a referenceDate (UC6 origin).""")
  private boolean copyTransactions;

  @Schema(description = """
      Initial cash balances per cash account (key = idSecuritycashAccount, value = balance amount).
      Used when copyTransactions is false to create deposit transactions.""")
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

  public boolean isCopyTransactions() {
    return copyTransactions;
  }

  public void setCopyTransactions(boolean copyTransactions) {
    this.copyTransactions = copyTransactions;
  }

  public Map<Integer, Double> getCashBalances() {
    return cashBalances;
  }

  public void setCashBalances(Map<Integer, Double> cashBalances) {
    this.cashBalances = cashBalances;
  }
}
