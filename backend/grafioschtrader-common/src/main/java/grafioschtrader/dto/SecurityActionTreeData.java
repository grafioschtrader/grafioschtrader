package grafioschtrader.dto;

import java.util.List;
import java.util.Map;

import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.SecurityActionApplication;
import grafioschtrader.entities.SecurityTransfer;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO returned by the /api/securityaction/tree endpoint. Contains all data needed to render the SecurityAction
 * TreeTable: system-wide ISIN change actions, the current tenant's application status, and the tenant's own security
 * transfers.
 */
@Schema(description = "Combined tree data for security actions and transfers displayed in the SecurityAction TreeTable.")
public class SecurityActionTreeData {

  @Schema(description = "All system-wide ISIN change actions ordered by date descending")
  public List<SecurityAction> systemActions;

  @Schema(description = "Map of security action ID to the current tenant's application, for actions the tenant has applied")
  public Map<Integer, SecurityActionApplication> appliedByCurrentTenant;

  @Schema(description = "The current tenant's security transfers ordered by date descending")
  public List<SecurityTransfer> clientTransfers;

  public SecurityActionTreeData() {
  }

  public SecurityActionTreeData(List<SecurityAction> systemActions,
      Map<Integer, SecurityActionApplication> appliedByCurrentTenant, List<SecurityTransfer> clientTransfers) {
    this.systemActions = systemActions;
    this.appliedByCurrentTenant = appliedByCurrentTenant;
    this.clientTransfers = clientTransfers;
  }
}
