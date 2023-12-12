import {Auditable} from './auditable';
import {ProposeChangeField} from './propose.change.field';

export abstract class ProposeRequest extends Auditable {
  entity: string;
  dataChangeState: string;
  noteRequest: string;
  noteAcceptReject: string;
  proposeChangeFieldList: ProposeChangeField[];

  public addProposeChangeField(proposeChangeField: ProposeChangeField) {
    (this.proposeChangeFieldList = (this.proposeChangeFieldList || [])).push(proposeChangeField);
  }
}
