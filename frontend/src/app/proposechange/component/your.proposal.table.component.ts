import {Component} from '@angular/core';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../shared/datashowbase/table.crud.support.menu';
import {ProposeChangeEntity} from '../../entities/propose.change.entity';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {AssetclassService} from '../../assetclass/service/assetclass.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {TranslateService} from '@ngx-translate/core';
import {ProposeChangeEntityService} from '../service/propose.change.entity.service';
import {plainToClass} from 'class-transformer';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService} from 'primeng/api';
import {TranslateValue} from '../../shared/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';


/**
 * Shows your created change request on entities in a table. The only available operation is to delete a request.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">

      <p-table [columns]="fields" [value]="entityList" selectionMode="single" [(selection)]="selectedEntity"
               [dataKey]="entityKeyName" responsiveLayout="scroll"
               styleClass="sticky-table p-datatable-striped p-datatable-gridlines">
        <ng-template pTemplate="caption">
          <h4>{{'YOUR_CHANGE_REQUESTS' | translate}}</h4>
        </ng-template>
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th *ngFor="let field of fields" [pSortableColumn]="field.field" [pTooltip]="field.headerTooltipTranslated">
              {{field.headerTranslated}}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td *ngFor="let field of fields">
              {{getValueByPath(el, field)}}
            </td>
          </tr>
        </ng-template>
      </p-table>
      <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"
                     appendTo="body"></p-contextMenu>
    </div>
  `,
  providers: [DialogService]
})
export class YourProposalTableComponent extends TableCrudSupportMenu<ProposeChangeEntity> {

  constructor(private assetclassService: AssetclassService,
              private proposeChangeEntityService: ProposeChangeEntityService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              dialogService: DialogService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(AppSettings.PROPOSE_CHANGE_ENTITY, proposeChangeEntityService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService, [CrudMenuOptions.Allow_Delete]);

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
      this.entityList = plainToClass(ProposeChangeEntity, proposeChangeEntityList);
    });
  }

  prepareCallParam(entity: ProposeChangeEntity): void {
  }
}
