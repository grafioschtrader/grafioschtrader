package grafioschtrader.repository;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.calendar.rule.HolidayRuleSet;
import grafioschtrader.entities.TradingCalendarRuleSet;

public interface TradingCalendarRuleSetJpaRepositoryCustom extends BaseRepositoryCustom<TradingCalendarRuleSet> {

  /**
   * Returns every rule set for the overview table, with the transient display fields filled in.
   *
   * @return the rule sets ordered by name
   */
  List<TradingCalendarRuleSet> getAllRuleSets();

  /**
   * Returns the rule sets as select options for the stock exchange dialog.
   *
   * @return options with the rule set id as value and its name as key, ordered by name
   */
  List<ValueKeyHtmlSelectOptions> getRuleSetOptions();

  /**
   * Returns the rule sets that carry a MIC, keyed by that MIC. The stock exchange dialog uses it to preselect the
   * matching rule set as soon as a MIC is chosen, so the user does not have to look it up.
   *
   * @return map from MIC to rule set id
   */
  Map<String, Integer> getRuleSetIdByMic();

  /**
   * Resolves a rule set to its effective form, with the rules of the set it extends prepended.
   *
   * @param idTradingCalendarRuleSet the rule set to resolve
   * @return the effective rule set, ready to produce closures
   */
  HolidayRuleSet getResolvedRuleSet(Integer idTradingCalendarRuleSet);

  /**
   * Resolves a rule set and returns the closures of one year together with the name of the rule that produced each
   * one. Drives the preview in the rule editor, so the user sees the effect of a rule before saving it.
   *
   * @param idTradingCalendarRuleSet the rule set to evaluate
   * @param ruleYaml                 unsaved YAML to evaluate instead of the persisted text, may be null
   * @param year                     the year for which the closures are wanted
   * @return map from closure date to rule name, in ascending date order
   */
  SortedMap<String, String> previewClosures(Integer idTradingCalendarRuleSet, String ruleYaml, int year);

  /**
   * Refuses the deletion of a rule set that is still referenced, either by a stock exchange whose trading calendar it
   * produces or by another rule set that extends it. Deleting it would leave the exchange without a calendar source
   * or the extending set without its inherited rules.
   *
   * @param idTradingCalendarRuleSet the rule set about to be deleted
   */
  void assertDeletable(Integer idTradingCalendarRuleSet);

  /**
   * Validates the YAML of a rule set without saving it.
   *
   * @param ruleYaml the YAML text to check
   * @return the validation messages, empty when the YAML is valid
   */
  List<String> validateRuleYaml(String ruleYaml);
}
