package grafiosch.entities.projection;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        Response indicating whether a change operation was successful with an optional message. 
        Is intended for the return of a message. Possible use cases are the change after a user setting, 
        for example for changing the password.""")
public class SuccessfullyChanged {
  
  @Schema(description = "Indicates whether the change operation was successfully executed")
  public boolean wasChanged;
 
  @Schema(description = "Descriptive message about the change operation result, it should be localized")
  public String message;

  public SuccessfullyChanged(boolean wasChanged, String message) {
    this.wasChanged = wasChanged;
    this.message = message;
  }

}
