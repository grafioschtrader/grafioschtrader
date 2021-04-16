import {ChangeDetectorRef, Component, OnInit} from '@angular/core';
import {TableConfigBase} from '../datashowbase/table.config.base';
import {Globalparameters} from '../../entities/globalparameters';
import {FilterService, MenuItem} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../service/globalparameter.service';
import {UserSettingsService} from '../service/user.settings.service';
import {IGlobalMenuAttach} from '../mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../help/help.ids';
import {ActivePanelService} from '../mainmenubar/service/active.panel.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../datashowbase/column.config';
import {AppHelper} from '../helper/app.helper';
import {AppSettings} from '../app.settings';
import {AuditHelper} from '../helper/audit.helper';
import {User} from '../../entities/user';
import {ProcessedActionData} from '../types/processed.action.data';
import {ProcessedAction} from '../types/processed.action';
import {TranslateHelper} from '../helper/translate.helper';

/**
 * Shows global settings in table
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">

      <p-table [columns]="fields" [value]="globalparametersList" selectionMode="single"
               [(selection)]="selectedEntity" dataKey="propertyName"
               (sortFunction)="customSort($event)" [customSort]="true"
               styleClass="sticky-table p-datatable-striped p-datatable-gridlines">
        <ng-template pTemplate="caption">
          <h4>{{'GLOBAL_SETTINGS' | translate}}</h4>
        </ng-template>
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th *ngFor="let field of fields" [pSortableColumn]="field.field" [pTooltip]="field.headerTooltipTranslated"
                [style.width.px]="field.width">
              {{field.headerTranslated}}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td *ngFor="let field of fields">
              <ng-container [ngSwitch]="field.templateName">
                <ng-container *ngSwitchCase="'check'">
                  <span><i [ngClass]="{'fa fa-check': getValueByPath(el, field)}" aria-hidden="true"></i></span>
                </ng-container>
                <ng-container *ngSwitchDefault>
                  {{getValueByPath(el, field)}}
                </ng-container>
              </ng-container>
            </td>
          </tr>
        </ng-template>
      </p-table>
      <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"
                     appendTo="body"></p-contextMenu>
    </div>
    <globalsettings-edit *ngIf="visibleDialog"
                         [visibleDialog]="visibleDialog"
                         [globalparameters]="selectedEntity"
                         (closeDialog)="handleGlobalparameterCloseDialog($event)">
    </globalsettings-edit>
  `
})
export class GlobalSettingsTableComponent extends TableConfigBase implements OnInit, IGlobalMenuAttach {
  contextMenuItems: MenuItem[] = [];
  editMenu: MenuItem;
  globalparametersList: Globalparameters[];
  selectedEntity: Globalparameters;
  callParam: User;
  visibleDialog = false;

  constructor(private activePanelService: ActivePanelService,
              changeDetectionStrategy: ChangeDetectorRef,
              filterService: FilterService,
              translateService: TranslateService,
              globalparameterService: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(changeDetectionStrategy, filterService, usersettingsService, translateService, globalparameterService);

    this.addColumn(DataType.String, 'propertyName', 'PROPERTY_NAME_DESC', true, false,
      {translateValues: TranslateValue.NORMAL, width: 450});
    this.addColumnFeqH(DataType.String, 'propertyValue', true, false,
      {fieldValueFN: this.getProperty.bind(this)});
    this.addColumnFeqH(DataType.Boolean, 'changedBySystem', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.String, 'propertyName', true, false,
      {width: 200});
    this.editMenu = { label: 'EDIT_RECORD|GLOBAL_SETTINGS' + AppSettings.DIALOG_MENU_SUFFIX,
      command: (event) => this.handleEditEntity(this.selectedEntity),
      disabled: !AuditHelper.hasAdminRole(this.globalparameterService)};
    TranslateHelper.translateMenuItems([this.editMenu], translateService);
  }

  ngOnInit(): void {
    this.readData();
  }

  getProperty(entity: Globalparameters, field: ColumnConfig): string {
    if (entity.propertyDate) {
      return AppHelper.getDateByFormat(this.globalparameterService, entity.propertyDate);
    } else if (entity.propertyInt) {
      return AppHelper.numberIntegerFormat(this.globalparameterService, entity.propertyInt);
    } else {
      return entity.propertyString;
    }
  }

  readData(): void {
    this.globalparameterService.getAllGlobalparameters().subscribe(globalparametersList => {
      this.globalparametersList = globalparametersList;
      this.prepareTableAndTranslate();
      this.createTranslatedValueStoreAndFilterField(globalparametersList);
    });
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    this.resetMenu();
  }

  private resetMenu(): void {
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptions(),
      editMenu: this.getEditMenu()
    });
  }

  protected getEditMenu(): MenuItem[] {
    this.contextMenuItems = [];
    if (this.selectedEntity) {
      this.contextMenuItems.push(this.editMenu);
    }
    return this.contextMenuItems;
  }

  handleEditEntity(globalparameters: Globalparameters): void {
    this.visibleDialog = true;
  }

  handleGlobalparameterCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): HelpIds {
    return HelpIds.HELP_GLOBAL_SETTINGS;
  }

}
