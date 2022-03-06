import {CallParam} from '../../shared/maintree/types/dialog.visible';

import {EntityMapping, PrepareCallParam} from './request.for.you.table.component';
import {ImportTransactionPlatform} from '../../entities/import.transaction.platform';
import {ImportTransactionPlatformService} from '../../imptranstemplate/service/import.transaction.platform.service';
import {IPlatformTransactionImport} from '../../portfolio/component/iplatform.transaction.import';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {BasePrepareEdit} from './base.prepare.edit';

export class ImportTransactionPlatformPrepareEdit extends BasePrepareEdit implements PrepareCallParam {

  constructor(private importTransactionPlatformService: ImportTransactionPlatformService) {
    super();
  }

  prepareForEditEntity(entity: ImportTransactionPlatform, entityMapping: EntityMapping): void {
    this.importTransactionPlatformService.getPlatformTransactionImport().subscribe(
      (platformTransactionImports: IPlatformTransactionImport[]) => {
        entityMapping.option =
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('id', 'readableName', platformTransactionImports, true);
        entityMapping.callParam = new CallParam(null, entity);
        entityMapping.visibleDialog = true;
      });
  }

}
