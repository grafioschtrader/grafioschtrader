package grafiosch.gtnet.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import grafiosch.gtnet.GTNetExchangeLogPeriodType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Node in the exchange log tree structure. Represents either a single log entry
 * or an aggregated period with children.
 */
@Schema(description = "A node in the exchange log tree, representing a period with statistics")
public class GTNetExchangeLogNodeDTO {

  @Schema(description = "Display label for this node (e.g., '2025-01-15', 'Week 2', 'January')")
  public String label;

  @Schema(description = "The period type of this node")
  public GTNetExchangeLogPeriodType periodType;

  @Schema(description = "Start date of this period")
  public LocalDate periodStart;

  @Schema(description = "Number of entities sent (Consumer) or received (Supplier)")
  public int entitiesSent;

  @Schema(description = "Number of entities successfully updated")
  public int entitiesUpdated;

  @Schema(description = "Number of entities in response")
  public int entitiesInResponse;

  @Schema(description = "Number of requests in this period")
  public int requestCount;

  @Schema(description = "Child nodes (shorter periods contained within this period)")
  public List<GTNetExchangeLogNodeDTO> children;

  public GTNetExchangeLogNodeDTO() {
    this.children = new ArrayList<>();
  }

  public GTNetExchangeLogNodeDTO(String label, GTNetExchangeLogPeriodType periodType, LocalDate periodStart) {
    this();
    this.label = label;
    this.periodType = periodType;
    this.periodStart = periodStart;
  }

  /**
   * Adds statistics from another node (for aggregation).
   */
  public void addStats(GTNetExchangeLogNodeDTO other) {
    this.entitiesSent += other.entitiesSent;
    this.entitiesUpdated += other.entitiesUpdated;
    this.entitiesInResponse += other.entitiesInResponse;
    this.requestCount += other.requestCount;
  }

  /**
   * Sets statistics directly.
   */
  public void setStats(int entitiesSent, int entitiesUpdated, int entitiesInResponse, int requestCount) {
    this.entitiesSent = entitiesSent;
    this.entitiesUpdated = entitiesUpdated;
    this.entitiesInResponse = entitiesInResponse;
    this.requestCount = requestCount;
  }
}
