import type { PanelClickEvent } from '../mainmenubar/component/iglobal.menu.attach';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { TableConfigBase } from '../datashowbase/table.config.base';
import { Globalparameters } from '../entities/globalparameters';
import { MenuItem } from '@openng/optimus-ui/api';
import { TranslateModule } from '@ngx-translate/core';
import { Textarea } from '@openng/optimus-ui/textarea';
import { ConfigurableTableComponent } from '../datashowbase/configurable-table.component';
import { GlobalSettingsEditComponent } from './global.settings-edit.component';
import { IGlobalMenuAttach } from '../mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../mainmenubar/service/active.panel.service';
import { DataType } from '../dynamic-form/models/data.type';
import { ColumnConfig, TranslateValue } from '../datashowbase/column.config';
import { AppHelper } from '../helper/app.helper';
import { AuditHelper } from '../helper/audit.helper';
import { ProcessedActionData } from '../types/processed.action.data';
import { ProcessedAction } from '../types/processed.action';
import { TranslateHelper } from '../helper/translate.helper';
import { BaseSettings } from '../base.settings';
import { HelpIds } from '../help/help.ids';

/**
 * Shows global settings in table. Here TableCrudSupportMenu is not derived because no entities can be deleted or added.
 */
@Component({
  template: `
    <configurable-table
      [data]="globalparametersList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="'propertyName'"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSorter"
      [scrollable]="false"
      [stripedRows]="true"
      [showGridlines]="true"
      [expandable]="true"
      [canExpandFn]="canExpandRow"
      [expandedRowTemplate]="expandedContent"
      [containerClass]="{
        'data-container': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [showContextMenu]="contextMenuItems().length > 0"
      [contextMenuItems]="contextMenuItems()"
      [valueGetterFn]="valueGetter"
      (componentClick)="onComponentClick($event)">
      <h4 caption>{{ 'GLOBAL_SETTINGS' | translate }}</h4>
    </configurable-table>

    <!-- Expanded row content template for property blob text -->
    <ng-template #expandedContent let-gp>
      <textarea [rows]="10" pTextarea style="width: 100%;" readonly="true">{{ gp.propertyBlobAsText }}</textarea>
    </ng-template>

    @if (visibleDialog()) {
      @if (selectedEntity(); as parameter) {
        <globalsettings-edit
          [visibleDialog]="visibleDialog()"
          [globalparameters]="parameter"
          (closeDialog)="handleGlobalparameterCloseDialog($event)" />
      }
    }
  `,
  imports: [ConfigurableTableComponent, Textarea, TranslateModule, GlobalSettingsEditComponent]
})
export class GlobalSettingsTableComponent extends TableConfigBase implements OnInit, IGlobalMenuAttach {
  private activePanelService: ActivePanelService = inject(ActivePanelService);

  readonly contextMenuItems = signal<MenuItem[]>([]);
  editMenu: MenuItem;
  readonly globalparametersList = signal<Globalparameters[]>([]);
  readonly selectedEntity = signal<Globalparameters | null>(null);
  readonly visibleDialog = signal(false);
  private readonly destroyRef = inject(DestroyRef);

  /** Only a parameter with a text blob has something to expand */
  readonly canExpandRow = (gp: Globalparameters): boolean => !!gp.propertyBlobAsText;
  private readonly PROPERTY_NAME = 'propertyName';

  constructor() {
    super();

    this.addColumn(DataType.String, this.PROPERTY_NAME, 'PROPERTY_NAME_DESC', true, false, {
      translateValues: TranslateValue.NORMAL,
      width: 450
    });
    this.addColumnFeqH(DataType.String, 'propertyValue', true, false, {
      fieldValueFN: this.getProperty.bind(this)
    });
    this.addColumnFeqH(DataType.Boolean, 'changedBySystem', true, false, {
      templateName: 'check'
    });
    this.addColumn(
      DataType.String,
      this.PROPERTY_NAME + '1',
      TranslateHelper.camelToUnderscoreCase(this.PROPERTY_NAME),
      true,
      false,
      { width: 200, fieldValueFN: this.getPropertyName1.bind(this) }
    );
    this.editMenu = {
      label: 'EDIT_RECORD|GLOBAL_SETTINGS' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: (event) => this.handleEditEntity(this.selectedEntity()),
      disabled: !AuditHelper.hasAdminRole(this.gps)
    };
    TranslateHelper.translateMenuItems([this.editMenu], this.translateService);
  }

  ngOnInit(): void {
    this.readData();
  }

  getProperty(entity: Globalparameters, field: ColumnConfig): string | null {
    if (entity.propertyDateTime) {
      return entity.propertyDateTime;
    } else if (entity.propertyDate) {
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

  onComponentClick(event: PanelClickEvent): void {
    this.resetMenu(this.selectedEntity());
  }

  handleEditEntity(globalparameters: Globalparameters | null): void {
    if (!globalparameters || globalparameters.propertyName == null) return;
    this.visibleDialog.set(true);
  }

  handleGlobalparameterCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_GLOBAL_SETTINGS;
  }

  protected getEditMenu(): MenuItem[] {
    const menuItems = this.selectedEntity() ? [this.editMenu] : [];
    this.contextMenuItems.set(menuItems);
    return menuItems;
  }

  private readData(): void {
    (this.gps as any)
      .getAllGlobalparameters()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((globalparametersList: Globalparameters[]) => {
        this.prepareTableAndTranslate();
        this.createTranslatedValueStoreAndFilterField(globalparametersList);
        this.globalparametersList.set(globalparametersList);
        const selected = this.selectedEntity();
        if (selected) {
          this.resetMenu(globalparametersList.find((gp) => gp.propertyName === selected.propertyName));
        }
      });
  }

  private resetMenu(gp: Globalparameters | null | undefined): void {
    this.selectedEntity.set(gp ?? null);
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptions(),
      editMenu: this.getEditMenu()
    });
  }
}
