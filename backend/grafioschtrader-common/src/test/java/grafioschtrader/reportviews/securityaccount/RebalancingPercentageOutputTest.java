package grafioschtrader.reportviews.securityaccount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.algo.RebalancingPlan.ClassAdjustment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RebalancingPercentageOutputTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  @DisplayName("Rebalancing JSON rounds percentage points while preserving calculation and amount precision")
  void roundsReportPercentagesOnly() {
    var position = new SecurityPositionSummary("CHF", 2);
    position.targetPercentage = 12.3456;
    position.actualPercentage = 9.8765;
    position.deviationPercentage = -2.4691;
    position.parentDeviation = -4.9382;
    position.securityDeviationPercentage = 5.1234;
    position.recommendedAmount = 123.45678;
    position.recommendedUnits = 0.12345678;
    var group = new SecurityPositionDynamicGroupSummary<String>("Equities");
    group.groupTargetPercentage = position.targetPercentage;
    group.groupActualPercentage = position.actualPercentage;
    group.groupDeviationPercentage = position.deviationPercentage;
    group.groupParentDeviation = position.parentDeviation;
    group.groupSecurityDeviationPercentage = position.securityDeviationPercentage;
    group.securityPositionSummaryList.add(position);
    var summary = new SecurityPositionGrandSummary("CHF", 2);
    summary.securityPositionGroupSummaryList.add(group);
    summary.toleranceThreshold = 3.4567;
    summary.overallAllocationMismatchPercentage = 33.33333;
    var adjustment = new ClassAdjustment(303, -4.9382, 5.1234, 3, 123.45678, 100.12345, 23.33333, null);
    summary.classAdjustments = List.of(adjustment);

    JsonNode json = mapper.readTree(mapper.writeValueAsString(summary));
    JsonNode groupJson = json.get("securityPositionGroupSummaryList").get(0);
    JsonNode positionJson = groupJson.get("securityPositionSummaryList").get(0);
    assertPercentages(positionJson, "targetPercentage", "actualPercentage", "deviationPercentage", "parentDeviation",
        "securityDeviationPercentage");
    assertPercentages(groupJson, "groupTargetPercentage", "groupActualPercentage", "groupDeviationPercentage",
        "groupParentDeviation", "groupSecurityDeviationPercentage");
    assertEquals(3.46, json.get("toleranceThreshold").asDouble());
    assertEquals(33.33, json.get("overallAllocationMismatchPercentage").asDouble());
    JsonNode diagnostic = json.get("classAdjustments").get(0);
    assertEquals(-4.94, diagnostic.get("parentDeviation").asDouble());
    assertEquals(5.12, diagnostic.get("securityDeviationPercentage").asDouble());
    assertEquals(123.45678, diagnostic.get("requestedAdjustment").asDouble());
    assertEquals(100.12345, diagnostic.get("plannedAdjustment").asDouble());
    assertEquals(23.33333, diagnostic.get("residual").asDouble());
    assertEquals(123.45678, positionJson.get("recommendedAmount").asDouble());
    assertEquals(0.12345678, positionJson.get("recommendedUnits").asDouble());
    assertEquals(12.3456, position.targetPercentage);
    assertEquals(-4.9382, group.groupParentDeviation);
    assertEquals(-4.9382, adjustment.parentDeviation());
    assertEquals(3.4567, summary.toleranceThreshold);
  }

  @Test
  @DisplayName("Reports without rebalancing keep absent percentages null, and small deviations round to zero")
  void preservesMissingPercentagesAndRoundsSmallDeviations() {
    var position = new SecurityPositionSummary("CHF", 2);
    var group = new SecurityPositionDynamicGroupSummary<String>("Cash");
    var summary = new SecurityPositionGrandSummary("CHF", 2);
    group.securityPositionSummaryList.add(position);
    summary.securityPositionGroupSummaryList.add(group);
    JsonNode json = mapper.readTree(mapper.writeValueAsString(summary));
    JsonNode groupJson = json.get("securityPositionGroupSummaryList").get(0);
    JsonNode positionJson = groupJson.get("securityPositionSummaryList").get(0);
    for (String field : List.of("targetPercentage", "actualPercentage", "deviationPercentage", "parentDeviation",
        "securityDeviationPercentage")) {
      assertTrue(positionJson.get(field).isNull(), field);
      assertTrue(groupJson.get("group" + Character.toUpperCase(field.charAt(0)) + field.substring(1)).isNull(), field);
    }
    assertTrue(json.get("toleranceThreshold").isNull());
    assertTrue(json.get("overallAllocationMismatchPercentage").isNull());
    assertTrue(json.get("classAdjustments").isNull());
    position.deviationPercentage = -0.004;
    assertEquals(0.0, mapper.readTree(mapper.writeValueAsString(position)).get("deviationPercentage").asDouble());
  }

  private void assertPercentages(JsonNode json, String target, String actual, String deviation, String parent,
      String band) {
    assertEquals(12.35, json.get(target).asDouble());
    assertEquals(9.88, json.get(actual).asDouble());
    assertEquals(-2.47, json.get(deviation).asDouble());
    assertEquals(-4.94, json.get(parent).asDouble());
    assertEquals(5.12, json.get(band).asDouble());
  }
}
