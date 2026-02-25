package grafioschtrader.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * Concrete standing order subclass for cash-account transactions (WITHDRAWAL and DEPOSIT). Stores the fixed
 * cash amount that is debited or credited on each execution. Maps to the {@code standing_order_cashaccount}
 * join table in the JOINED inheritance hierarchy.
 */
@Entity
@Table(name = StandingOrderCashaccount.TABNAME)
@DiscriminatorValue("C")
@Schema(description = """
    Standing order for cash-account transactions (WITHDRAWAL=0 or DEPOSIT=1).
    Contains the fixed cash amount per execution.""")
public class StandingOrderCashaccount extends StandingOrder {

  public static final String TABNAME = "standing_order_cashaccount";

  private static final long serialVersionUID = 1L;

  @Schema(description = "Cash amount for each DEPOSIT or WITHDRAWAL execution")
  @Column(name = "cashaccount_amount")
  @NotNull
  private Double cashaccountAmount;

  public Double getCashaccountAmount() {
    return cashaccountAmount;
  }

  public void setCashaccountAmount(Double cashaccountAmount) {
    this.cashaccountAmount = cashaccountAmount;
  }
}
