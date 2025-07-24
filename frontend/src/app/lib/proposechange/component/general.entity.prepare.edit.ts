import {EntityMapping, PrepareCallParam, ProposeChangeable} from '../../../shared/changerequest/request.for.you.table.component';
import {BasePrepareEdit} from './base.prepare.edit';

export class GeneralEntityPrepareEdit extends BasePrepareEdit implements PrepareCallParam {

  constructor(private type: new() => ProposeChangeable) {
    super();
  }

  prepareForEditEntity(entity: ProposeChangeable, entityMapping: EntityMapping): void {
    entityMapping.callParam = new this.type();
    Object.assign(entityMapping.callParam, entity);
    entityMapping.visibleDialog = true;
  }
}
