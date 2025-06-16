package grafioschtrader.reportviews.transactioncost;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.entities.Securityaccount;
import grafioschtrader.reportviews.SecurityCostGroup;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aggregated transaction cost summary grouped by security account, providing totals and averages for cost analysis and broker comparison")
public class TransactionCostGroupSummary extends SecurityCostGroup {

  @JsonIgnore
  public Integer idSecurityaccount;
  @Schema(description = "The security account entity containing broker details, account settings, and trading configuration")
  public Securityaccount securityaccount;

  public List<TransactionCostPosition> transactionCostPositions = new ArrayList<>();

  public TransactionCostGroupSummary(Integer idSecurityaccount, int precision) {
    super(precision);
    this.idSecurityaccount = idSecurityaccount;
  }

  public void add(TransactionCostPosition transactionCostPosition) {
    transactionCostPositions.add(transactionCostPosition);
  }

  @Override
  public void caclulateGroupSummary() {
    transactionCostPositions.forEach(transactionCostPosition -> {
      sumPositionToGroupTotal(transactionCostPosition);
    });
    calcAverages(transactionCostPositions.size());
  }

}
