package grafioschtrader.service;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the details of an algo alarm as a JSON object.
 *
 * <p>
 * The details are stored in {@code algo_message_alert.alarm_details}, whose column only accepts valid JSON, and
 * {@link AlgoAlarmTextRenderer} reads them back field by field to write the notification in the reader's language.
 * Assembling the JSON with {@code String.format} broke both: a free text such as an expression containing a quote
 * produced invalid JSON, and a text that was not JSON at all could not be stored. Every producer therefore goes through
 * this class, which escapes whatever it is given.
 * </p>
 */
final class AlgoAlarmDetails {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private AlgoAlarmDetails() {
  }

  /**
   * Creates the JSON object of an alarm from alternating field names and values.
   *
   * @param namesAndValues field name followed by its value, repeated; a value is a number, a boolean, null or anything
   *                       else, which is stored as its string form (enums by their constant name, dates as ISO dates)
   * @return the JSON text, always a valid object
   * @throws IllegalArgumentException if a name is missing its value
   */
  static String of(Object... namesAndValues) {
    if (namesAndValues.length % 2 != 0) {
      throw new IllegalArgumentException("Every alarm detail needs a name and a value");
    }
    ObjectNode node = MAPPER.createObjectNode();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      put(node, (String) namesAndValues[i], namesAndValues[i + 1]);
    }
    return MAPPER.writeValueAsString(node);
  }

  private static void put(ObjectNode node, String name, Object value) {
    switch (value) {
    case null -> node.putNull(name);
    case Integer i -> node.put(name, i);
    case Long l -> node.put(name, l);
    case Number n -> node.put(name, n.doubleValue());
    case Boolean b -> node.put(name, b);
    default -> node.put(name, value.toString());
    }
  }
}
