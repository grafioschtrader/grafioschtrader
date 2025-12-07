import {Component, OnInit} from '@angular/core';

import {TableConfigBase} from '../datashowbase/table.config.base';
import {Globalparameters} from '../entities/globalparameters';
import {FilterService, MenuItem} from 'primeng/api';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {Textarea} from 'primeng/textarea';
import {ConfigurableTableComponent} from '../datashowbase/configurable-table.component';
import {GlobalSettingsEditComponent} from './global.settings-edit.component';
import {GlobalparameterService} from '../services/globalparameter.service';
import {UserSettingsService} from '../services/user.settings.service';
import {IGlobalMenuAttach} from '../mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../mainmenubar/service/active.panel.service';
import {DataType} from '../dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../datashowbase/column.config';
import {AppHelper} from '../helper/app.helper';
import {AuditHelper} from '../helper/audit.helper';
import {User} from '../entities/user';
import {ProcessedActionData} from '../types/processed.action.data';
import {ProcessedAction} from '../types/processed.action';
import {TranslateHelper} from '../helper/translate.helper';
import {BaseSettings} from '../base.settings';
import {HelpIds} from '../help/help.ids';

/**
 * Shows global settings in table. Here TableCrudSupportMenu is not derived because no entities can be deleted or added.
 */
@Component({
  template: `
    <configurable-table
      [data]="globalparametersList"
      [fields]="fields"
      [dataKey]="'propertyName'"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSort.bind(this)"
      [scrollable]="false"
      [stripedRows]="true"
      [showGridlines]="true"
      [expandable]="true"
      [canExpandFn]="canExpandRow.bind(this)"
      [expandedRowTemplate]="expandedContent"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [showContextMenu]="!!contextMenuItems && contextMenuItems.length > 0"
      [contextMenuItems]="contextMenuItems"
      [valueGetterFn]="getValueByPath.bind(this)"
      (componentClick)="onComponentClick($event)">

      <h4 caption>{{ 'GLOBAL_SETTINGS' | translate }}</h4>

    </configurable-table>

    <!-- Expanded row content template for property blob text -->
    <ng-template #expandedContent let-gp>
      <textarea [rows]="10" pTextarea style="width: 100%;"
                readonly="true">{{ gp.propertyBlobAsText }}</textarea>
    </ng-template>

    @if (visibleDialog) {
      <globalsettings-edit [visibleDialog]="visibleDialog"
                           [globalparameters]="selectedEntity"
                           (closeDialog)="handleGlobalparameterCloseDialog($event)">
      </globalsettings-edit>
    }
  `,
  standalone: true,
  imports: [ConfigurableTableComponent, Textarea, TranslateModule, GlobalSettingsEditComponent]
})
export class GlobalSettingsTableComponent extends TableConfigBase implements OnInit, IGlobalMenuAttach {

  contextMenuItems: MenuItem[] = [];
  editMenu: MenuItem;
  globalparametersList: Globalparameters[];
  selectedEntity: Globalparameters;
  callParam: User;
  visibleDialog = false;
  private readonly PROPERTY_NAME = 'propertyName';

  constructor(private activePanelService: ActivePanelService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);

    this.addColumn(DataType.String, this.PROPERTY_NAME, 'PROPERTY_NAME_DESC', true, false,
      {translateValues: TranslateValue.NORMAL, width: 450});
    this.addColumnFeqH(DataType.String, 'propertyValue', true, false,
      {fieldValueFN: this.getProperty.bind(this)});
    this.addColumnFeqH(DataType.Boolean, 'changedBySystem', true, false,
      {templateName: 'check'});
    this.addColumn(DataType.String, this.PROPERTY_NAME + '1', TranslateHelper.camelToUnderscoreCase(this.PROPERTY_NAME), true, false,
      {width: 200, fieldValueFN: this.getPropertyName1.bind(this)});
    this.editMenu = {
      label: 'EDIT_RECORD|GLOBAL_SETTINGS' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: (event) => this.handleEditEntity(this.selectedEntity),
      disabled: !AuditHelper.hasAdminRole(this.gps)
    };
    TranslateHelper.translateMenuItems([this.editMenu], translateService);
  }

  ngOnInit(): void {
    this.readData();
  }

  getProperty(entity: Globalparameters, field: ColumnConfig): string {
    if (entity.propertyDate) {
      return AppHelper.getDateByFormat(this.gps, entity.propertyDate);
    } else if (entity.propertyInt != null) {
      return AppHelper.numberIntegerFormat(this.gps, entity.propertyInt);
    } else {
      return entity.propertyString;
    }
  }

  /**
   * Is needed for the difference to the translated property name, otherwise sorting is not working
   */
  getPropertyName1(dataobject: any, field: ColumnConfig, valueField: any): any {
    return dataobject[this.PROPERTY_NAME];
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  canExpandRow(gp: Globalparameters): boolean {
    return !!gp.propertyBlobAsText;
  }

  onComponentClick(event): void {
    this.resetMenu(this.selectedEntity);
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

  getHelpContextId(): string {
    return HelpIds.HELP_GLOBAL_SETTINGS;
  }

  protected getEditMenu(): MenuItem[] {
    this.contextMenuItems = [];
    if (this.selectedEntity) {
      this.contextMenuItems.push(this.editMenu);
    }
    return this.contextMenuItems;
  }

  private readData(): void {
    (this.gps as any).getAllGlobalparameters().subscribe(globalparametersList => {
      this.globalparametersList = globalparametersList;
      this.prepareTableAndTranslate();
      this.createTranslatedValueStoreAndFilterField(globalparametersList);
      if (this.selectedEntity) {
        this.resetMenu(this.globalparametersList.find(gp => gp.propertyName === this.selectedEntity.propertyName));
      }
    });
  }

  private resetMenu(gp: Globalparameters): void {
    this.selectedEntity = gp;
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptions(),
      editMenu: this.getEditMenu()
    });
  }

}
