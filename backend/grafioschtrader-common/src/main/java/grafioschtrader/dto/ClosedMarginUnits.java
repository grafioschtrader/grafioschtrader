package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description= """
        Response to the query whether an opening margin position already has a position that refers to it. And the return of units that have not yet been closed. 
        Can be used to avoid changing the deposit of an existing opening margin position.""")
public class ClosedMarginUnits {
  @Schema(description = "Indicates whether there are any transactions associated with this open margin position")
  public boolean hasPosition;
  @Schema(description = "Specification of units that have not yet been closed. This would allow the editing of units to be validated at the opening position.")
  public Double closedUnits;

  public ClosedMarginUnits(boolean hasPosition, Double closedUnits) {
    this.hasPosition = hasPosition;
    this.closedUnits = closedUnits;
  }

}
