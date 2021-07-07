package grafioschtrader.reportviews.transactioncost;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.entities.Securityaccount;
import grafioschtrader.reportviews.SecurityCostGroup;

/**
 * Group summary of transaction cost report. It is grouped by security account.
 *
 * @author Hugo Graf
 *
 */
public class TransactionCostGroupSummary extends SecurityCostGroup {

  @JsonIgnore
  public Integer idSecurityaccount;
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
