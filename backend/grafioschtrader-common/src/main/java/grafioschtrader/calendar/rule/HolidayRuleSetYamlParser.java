package grafioschtrader.calendar.rule;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Turns the YAML of a stored trading calendar rule set into a {@link HolidayRuleSet}.
 *
 * <p>
 * The rules used to be classpath files that were read once at startup. They now live in the database and are edited by
 * users, so parsing happens on every save and on every calendar calculation. Only the text is handled here; the
 * identity of the set -- its MIC, its name and the set it extends -- are columns of the entity and are therefore not
 * part of the YAML.
 * </p>
 */
public class HolidayRuleSetYamlParser {

  /**
   * Unknown properties are rejected on purpose. The YAML is written by hand in an editor, so a misspelled key such as
   * {@code dayofWeek} must fail loudly rather than be dropped and leave the rule resolving to the wrong dates.
   */
  private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder()
      .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private HolidayRuleSetYamlParser() {
  }

  /**
   * Parses and validates the YAML of one rule set.
   *
   * @param ruleYaml the YAML text, may be null or blank for a set that only inherits
   * @return the parsed rule set, an empty one when the text is blank, never null
   * @throws IllegalStateException when the YAML is malformed or a rule is incomplete
   */
  public static HolidayRuleSet parse(String ruleYaml) {
    if (ruleYaml == null || ruleYaml.isBlank()) {
      return new HolidayRuleSet();
    }
    HolidayRuleSet ruleSet;
    try {
      ruleSet = YAML_MAPPER.readValue(ruleYaml, HolidayRuleSet.class);
    } catch (RuntimeException ex) {
      throw new IllegalStateException(ex.getMessage(), ex);
    }
    ruleSet.validate();
    return ruleSet;
  }
}
