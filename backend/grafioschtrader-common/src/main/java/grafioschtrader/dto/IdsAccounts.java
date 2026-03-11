package grafioschtrader.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for persisting the user's dividend report account selection (security accounts and cash accounts).
 */
@Schema(description = "Holds the selected security account and cash account IDs for the dividend report.")
public class IdsAccounts {

  @Schema(description = "List of selected security account IDs. Empty list means all accounts.")
  private List<Integer> idsSecurityaccount;

  @Schema(description = "List of selected cash account IDs. A single entry of -1 means all accounts.")
  private List<Integer> idsCashaccount;

  public IdsAccounts() {
  }

  public IdsAccounts(List<Integer> idsSecurityaccount, List<Integer> idsCashaccount) {
    this.idsSecurityaccount = idsSecurityaccount;
    this.idsCashaccount = idsCashaccount;
  }

  public List<Integer> getIdsSecurityaccount() {
    return idsSecurityaccount;
  }

  public void setIdsSecurityaccount(List<Integer> idsSecurityaccount) {
    this.idsSecurityaccount = idsSecurityaccount;
  }

  public List<Integer> getIdsCashaccount() {
    return idsCashaccount;
  }

  public void setIdsCashaccount(List<Integer> idsCashaccount) {
    this.idsCashaccount = idsCashaccount;
  }
}
