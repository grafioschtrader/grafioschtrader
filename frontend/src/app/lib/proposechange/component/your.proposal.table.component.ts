import {Component, Injector} from '@angular/core';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../datashowbase/table.crud.support.menu';
import {ProposeChangeEntity} from '../../entities/propose.change.entity';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {MessageToastService} from '../../message/message.toast.service';
import {UserSettingsService} from '../../services/user.settings.service';
import {TranslateService} from '@ngx-translate/core';
import {ProposeChangeEntityService} from '../service/propose.change.entity.service';
import {plainToInstance} from 'class-transformer';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService} from 'primeng/api';
import {TranslateValue} from '../../datashowbase/column.config';
import {BaseSettings} from '../../base.settings';
import {TranslateModule} from '@ngx-translate/core';
import {ConfigurableTableComponent} from '../../datashowbase/configurable-table.component';

/**
 * Shows your created change request on entities in a table. The only available operation is to delete a request.
 */
@Component({
  template: `
    <configurable-table
        (componentClick)="onComponentClick($event)"
        [data]="entityList"
        [fields]="fields"
        [dataKey]="entityKeyName"
        [contextMenuAppendTo]="'body'"
        [(selection)]="selectedEntity"
        [contextMenuItems]="contextMenuItems"
        [showContextMenu]="isActivated()"
        [valueGetterFn]="getValueByPath.bind(this)"
        [containerClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <h4 caption>{{ 'YOUR_CHANGE_REQUESTS' | translate }}</h4>
    </configurable-table>
  `,
  providers: [DialogService],
  standalone: true,
  imports: [TranslateModule, ConfigurableTableComponent]
})
export class YourProposalTableComponent extends TableCrudSupportMenu<ProposeChangeEntity> {

  constructor(private proposeChangeEntityService: ProposeChangeEntityService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector) {
    super(BaseSettings.PROPOSE_CHANGE_ENTITY, proposeChangeEntityService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService, injector, [CrudMenuOptions.Allow_Delete]);

    this.addColumnFeqH(DataType.String, 'entity', true, false,
      {translateValues: TranslateValue.UPPER_CASE});
    this.addColumnFeqH(DataType.String, 'noteRequest', true, false);
    this.addColumnFeqH(DataType.String, 'dataChangeState', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'noteAcceptReject', 'PROPOSEACCEPTREJECT', true, false);
    this.prepareTableAndTranslate();
  }

  readData(): void {
    this.proposeChangeEntityService.getProposeChangeEntityListByCreatedBy().subscribe(proposeChangeEntityList => {
      this.createTranslatedValueStoreAndFilterField(proposeChangeEntityList);
      this.entityList = plainToInstance(ProposeChangeEntity, proposeChangeEntityList);
    });
  }

  prepareCallParam(entity: ProposeChangeEntity): void {
  }
}
