package grafioschtrader.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Concrete standing order subclass for security transactions (ACCUMULATE and REDUCE). Supports two mutually
 * exclusive purchasing modes:
 * <ul>
 *   <li><b>Unit-based</b>: {@code units} is set, {@code investAmount} is null — buys/sells a fixed number of units</li>
 *   <li><b>Amount-based</b>: {@code investAmount} is set, {@code units} is null — invests a fixed cash amount,
 *       units are calculated at execution time from the current price</li>
 * </ul>
 * Maps to the {@code standing_order_security} join table in the JOINED inheritance hierarchy.
 */
@Entity
@Table(name = StandingOrderSecurity.TABNAME)
@DiscriminatorValue("S")
@Schema(description = """
    Standing order for security transactions (ACCUMULATE=4 or REDUCE=5). Supports unit-based mode (fixed units)
    and amount-based mode (fixed investment amount, units calculated at execution time from the current price).
    Exactly one of units or investAmount must be non-null.""")
public class StandingOrderSecurity extends StandingOrder {

  public static final String TABNAME = "standing_order_security";

  private static final long serialVersionUID = 1L;

  @Schema(description = "Security to accumulate or reduce")
  @JoinColumn(name = "id_securitycurrency", referencedColumnName = "id_securitycurrency")
  @ManyToOne
  @NotNull
  private Security security;

  @Schema(description = "Security account where the security is held")
  @Column(name = "id_security_account")
  @NotNull
  private Integer idSecurityaccount;

  @Schema(description = "Currency pair ID when the transaction involves currency conversion")
  @Column(name = "id_currency_pair")
  private Integer idCurrencypair;

  @Schema(description = """
      Number of units for unit-based mode. Mutually exclusive with investAmount —
      exactly one of units or investAmount must be non-null.""")
  @Column(name = "units")
  private Double units;

  @Schema(description = """
      Investment amount for amount-based mode. Units are calculated at execution time as investAmount / quotation.
      Mutually exclusive with units — exactly one of units or investAmount must be non-null.""")
  @Column(name = "invest_amount")
  private Double investAmount;

  @Schema(description = """
      Controls whether the invest amount includes costs (gross mode) or costs are added on top (net mode).
      When true (1): investAmount is the total cash outflow, costs are deducted before calculating units.
      When false (0, default): investAmount is the net investment, costs are added on top.""")
  @Column(name = "amount_includes_costs")
  @NotNull
  private boolean amountIncludesCosts = false;

  @Schema(description = """
      Whether fractional units are allowed. When true (default): calculated units can be fractional (e.g. 5.237).
      When false: units are rounded down to whole numbers.""")
  @Column(name = "fractional_units")
  @NotNull
  private boolean fractionalUnits = true;

  @Schema(description = """
      EvalEx formula for computing tax cost. Available variables: u (units), q (quotation/price), a (amount = u*q).
      Mutually exclusive with taxCost — when taxCost has a value, this formula is ignored.""")
  @Column(name = "tax_cost_formula")
  @Size(max = 200)
  private String taxCostFormula;

  @Schema(description = """
      EvalEx formula for computing transaction cost. Available variables: u (units), q (quotation/price),
      a (amount = u*q). Mutually exclusive with transactionCost — when transactionCost has a value,
      this formula is ignored.""")
  @Column(name = "transaction_cost_formula")
  @Size(max = 200)
  private String transactionCostFormula;

  @Schema(description = "Fixed tax cost fallback, used when taxCostFormula is null")
  @Column(name = "tax_cost")
  private Double taxCost;

  public Security getSecurity() {
    return security;
  }

  public void setSecurity(Security security) {
    this.security = security;
  }

  public Integer getIdSecurityaccount() {
    return idSecurityaccount;
  }

  public void setIdSecurityaccount(Integer idSecurityaccount) {
    this.idSecurityaccount = idSecurityaccount;
  }

  public Integer getIdCurrencypair() {
    return idCurrencypair;
  }

  public void setIdCurrencypair(Integer idCurrencypair) {
    this.idCurrencypair = idCurrencypair;
  }

  public Double getUnits() {
    return units;
  }

  public void setUnits(Double units) {
    this.units = units;
  }

  public Double getInvestAmount() {
    return investAmount;
  }

  public void setInvestAmount(Double investAmount) {
    this.investAmount = investAmount;
  }

  public boolean isAmountIncludesCosts() {
    return amountIncludesCosts;
  }

  public void setAmountIncludesCosts(boolean amountIncludesCosts) {
    this.amountIncludesCosts = amountIncludesCosts;
  }

  public boolean isFractionalUnits() {
    return fractionalUnits;
  }

  public void setFractionalUnits(boolean fractionalUnits) {
    this.fractionalUnits = fractionalUnits;
  }

  public String getTaxCostFormula() {
    return taxCostFormula;
  }

  public void setTaxCostFormula(String taxCostFormula) {
    this.taxCostFormula = taxCostFormula;
  }

  public String getTransactionCostFormula() {
    return transactionCostFormula;
  }

  public void setTransactionCostFormula(String transactionCostFormula) {
    this.transactionCostFormula = transactionCostFormula;
  }

  public Double getTaxCost() {
    return taxCost;
  }

  public void setTaxCost(Double taxCost) {
    this.taxCost = taxCost;
  }

}
