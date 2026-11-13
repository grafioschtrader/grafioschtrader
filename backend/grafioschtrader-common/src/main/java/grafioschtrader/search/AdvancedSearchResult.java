package grafioschtrader.search;

import java.util.List;
import java.util.Map;

import grafioschtrader.entities.Security;

/**
 * Result of a security search that may carry advanced criteria.
 *
 * @param securities   the securities that satisfy all criteria
 * @param values       per security id and field key the value that satisfied a criterion on a user-defined field or an
 *                     indicator; empty without such criteria. A number is a {@code Double}, a date an ISO date
 *                     {@code String}, a text a {@code String} and a flag a {@code Boolean}
 * @param notEvaluated the number of candidates for which at least one indicator could not be evaluated, and which
 *                     therefore did not match
 */
public record AdvancedSearchResult(List<Security> securities, Map<Integer, Map<String, Object>> values,
    int notEvaluated) {

  /**
   * Creates the result of a search without advanced criteria.
   *
   * @param securities the matching securities
   * @return a result without values
   */
  public static AdvancedSearchResult of(List<Security> securities) {
    return new AdvancedSearchResult(securities, Map.of(), 0);
  }
}
