package grafioschtrader.service;

import java.time.LocalDate;

import grafioschtrader.dto.TaxEstimateRequest.EventKind;
import grafioschtrader.dto.TaxEstimateResult;
import grafioschtrader.entities.Security;
import grafioschtrader.types.TransactionType;

/** Side-effect-free costs for a single candidate order in instrument currency. */
public final class AlgoReplayCosts {
  public record Estimate(double fee, double tax, double accruedInterest, TaxEstimateResult diagnostics) {
    public double total() {
      return fee + tax + accruedInterest;
    }
  }

  private final AlgoReplayFees fees;
  private final AlgoReplayTaxes taxes;
  private final AlgoReplayInputs.Snapshot inputs;

  public AlgoReplayCosts(AlgoReplayFees fees, AlgoReplayTaxes taxes, AlgoReplayInputs.Snapshot inputs) {
    this.fees = fees;
    this.taxes = taxes;
    this.inputs = inputs;
  }

  public Estimate estimate(Integer account, Security security, double units, double quotation, TransactionType type,
      LocalDate date, double equity) {
    double fee = fees.cost(account, security, units, quotation, type, date, equity);
    double accrued = 0;
    var instrument = inputs.instruments().get(security.getId());
    if (instrument != null && "GENERATED".equals(instrument.incomeSource()))
      accrued = new AlgoReplayCouponSchedule(instrument.couponTerms()).accrued(units, date);
    var result = taxes.estimate(security.getId(), account,
        type == TransactionType.ACCUMULATE ? EventKind.BUY : EventKind.SELL, date, units, quotation, accrued, 0);
    return new Estimate(fee, result.estimatedTax(), accrued, result);
  }

  /** One budget includes initial evaluation, account switches and every sizing candidate. */
  public final class Order {
    private int evaluations;

    public Estimate estimate(Integer account, Security security, double units, double quotation, TransactionType type,
        LocalDate date, double equity) {
      if (++evaluations > 100)
        throw new IllegalArgumentException("REPLAY_NOT_FUNDED");
      return AlgoReplayCosts.this.estimate(account, security, units, quotation, type, date, equity);
    }
  }

  public Order order() {
    return new Order();
  }
}
