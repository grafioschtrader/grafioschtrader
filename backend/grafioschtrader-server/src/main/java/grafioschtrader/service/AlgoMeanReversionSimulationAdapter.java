package grafioschtrader.service;

import java.util.Set;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.AlgoTradingRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.service.AlgoMeanReversionDecisionService.*;
import grafioschtrader.types.TransactionType;

/** Books supplied fills in an authorized simulation only. Historical scheduling is supplied by the replay caller. */
@Service
public class AlgoMeanReversionSimulationAdapter {
  private final AlgoTradingRepository data;
  private final TransactionJpaRepository transactions;
  private final AlgoMeanReversionPositionService positions;
  private final AlgoMeanReversionFillBudgetService budgets;

  public AlgoMeanReversionSimulationAdapter(AlgoTradingRepository data, TransactionJpaRepository transactions,
      AlgoMeanReversionPositionService positions, AlgoMeanReversionFillBudgetService budgets) {
    this.data = data;
    this.transactions = transactions;
    this.positions = positions;
    this.budgets = budgets;
  }

  /**
   * The fill carries normal account, price, FX and margin fields and is validated by the portfolio write path.
   *
   * @throws Exception
   */
  @Transactional(rollbackFor = Exception.class)
  public Transaction fill(Context context, Decision decision, String fillId, Transaction fill) throws Exception {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getDetails() instanceof User user) || user.isTenantAccessReadOnly())
      throw new SecurityException("Unauthorized simulation fill");
    var tenant = data.lockTenant(context.tenant());
    if (tenant.getIdParentTenant() == null || !tenant.getIdParentTenant().equals(user.getActualIdTenant()))
      throw new SecurityException("A simulation belonging to the authenticated tenant is required");
    // Costs affect cash affordability; the strategy budget remains an exposure budget.
    if (!decision.actionable() || fillId == null || fillId.isBlank() || fillId.length() > 128
        || decision.identity().length() > 255 || fill.getId() != null || fill.getSecurity() == null
        || !context.security().equals(fill.getSecurity().getId()) || fill.getUnits() == null
        || !Double.isFinite(fill.getUnits()) || Math.abs(fill.getUnits()) <= 0 || fill.getTransactionTime() == null
        || fill.getQuotation() == null || !Double.isFinite(fill.getQuotation()) || fill.getQuotation() <= 0
        || fill.getTransactionCost() != null
            && (!Double.isFinite(fill.getTransactionCost()) || fill.getTransactionCost() < 0)
        || fill.getTaxCost() != null && (!Double.isFinite(fill.getTaxCost()) || fill.getTaxCost() < 0)
        || !fill.getTransactionTime().toLocalDate().isAfter(context.date()))
      throw new IllegalArgumentException("Invalid mean reversion fill");
    if (context.market().tradingExcluded(fill.getSecurity())
        || context.market().allocation() == null && fill.getSecurity().isSimulationTradingExcluded())
      throw new IllegalArgumentException("REPLAY_INSTRUMENT_EXCLUDED");
    var existing = data.fill(context.tenant(), fillId);
    if (existing.isPresent()) {
      var old = existing.get();
      if (!decision.identity().equals(old.getAlgoSignalId())
          || !java.util.Objects.equals(fill.getUnits(), old.getUnits())
          || !java.util.Objects.equals(fill.getQuotation(), old.getQuotation())
          || !java.util.Objects.equals(fill.getTransactionCost(), old.getTransactionCost())
          || !java.util.Objects.equals(fill.getTaxCost(), old.getTaxCost())
          || !java.util.Objects.equals(fill.getAssetInvestmentValue1(), old.getAssetInvestmentValue1())
          || !java.util.Objects.equals(fill.getIdSecurityaccount(), old.getIdSecurityaccount())
          || !java.util.Objects.equals(fill.getCashaccount() == null ? null : fill.getCashaccount().getId(),
              old.getCashaccount() == null ? null : old.getCashaccount().getId())
          || !java.util.Objects.equals(fill.getIdCurrencypair(), old.getIdCurrencypair())
          || !java.util.Objects.equals(fill.getCurrencyExRate(), old.getCurrencyExRate())
          || !java.util.Objects.equals(fill.getCashaccountAmount(), old.getCashaccountAmount())
          || fill.getTransactionType() != old.getTransactionType()
          || !fill.getTransactionTime().equals(old.getTransactionTime()))
        throw new IllegalArgumentException("Fill identity reused");
      return existing.get();
    }
    if (data.filledUnits(context.tenant(), decision.identity()) + Math.abs(fill.getUnits()) > decision.quantity()
        + 1e-8)
      throw new IllegalArgumentException("Fill exceeds the outstanding signal quantity");
    boolean buy = decision.increasesExposure() ? decision.direction() > 0 : decision.direction() < 0;
    if (fill.getTransactionType() != (buy ? TransactionType.ACCUMULATE : TransactionType.REDUCE))
      throw new IllegalArgumentException("Fill direction differs from the signal");
    Position current = context.position();
    if (decision.action() == Action.ADD) {
      current = positions.reconcile(context.tenant(), context.strategy(), fill.getSecurity(),
          fill.getTransactionTime().toLocalDate());
      if (current.lifecycle() != context.position().lifecycle()
          || Math.signum(current.signedUnits()) != decision.direction()
          || current.realizedExitUnits() > context.position().realizedExitUnits() + 1e-8)
        throw new IllegalArgumentException("Addition was superseded by a position reduction or closure");
    }
    budgets.validate(user.getActualIdTenant(), context, decision, fill, current,
        data.filledUnits(context.tenant(), decision.identity()));
    fill.setIdTenant(context.tenant());
    fill.setIdAlgoStrategy(context.strategy());
    fill.setAlgoFillId(fillId);
    fill.setAlgoSignalId(decision.identity());
    fill.setAlgoTrancheTargets(AlgoTrancheTargets.write(decision.trancheTargets()));
    fill.setSimulationOpening(false);
    Transaction saved = transactions.saveOnlyAttributes(fill, null, Set.of());
    positions.reconcile(context.tenant(), context.strategy(), fill.getSecurity(),
        fill.getTransactionTime().toLocalDate());
    return saved;
  }
}
