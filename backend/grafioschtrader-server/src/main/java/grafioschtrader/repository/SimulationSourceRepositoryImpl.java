package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import grafioschtrader.entities.Transaction;

/** Routes the ledger read through the replay cache when one is open on the calling thread. */
public class SimulationSourceRepositoryImpl implements SimulationSourceRepositoryCustom {

  private SimulationSourceRepository simulationSourceRepository;

  @Autowired
  public void setSimulationSourceRepository(@Lazy final SimulationSourceRepository simulationSourceRepository) {
    this.simulationSourceRepository = simulationSourceRepository;
  }

  @Override
  public List<Transaction> transactions(Integer idTenant, LocalDate exclusiveEnd) {
    SimulationLedgerCache cache = SimulationLedgerCache.active(idTenant);
    return cache == null ? simulationSourceRepository.loadTransactions(idTenant, exclusiveEnd)
        : cache.transactions(exclusiveEnd, simulationSourceRepository);
  }
}
