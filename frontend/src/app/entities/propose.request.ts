import {Auditable} from './auditable';
import {ProposeChangeField} from './propose.change.field';

export abstract class ProposeRequest extends Auditable {
  entity: string;
  idProposeRequest: number;
  dataChangeState: string;
  noteRequest: string;
  noteAcceptReject: string;
  proposeChangeFieldList: ProposeChangeField[];

  public getId() {
    return this.idProposeRequest;
  }

  public addProposeChangeField(proposeChangeField: ProposeChangeField) {
    (this.proposeChangeFieldList = (this.proposeChangeFieldList || [])).push(proposeChangeField);
  }
}
