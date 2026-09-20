package grafiosch.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.dynamic.model.ClassDescriptorInputAndShow;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

/** Wire contracts for personal layouts and read-only dashboard summaries. */
public final class DashboardDtos {
  private DashboardDtos() {
  }

  @Schema(description = "Atomic personal layout save. No caller supplied owner or tenant is accepted.")
  public record Save(Long expectedRevision, int schemaVersion, List<JsonNode> widgets, boolean resetToDefault) {
  }

  @Schema(description = "Eligible layout with independently loaded widget results. A null revision denotes unsaved defaults.")
  public record Dashboard(boolean persisted, Long revision, int schemaVersion, Integer activeTenantId,
      @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND) LocalDateTime generatedAt,
      List<JsonNode> widgets, List<Result> results, int remainingCapacity) {
  }

  @Schema(description = "One read-only widget result, with isolated error and data timestamp.")
  public record Result(String instanceId, String type, String status, Payload payload,
      @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND) LocalDateTime dataAsOf, String errorCode) {
  }

  @Schema(description = """
      Counted lists. Each count describes its own population; overlapping lists must not be summed. A widget whose rows do
      not fit the flat row shape leaves the lists empty and carries its own document in custom instead; the two are not
      mixed within one widget.""")
  public record Payload(List<SummaryList> lists, JsonNode custom) {
    public Payload(List<SummaryList> lists) {
      this(lists, null);
    }

    @JsonIgnore
    public boolean isEmpty() {
      return custom == null ? lists.stream().allMatch(list -> list.count() == 0) : custom.isEmpty();
    }
  }

  public record SummaryList(String titleKey, long count, Long userCount, String destination, List<Row> rows) {
  }

  public record Row(Integer id, String nickname, String subject, String entity, String noteRequest,
      @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND) LocalDateTime creationTime, Integer dayLimit,
      String validUntil) {
  }

  @Schema(description = "Registered widget with server-authoritative form constraints and defaults.")
  public record Descriptor(String type, String titleKey, String descriptionKey, String defaultWidth,
      Map<String, Integer> defaultConfig, ClassDescriptorInputAndShow formDefinition) {
  }

  public record Catalogue(List<Descriptor> descriptors, int remainingCapacity, List<JsonNode> defaults) {
  }
}
