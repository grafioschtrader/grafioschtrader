package grafioschtrader.entities;

import jakarta.persistence.Transient;

/**
 * An entity which stands for public data can not directly edited by everybody.
 * In this case a proposal is used and referenced by this class.
 *
 * @author Hugo Graf
 *
 */
public abstract class ProposeTransientTransfer extends BaseID {

  @Transient
  private Integer idProposeRequest;

  @Transient
  private String noteRequestOrReject;

  public String getNoteRequestOrReject() {
    return noteRequestOrReject;
  }

  public void setNoteRequestOrReject(String noteRequestOrReject) {
    this.noteRequestOrReject = noteRequestOrReject;
  }

  public Integer getIdProposeRequest() {
    return idProposeRequest;
  }

  public void setIdProposeRequest(Integer idProposeRequest) {
    this.idProposeRequest = idProposeRequest;
  }

}
