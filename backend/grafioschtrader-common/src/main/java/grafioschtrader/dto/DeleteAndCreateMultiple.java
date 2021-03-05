package grafioschtrader.dto;

import javax.validation.constraints.Size;

import grafioschtrader.GlobalConstants;

public abstract class DeleteAndCreateMultiple {
  public Integer idSecuritycurrency;

  @Size(max = GlobalConstants.NOTE_SIZE)
  public String noteRequest;

  public String getNoteRequest() {
    return noteRequest;
  }

  public void setNoteRequest(String noteRequest) {
    this.noteRequest = noteRequest;
  }

}
