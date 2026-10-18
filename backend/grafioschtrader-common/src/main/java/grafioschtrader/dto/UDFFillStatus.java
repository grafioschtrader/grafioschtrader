package grafioschtrader.dto;

import java.util.EnumMap;
import java.util.Map;

import grafioschtrader.types.UDFFillState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Summary of the automatic filling of one user-defined security field: how many cells of the tenant scope are
    automatic, locked, manual or without a value from the source, and whether a fill run is pending.""")
public class UDFFillStatus {

  @Schema(description = "Number of securities in the tenant scope of the owner that match the asset classes of the field")
  public int inScope;

  @Schema(description = """
      Number of in-scope cells per fill state. An APPLIED cell is counted only while it still holds the filled value;
      otherwise it is manual.""")
  public Map<UDFFillState, Integer> countByState = new EnumMap<>(UDFFillState.class);

  @Schema(description = "Number of in-scope cells with a value that no fill row describes as automatic or locked")
  public int manual;

  @Schema(description = "True when a fill run for this field is waiting in the task queue")
  public boolean waiting;

  @Schema(description = "True when a fill run for this field is executing now")
  public boolean running;
}
