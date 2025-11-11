import {AssetclassService} from '../../assetclass/service/assetclass.service';
import {Assetclass} from '../../entities/assetclass';
import {AssetclassCallParam} from '../../assetclass/component/assetclass.call.param';
import {combineLatest} from 'rxjs';
import {BasePrepareEdit} from '../../lib/proposechange/component/base.prepare.edit';
import {EntityMapping, PrepareCallParam} from '../../lib/proposechange/component/general.entity.prepare.edit';

/**
 * Preparation handler for Assetclass entities in the propose change workflow.
 * Provides custom preparation logic that loads related data (all asset classes, security associations)
 * needed for the asset class edit dialog.
 */
export class AssetclassPrepareEdit extends BasePrepareEdit<Assetclass> implements PrepareCallParam<Assetclass> {
  /**
   * Creates an asset class preparation handler.
   *
   * @param assetclassService - Service for loading asset class data
   */
  constructor(private assetclassService: AssetclassService) {
    super();
  }

  /**
   * Prepares an asset class for editing by loading related data asynchronously.
   * Fetches all asset classes (for parent selection) and checks if this asset class
   * has associated securities (affects editability of certain fields).
   *
   * @param entity - The asset class to prepare for editing
   * @param entityMapping - Container for dialog state and parameters to be populated
   */
  prepareForEditEntity(entity: Assetclass, entityMapping: EntityMapping): void {
    const observableAllAssetclasses = this.assetclassService.getAllAssetclass();
    const observableAssetclassHasSecurity = this.assetclassService.assetclassHasSecurity(entity.idAssetClass);

    combineLatest([observableAllAssetclasses, observableAssetclassHasSecurity]).subscribe(data => {
        entityMapping.callParam = new AssetclassCallParam();
        entityMapping.callParam.assetclass = new Assetclass();
        Object.assign(entityMapping.callParam.assetclass, entity);
        entityMapping.callParam.setSuggestionsArrayOfAssetclassList(data[0]);
        entityMapping.callParam.hasSecurity = data[1];
        entityMapping.visibleDialog = true;
      }
    );
  }
}
