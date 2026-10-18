package grafioschtrader.dto;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Result of testing a fill rule against the tenant scope of the caller; nothing is written. Coverage and agreement
    are counted separately. Existing values are comparison data, not truth: a differing row may also be a wrong manual
    value.""")
public class UDFFillTestResult {

  /** Maximum number of entries in each of the two lists. */
  public static final int MAX_LIST_ENTRIES = 20;

  @Schema(description = "Number of securities in the tenant scope that match the asset classes of the field")
  public int inScope;

  @Schema(description = "Number of securities in scope that already hold a value in this field")
  public int withValue;

  @Schema(description = "Number of securities for which the rule produced a value")
  public int produced;

  @Schema(description = "Number of existing values the rule reproduces exactly")
  public int equal;

  @Schema(description = "Number of existing values the rule computes differently or not at all")
  public int differing;

  @Schema(description = "Number of securities for which no line matched or a placeholder stayed empty")
  public int noLine;

  @Schema(description = "Number of securities for which a matching line computed a value that failed the validation")
  public int invalid;

  @Schema(description = "Up to 20 existing values the rule does not reproduce, ordered by security name")
  public List<UDFFillMismatch> mismatches = new ArrayList<>();

  @Schema(description = "Up to 20 values the rule computes for securities without a value, ordered by security name")
  public List<UDFFillSample> samples = new ArrayList<>();

  @Schema(description = "An existing value of a security which the rule does not reproduce")
  public record UDFFillMismatch(@Schema(description = "Name of the security") String name,
      @Schema(description = "The value stored in the field") String existing,
      @Schema(description = "The value the rule computes, or null when it computes none") String computed,
      @Schema(description = "Why no value was computed, null when one was") String reason) {
  }

  @Schema(description = "A value the rule computes for a security whose field is still empty")
  public record UDFFillSample(@Schema(description = "Name of the security") String name,
      @Schema(description = "The value the rule computes") String computed) {
  }
}
