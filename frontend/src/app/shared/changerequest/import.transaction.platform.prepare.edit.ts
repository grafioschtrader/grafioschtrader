import {CallParam} from '../maintree/types/dialog.visible';
import {ImportTransactionPlatform} from '../../entities/import.transaction.platform';
import {ImportTransactionPlatformService} from '../../imptranstemplate/service/import.transaction.platform.service';
import {IPlatformTransactionImport} from '../../portfolio/component/iplatform.transaction.import';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {BasePrepareEdit} from '../../lib/proposechange/component/base.prepare.edit';
import {EntityMapping, PrepareCallParam} from '../../lib/proposechange/component/general.entity.prepare.edit';

/**
 * Preparation handler for ImportTransactionPlatform entities in the propose change workflow.
 * Loads available platform transaction import types to populate selection options
 * in the edit dialog.
 */
export class ImportTransactionPlatformPrepareEdit extends BasePrepareEdit<ImportTransactionPlatform> implements PrepareCallParam<ImportTransactionPlatform> {

  /**
   * Creates an import transaction platform preparation handler.
   *
   * @param importTransactionPlatformService - Service for loading platform import data
   */
  constructor(private importTransactionPlatformService: ImportTransactionPlatformService) {
    super();
  }

  /**
   * Prepares an import transaction platform for editing by loading available platform types.
   * Fetches the list of registered transaction import platform implementations and creates
   * dropdown options for platform selection.
   *
   * @param entity - The import transaction platform to prepare for editing
   * @param entityMapping - Container for dialog state and parameters to be populated
   * @returns Observable that completes when platform types are loaded
   */
  prepareForEditEntity(entity: ImportTransactionPlatform, entityMapping: EntityMapping): Observable<void> {
    return this.importTransactionPlatformService.getPlatformTransactionImport().pipe(
      map((platformTransactionImports: IPlatformTransactionImport[]) => {
        entityMapping.option =
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('id', 'readableName', platformTransactionImports, true);
        entityMapping.callParam = new CallParam(null, entity);
        entityMapping.visibleDialog = true;
      })
    );
  }
}
