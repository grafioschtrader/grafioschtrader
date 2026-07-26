package grafioschtrader.calendar.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * Merges a rule set with the set it extends.
 *
 * <p>
 * A venue that follows the calendar of a larger exchange stores only its own deviations, so the effective calendar is
 * the referenced set's rules followed by the set's own. The order matters only for the diagnostic rule names: when two
 * rules resolve to the same date, {@link HolidayRuleSet#closuresWithRuleName} keeps both names in evaluation order.
 * </p>
 */
public class HolidayRuleSetResolver {

  private HolidayRuleSetResolver() {
  }

  /**
   * Produces the effective rule set of a child, with the already resolved rules of its parent prepended.
   *
   * <p>
   * The own rules are appended last so a deviation can add a holiday the parent does not have; a deviation that needs
   * to <em>remove</em> an inherited holiday expresses this with a {@code validTo} on the parent rule rather than by
   * subtraction here. The authoritative range narrows to the part both sets can vouch for.
   * </p>
   *
   * <p>
   * The date level corrections {@code additionalClosures} and {@code openDates} are deliberately <em>not</em>
   * inherited. They describe deviations observed on the parent exchange's own calendar and do not automatically apply
   * to a venue that merely shares the recurring holidays.
   * </p>
   *
   * @param parent the resolved rule set that is extended
   * @param child  the set holding the deviations
   * @return a new rule set combining both, leaving the arguments untouched
   */
  public static HolidayRuleSet merge(HolidayRuleSet parent, HolidayRuleSet child) {
    List<HolidayRule> merged = new ArrayList<>(parent.getRules());
    merged.addAll(child.getRules());

    HolidayRuleSet effective = new HolidayRuleSet();
    effective.setNote(child.getNote());
    effective.setAuthoritativeFrom(laterBound(parent.getAuthoritativeFrom(), child.getAuthoritativeFrom()));
    effective.setAuthoritativeThrough(earlierBound(parent.getAuthoritativeThrough(), child.getAuthoritativeThrough()));
    effective.setRules(merged);
    effective.setAdditionalClosures(child.getAdditionalClosures());
    effective.setOpenDates(child.getOpenDates());
    return effective;
  }

  private static Integer laterBound(Integer first, Integer second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    return Math.max(first, second);
  }

  private static Integer earlierBound(Integer first, Integer second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    return Math.min(first, second);
  }
}
