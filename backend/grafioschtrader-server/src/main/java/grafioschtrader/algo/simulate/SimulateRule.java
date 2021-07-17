package grafioschtrader.algo.simulate;

import java.time.LocalDate;

import grafioschtrader.entities.AlgoTop;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.repository.WatchlistJpaRepository;

/**
 * Simulate a rule strategy with existing accounts over a certain period. All
 * existing transactions are not taken into account.
 *
 *
 * @author Hugo Graf
 *
 */
public class SimulateRule {
  private LocalDate startDate;
  private LocalDate endDate;
  private AlgoTop algoTop;

  private final AlgoAssetclassJpaRepository algoAssetclassJpaRepository;
  private final WatchlistJpaRepository watchlistJpaRepository;
  private final HistoryquoteJpaRepository historyquoteJpaRepository;
  private final SecurityJpaRepository securityJpaRepository;
  private final TransactionJpaRepository transactionJpaRepository;

  public SimulateRule(AlgoAssetclassJpaRepository algoAssetclassJpaRepository,
      WatchlistJpaRepository watchlistJpaRepository, HistoryquoteJpaRepository historyquoteJpaRepository,
      SecurityJpaRepository securityJpaRepository, TransactionJpaRepository transactionJpaRepository) {
    super();

    this.algoAssetclassJpaRepository = algoAssetclassJpaRepository;
    this.watchlistJpaRepository = watchlistJpaRepository;
    this.historyquoteJpaRepository = historyquoteJpaRepository;
    this.securityJpaRepository = securityJpaRepository;
    this.transactionJpaRepository = transactionJpaRepository;
  }

  public void simulate(LocalDate startDate, LocalDate endDate, AlgoTop algoTop) {
    this.startDate = startDate;
    this.endDate = endDate;
    this.algoTop = algoTop;
  }

  @Override
  public String toString() {
    return "SimulateRule [startDate=" + startDate + ", endDate=" + endDate + ", algoTop=" + algoTop
        + ", algoAssetclassJpaRepository=" + algoAssetclassJpaRepository + ", watchlistJpaRepository="
        + watchlistJpaRepository + ", historyquoteJpaRepository=" + historyquoteJpaRepository
        + ", securityJpaRepository=" + securityJpaRepository + ", transactionJpaRepository=" + transactionJpaRepository
        + "]";
  }

}
