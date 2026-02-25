package grafioschtrader.algo;

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
