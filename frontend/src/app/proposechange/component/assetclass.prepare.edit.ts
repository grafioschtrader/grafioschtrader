import {AssetclassService} from '../../assetclass/service/assetclass.service';
import {Assetclass} from '../../entities/assetclass';
import {EntityMapping, PrepareCallParam} from './request.for.you.table.component';
import {AssetclassCallParam} from '../../assetclass/component/assetclass.call.param';
import {combineLatest} from 'rxjs';
import {BasePrepareEdit} from './base.prepare.edit';

export class AssetclassPrepareEdit extends BasePrepareEdit implements PrepareCallParam {
  constructor(private assetclassService: AssetclassService) {
    super();
  }

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
