package grafioschtrader.service;

import java.util.Map;
import java.util.TreeMap;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Internal transaction metadata codec; quantities are adjusted together with their originating fills. */
final class AlgoTrancheTargets {
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private AlgoTrancheTargets() {
  }

  static String write(Map<String, Double> targets) {
    return targets.isEmpty() ? null : MAPPER.writeValueAsString(new TreeMap<>(targets));
  }

  static Map<String, Double> read(String json, double splitFactor) {
    if (json == null)
      return Map.of();
    Map<String, Double> targets = MAPPER.readValue(json, new TypeReference<Map<String, Double>>() {
    });
    targets.replaceAll((_, value) -> value * splitFactor);
    return Map.copyOf(targets);
  }
}
