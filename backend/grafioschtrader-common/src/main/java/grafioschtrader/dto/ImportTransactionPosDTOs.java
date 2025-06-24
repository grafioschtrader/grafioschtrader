package grafioschtrader.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class ImportTransactionPosDTOs {

  @Schema(description = "Request object for setting duplicate transaction handling on import positions")
  public static class SetIdTransactionMayBe {
    @Schema(description = "Transaction duplicate flag - must be 0 to mark as confirmed non-duplicate, or null to reset")
    @Min(value = 0)
    @Max(value = 0)
    public Integer idTransactionMayBe;
    @Schema(description = "List of import transaction position IDs to update duplicate handling for")
    public List<Integer> idTransactionPosList;
  }
  
  @Schema(description = "Request object for assigning a security to multiple import transaction positions")
  public static class SetSecurityImport {
    @Schema(description = "Unique identifier of the security to assign to the import positions")
    public Integer idSecuritycurrency;
    
    @Schema(description = "List of import transaction position IDs to update with the specified security")
    public List<Integer> idTransactionPosList;
  }

  @Schema(description = "Request object for assigning a cash account to multiple import transaction positions")
  public static class SetCashAccountImport {
    @Schema(description = "Unique identifier of the cash account to assign to the import positions")
    @NotNull
    public Integer idSecuritycashAccount;
   
    @Schema(description = "List of import transaction position IDs to update with the specified cash account")
    @NotNull
    public List<Integer> idTransactionPosList;
  }

}
