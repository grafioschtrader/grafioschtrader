import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TableCrudSupportMenu, CrudMenuOptions } from '../../datashowbase/table.crud.support.menu';
import { ProposeChangeEntity } from '../../entities/propose.change.entity';
import { DataType } from '../../dynamic-form/models/data.type';
import { ProposeChangeEntityService } from '../service/propose.change.entity.service';
import { plainToInstance } from 'class-transformer';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { TranslateValue } from '../../datashowbase/column.config';
import { BaseSettings } from '../../base.settings';
import { TranslateModule } from '@ngx-translate/core';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';

/**
 * Shows your created change request on entities in a table. The only available operation is to delete a request.
 */
@Component({
  template: `
    <configurable-table
      (componentClick)="onComponentClick($event)"
      [data]="entityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="entityKeyName"
      [contextMenuAppendTo]="'body'"
      [(selection)]="selectedEntity"
      [contextMenuItems]="contextMenuItems()"
      [showContextMenu]="isActivated()"
      [valueGetterFn]="valueGetter"
      [containerClass]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <h4 caption>{{ 'YOUR_CHANGE_REQUESTS' | translate }}</h4>
    </configurable-table>
  `,
  providers: [DialogService],
  imports: [TranslateModule, ConfigurableTableComponent]
})
export class YourProposalTableComponent extends TableCrudSupportMenu<ProposeChangeEntity> {
  private proposeChangeEntityService: ProposeChangeEntityService = inject(ProposeChangeEntityService);

  constructor() {
    super(BaseSettings.PROPOSE_CHANGE_ENTITY, inject(ProposeChangeEntityService), [CrudMenuOptions.Allow_Delete]);

    this.addColumnFeqH(DataType.String, 'entity', true, false, {
      translateValues: TranslateValue.UPPER_CASE
    });
    this.addColumnFeqH(DataType.String, 'noteRequest', true, false);
    this.addColumnFeqH(DataType.String, 'dataChangeState', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'noteAcceptReject', 'PROPOSEACCEPTREJECT', true, false);
    this.prepareTableAndTranslate();
  }

  readData(): void {
    this.proposeChangeEntityService
      .getProposeChangeEntityListByCreatedBy()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((proposeChangeEntityList) => {
        this.createTranslatedValueStoreAndFilterField(proposeChangeEntityList);
        this.entityList.set(plainToInstance(ProposeChangeEntity, proposeChangeEntityList));
        this.refreshSelectedEntity();
      });
  }

  prepareCallParam(entity: ProposeChangeEntity): void {}
}
