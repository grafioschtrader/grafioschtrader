package grafioschtrader.entities;

import static javax.persistence.InheritanceType.JOINED;

import java.util.List;
import java.util.Objects;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.types.ProposeDataChangeState;

@Entity
@Table(name = ProposeRequest.TABNAME)
@Inheritance(strategy = JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
public abstract class ProposeRequest extends Auditable {

  public static final String TABNAME = "propose_request";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_propose_request")
  protected Integer idProposeRequest;

  @Column(name = "entity")
  protected String entity;

  @Basic(optional = false)
  @Column(name = "data_change_state")
  protected byte dataChangeState;

  @Column(name = "note_request")
  @Size(max = GlobalConstants.NOTE_SIZE)
  protected String noteRequest;

  @Column(name = "note_accept_reject")
  @Size(max = GlobalConstants.NOTE_SIZE)
  protected String noteAcceptReject;

  @JoinColumn(name = "id_propose_request")
  @OneToMany(fetch = FetchType.EAGER)
  private List<ProposeChangeField> proposeChangeFieldList;

  @JsonIgnore
  @Override
  public Integer getId() {
    return idProposeRequest;
  }

  @Override
  public void setIdProposeRequest(Integer idProposeRequest) {
    this.idProposeRequest = idProposeRequest;
  }

  @Override
  public Integer getIdProposeRequest() {
    return idProposeRequest;
  }

  public String getEntity() {
    return entity;
  }

  public void setEntity(String entity) {
    this.entity = entity;
  }

  public ProposeDataChangeState getDataChangeState() {
    return ProposeDataChangeState.getProposeDataChangeStateByValue(dataChangeState);
  }

  public void setDataChangeState(ProposeDataChangeState proposeDataChangeState) {
    this.dataChangeState = proposeDataChangeState.getValue();
  }

  public String getNoteRequest() {
    return noteRequest;
  }

  public void setNoteRequest(String noteRequest) {
    this.noteRequest = noteRequest;
  }

  public String getNoteAcceptReject() {
    return noteAcceptReject;
  }

  public void setNoteAcceptReject(String noteAcceptReject) {
    this.noteAcceptReject = noteAcceptReject;
  }

  public List<ProposeChangeField> getProposeChangeFieldList() {
    return proposeChangeFieldList;
  }

  public void setProposeChangeFieldList(List<ProposeChangeField> proposeChangeFieldList) {
    this.proposeChangeFieldList = proposeChangeFieldList;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProposeRequest that = (ProposeRequest) o;
    return Objects.equals(idProposeRequest, that.idProposeRequest);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idProposeRequest);
  }

}
