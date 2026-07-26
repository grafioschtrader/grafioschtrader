package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.TradingCalendarRuleSet;

/**
 * Repository for the trading calendar rule sets, the shared data from which the closures of a stock exchange can be
 * calculated instead of deriving them from the quotes of a reference index.
 */
public interface TradingCalendarRuleSetJpaRepository extends JpaRepository<TradingCalendarRuleSet, Integer>,
    TradingCalendarRuleSetJpaRepositoryCustom, UpdateCreateJpaRepository<TradingCalendarRuleSet> {

  List<TradingCalendarRuleSet> findAllByOrderByNameAsc();

  /**
   * Finds the rule set written for a market identifier code. Used when a stock exchange is created with that MIC, so
   * the matching rule set can be assigned without the user having to look it up.
   *
   * @param mic the market identifier code
   * @return the rule set carrying this MIC, or empty when none does
   */
  Optional<TradingCalendarRuleSet> findByMic(String mic);

  Optional<TradingCalendarRuleSet> findByName(String name);

  /**
   * Counts the stock exchanges that currently calculate their trading calendar from the given rule set. A rule set
   * that is still in use must not be deleted.
   *
   * Named query: TradingCalendarRuleSet.countUsingStockexchanges
   *
   * @param idTradingCalendarRuleSet the rule set to check
   * @return number of referencing stock exchanges, never null
   */
  @Query(nativeQuery = true)
  int countUsingStockexchanges(Integer idTradingCalendarRuleSet);

  /**
   * Returns the number of referencing stock exchanges for every rule set, so the overview table can show at a glance
   * which rule sets are in use. Rule sets without a single exchange are absent from the result.
   *
   * Named query: TradingCalendarRuleSet.countUsingStockexchangesGrouped
   *
   * @return rows of rule set id and usage count
   */
  @Query(nativeQuery = true)
  List<IdRuleSetUsage> countUsingStockexchangesGrouped();

  /** Projection of the per rule set usage count. */
  public static interface IdRuleSetUsage {
    Integer getIdTradingCalendarRuleSet();

    Integer getUsedByExchanges();
  }
}
