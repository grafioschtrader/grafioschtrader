package grafioschtrader.dto;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public abstract class DeleteAndCreateMultiple {
  public Integer idSecuritycurrency;

  @Schema(description = "An unauthorized user cannot make changes to this data directly. He leaves a message to the user with sufficient rights for this change.")
  @Size(max = BaseConstants.FID_MAX_LETTERS)
  private String noteRequest;

  public String getNoteRequest() {
    return noteRequest;
  }

  public void setNoteRequest(String noteRequest) {
    this.noteRequest = noteRequest;
  }

}
