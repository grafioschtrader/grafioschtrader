package grafioschtrader.reportviews.securitycurrency;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.search.AdvancedSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Represents a container for lists of securities and currency pairs.")
public class SecuritycurrencyLists implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "A list of security entities.")
  public List<Security> securityList;
  @Schema(description = "A list of currency pair entities.")
  public List<Currencypair> currencypairList;
  @Schema(description = """
      Only for a search with advanced criteria: per security id and field key the value that satisfied the criterion.
      A number is a double, a date an ISO date string, a text a string and a flag a boolean. Empty otherwise.""")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public Map<Integer, Map<String, Object>> advancedValues = new HashMap<>();
  @Schema(description = """
      Only for a search with indicator criteria: the number of candidates for which at least one indicator could not be
      evaluated, for example because a price or a fundamental value is missing. They never match.""")
  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  public int advancedNotEvaluated;

  public SecuritycurrencyLists() {
  }

  public SecuritycurrencyLists(List<Security> securityList, List<Currencypair> currencypairList) {
    super();
    this.securityList = securityList;
    this.currencypairList = currencypairList;
  }

  /**
   * Creates the lists of a search that may have carried advanced criteria.
   *
   * @param result        the securities with the values that satisfied the criteria
   * @param currencypairs the matching currency pairs
   * @return the lists including the matched values and the count of candidates that could not be evaluated
   */
  public static SecuritycurrencyLists of(AdvancedSearchResult result, List<Currencypair> currencypairs) {
    SecuritycurrencyLists lists = new SecuritycurrencyLists(result.securities(), currencypairs);
    lists.advancedValues = new HashMap<>(result.values());
    lists.advancedNotEvaluated = result.notEvaluated();
    return lists;
  }

  public int getLength() {
    return securityList.size() + currencypairList.size();
  }

}
