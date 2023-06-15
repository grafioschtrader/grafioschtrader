import {PrepareCallParam} from './request.for.you.table.component';
import {Security} from '../../entities/security';
import {GeneralEntityPrepareEdit} from './general.entity.prepare.edit';


export class SecurityPrepareEdit extends GeneralEntityPrepareEdit implements PrepareCallParam {
  constructor(private derivedEntityMapping: string) {
    super(Security);
  }

  redirectEntityMapping(proposedEntity: Security): string {
    return proposedEntity.idLinkSecuritycurrency ? this.derivedEntityMapping : null;
  }

}
