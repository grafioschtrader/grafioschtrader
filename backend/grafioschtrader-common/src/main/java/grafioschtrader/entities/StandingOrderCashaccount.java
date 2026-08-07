package grafioschtrader.entities;

import grafiosch.common.LockedWhenUsed;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Concrete standing order subclass for cash-account transactions (WITHDRAWAL, DEPOSIT, FEE and
 * INTEREST_CASHACCOUNT). Stores the base cash amount that is debited or credited on each execution.
 *
 * <p>
 * The amount may be denominated in a currency other than the cash account currency. This covers recurring charges that
 * a bank bills in one currency but debits in another — for example a monthly account fee of CHF 3.00 debited from a USD
 * account, where the booked USD amount changes every month with the exchange rate. When {@code amountCurrency} is set,
 * the exchange rate of the execution date is applied at execution time, optionally through the EvalEx expression in
 * {@code cashaccountAmountFormula}, which also allows for the spread a bank adds to the mid-market rate.
 * </p>
 *
 * Maps to the {@code standing_order_cashaccount} join table in the JOINED inheritance hierarchy.
 */
@Entity
@Table(name = StandingOrderCashaccount.TABNAME)
@DiscriminatorValue("C")
@Schema(description = """
    Standing order for cash-account transactions (WITHDRAWAL=0, DEPOSIT=1, INTEREST_CASHACCOUNT=2 or FEE=3).
    Contains the base cash amount per execution, optionally in a foreign currency that is converted with the
    exchange rate of the execution date and an optional EvalEx formula.""")
public class StandingOrderCashaccount extends StandingOrder {

  public static final String TABNAME = "standing_order_cashaccount";

  private static final long serialVersionUID = 1L;

  @Schema(description = """
      Base cash amount for each execution. Expressed in amountCurrency when that is set, otherwise in the currency
      of the cash account. For transaction type FEE the value must be positive, because the fee is always booked as
      a debit.""")
  @Column(name = "cashaccount_amount")
  @NotNull
  @LockedWhenUsed
  private Double cashaccountAmount;

  @Schema(description = """
      ISO currency code of cashaccountAmount when the amount is denominated in a currency other than the cash
      account currency. Null means the amount is already in the cash account currency and no conversion takes
      place.""")
  @Column(name = "amount_currency")
  @Size(min = 3, max = 3)
  @LockedWhenUsed
  private String amountCurrency;

  @Schema(description = """
      Currency pair used to look up the exchange rate at execution time. Derived from amountCurrency and the cash
      account currency when the standing order is saved, so the pair and its price history exist before the first
      execution. Null when amountCurrency is null.""")
  @Column(name = "id_currency_pair")
  @LockedWhenUsed
  private Integer idCurrencypair;

  @Schema(description = """
      Optional EvalEx formula for computing the booked amount. Available variables: a (base amount =
      cashaccountAmount), r (exchange rate of the currency pair on the execution date, only bound when
      amountCurrency is set). Example: 'a * r * 1.019' applies the exchange rate plus a bank spread of 1.9%.
      Without a formula the amount is converted with the plain exchange rate.""")
  @Column(name = "cashaccount_amount_formula")
  @Size(max = 200)
  @LockedWhenUsed
  private String cashaccountAmountFormula;

  public Double getCashaccountAmount() {
    return cashaccountAmount;
  }

  public void setCashaccountAmount(Double cashaccountAmount) {
    this.cashaccountAmount = cashaccountAmount;
  }

  public String getAmountCurrency() {
    return amountCurrency;
  }

  public void setAmountCurrency(String amountCurrency) {
    this.amountCurrency = amountCurrency;
  }

  public Integer getIdCurrencypair() {
    return idCurrencypair;
  }

  public void setIdCurrencypair(Integer idCurrencypair) {
    this.idCurrencypair = idCurrencypair;
  }

  public String getCashaccountAmountFormula() {
    return cashaccountAmountFormula;
  }

  public void setCashaccountAmountFormula(String cashaccountAmountFormula) {
    this.cashaccountAmountFormula = cashaccountAmountFormula;
  }
}
