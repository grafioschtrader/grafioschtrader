package grafioschtrader.dto;

import javax.validation.constraints.Size;

import grafioschtrader.GlobalConstants;

public abstract class DeleteAndCreateMultiple {
  public Integer idSecuritycurrency;

  @Size(max = GlobalConstants.FID_MAX_LETTERS)
  public String noteRequest;

  public String getNoteRequest() {
    return noteRequest;
  }

  public void setNoteRequest(String noteRequest) {
    this.noteRequest = noteRequest;
  }

}
