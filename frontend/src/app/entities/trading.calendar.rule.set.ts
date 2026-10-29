import { BaseID } from '../lib/entities/base.id';
import { Auditable } from '../lib/entities/auditable';

/**
 * A set of holiday rules from which the closures of one or more stock exchanges are calculated. It is the alternative
 * to deriving the trading calendar from the quotes of a reference index; a stock exchange uses either the index or a
 * rule set, never both.
 */
export class TradingCalendarRuleSet extends Auditable implements BaseID {
  idTradingCalendarRuleSet?: number | null = null;
  /** Market identifier code this rule set was written for. Optional and unique. */
  mic?: string | null = null;
  name: string | null = null;
  /** Rule set whose rules are evaluated before this one's, so a venue stores only its deviations. */
  idExtendsRuleSet?: number | null = null;
  /** The rules as YAML: note, authoritative range, rules, additionalClosures and openDates. */
  ruleYaml?: string | null = null;
  /** Read only, name of the rule set referenced by idExtendsRuleSet. */
  nameExtendsRuleSet?: string | null = null;
  /** Read only, number of stock exchanges currently using this rule set. */
  usedByExchanges?: number | null = null;

  public override getId(): number | null | undefined {
    return this.idTradingCalendarRuleSet;
  }
}
