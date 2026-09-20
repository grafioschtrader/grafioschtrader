package grafioschtrader.algo;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Account-oriented opening state; proceeds are recomputed when creating the environment. */
@Schema(description = "Simulation opening balances and problems to resolve before creation")
public class SimulationPreviewDto {
  public List<AccountBalance> accounts = new ArrayList<>();
  public List<UnresolvedPosition> unresolvedPositions = new ArrayList<>();
  public List<String> errors = new ArrayList<>();

  @Schema(description = "Opening balance in the source cash account currency")
  public record AccountBalance(Integer idCashaccount, String name, String currency, double balance) {
  }

  @Schema(description = "Position requiring an explicit destination cash account")
  public record UnresolvedPosition(String positionKey, String securityName, String securityaccountName, String currency,
      double units, double proceeds) {
  }
}
