import {Component} from '@angular/core';

import {ImportTransactionTemplate} from '../../entities/import.transaction.template';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {ImportTransactionTemplateService} from '../service/import.transaction.template.service';
import {ParentChildRowSelection} from '../../lib/datashowbase/parent.child.row.selection';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {ImportTransactionPlatform} from '../../entities/import.transaction.platform';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {plainToClass} from 'class-transformer';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {combineLatest} from 'rxjs';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';
import {BaseSettings} from '../../lib/base.settings';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {ImportTransactionEditTemplateComponent} from './import-transaction-edit-template.component';

/**
 * Component for import transaction template, one row one template. This table is controlled by a master data selection view.
 */
@Component({
  selector: 'import-transaction-template-table',
  template: `
    <configurable-table
      [data]="entityList"
      [fields]="fields"
      [dataKey]="entityKeyName"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSort.bind(this)"
      [scrollHeight]="'flex'"
      [scrollable]="true"
      [stripedRows]="true"
      [showGridlines]="true"
      [containerClass]="{'data-container': true}"
      [showContextMenu]="isActivated()"
      [contextMenuItems]="contextMenuItems"
      [ownerHighlightFn]="isNotSingleModeAndOwner.bind(this)"
      [valueGetterFn]="getValueByPath.bind(this)"
      (componentClick)="onComponentClick($event)">

      <h4 caption>{{ entityNameUpper | translate }}</h4>

    </configurable-table>
    @if (visibleDialog) {
      <import-transaction-edit-template
        [visibleDialog]="visibleDialog"
        [callParam]="callParam"
        (closeDialog)="handleCloseDialog($event)">
      </import-transaction-edit-template>
    }
  `,
  providers: [DialogService],
  standalone: true,
  imports: [TranslateModule, ConfigurableTableComponent, ImportTransactionEditTemplateComponent]
})
export class ImportTransactionTemplateTableComponent extends TableCrudSupportMenu<ImportTransactionTemplate> {
  callParam: CallParam;

  // Parent selected entity
  selectImportTransactionPlatform: ImportTransactionPlatform;

  parentChildRowSelection: ParentChildRowSelection<ImportTransactionTemplate>;
  private languageAsKeyValue: { [key: string]: string } = {};

  constructor(private importTransactionTemplateService: ImportTransactionTemplateService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(AppSettings.IMPORT_TRANSACTION_TEMPLATE, importTransactionTemplateService, confirmationService,
      messageToastService, activePanelService, dialogService, filterService, translateService, gps,
      usersettingsService, [CrudMenuOptions.ParentControl, ...TableCrudSupportMenu.ALLOW_ALL_CRUD_OPERATIONS]);

    this.addColumnFeqH(DataType.String, 'templatePurpose', true, false, {templateName: BaseSettings.OWNER_TEMPLATE});
    this.addColumnFeqH(DataType.String, 'templateCategory', true, false, {translateValues: TranslateValue.NORMAL});

    this.addColumnFeqH(DataType.String, 'templateFormatType', true, false, {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.DateString, 'validSince', true, false);
    this.addColumnFeqH(DataType.String, 'templateLanguage', true, false, {fieldValueFN: this.getDisplayNameForLanguage.bind(this)});
    this.multiSortMeta.push({field: 'templatePurpose', order: 1});
    this.prepareTableAndTranslate();
  }

  getDisplayNameForLanguage(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.languageAsKeyValue[dataobject['templateLanguage']];
  }

  parentSelectionChanged(seclectImportTransactionPlatform: ImportTransactionPlatform,
    parentChildRowSelection: ParentChildRowSelection<ImportTransactionTemplate>) {
    this.selectImportTransactionPlatform = seclectImportTransactionPlatform;
    this.parentChildRowSelection = parentChildRowSelection;
    this.readData();
  }

  override readData(): void {
    if (this.selectImportTransactionPlatform) {
      combineLatest([
        this.importTransactionTemplateService.getImportTransactionPlatformByPlatform(
          this.selectImportTransactionPlatform.idTransactionImportPlatform,
          false
        ),
        this.importTransactionTemplateService.getPossibleLanguagesForTemplate()
      ]).subscribe(([templates, languages]: [ImportTransactionTemplate[], any[]]) => {
        this.createTranslatedValueStoreAndFilterField(templates);
        this.entityList = plainToClass(ImportTransactionTemplate, templates);
        languages.forEach((o: any) => {
          this.languageAsKeyValue.key = o.key as string;
          this.languageAsKeyValue[o.key as string] = o.value;
        });
        this.refreshSelectedEntity();
        this.parentChildRowSelection?.rowSelectionChanged(this.entityList, this.selectedEntity);
      });
    } else {
      this.entityList = [];
      this.parentChildRowSelection?.rowSelectionChanged(this.entityList, this.selectedEntity);
    }
  }

  override prepareCallParam(entity: ImportTransactionTemplate) {
    this.callParam = new CallParam(this.selectImportTransactionPlatform, entity);
  }

  public override prepareEditMenu(): MenuItem[] {
    let menuItems: MenuItem[] = [];
    menuItems.push({separator: true});
    if (this.selectImportTransactionPlatform) {
      menuItems = menuItems.concat(super.prepareEditMenu(this.selectedEntity));
    }
    return menuItems;
  }

  protected override hasRightsForDeleteEntity(entity: ImportTransactionTemplate): boolean {
    return super.hasRightsForDeleteEntity(entity);
  }
}
