import {ChangeDetectorRef, Component} from '@angular/core';
import {ImportTransactionTemplate} from '../../entities/import.transaction.template';
import {DataType} from '../../dynamic-form/models/data.type';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {ImportTransactionTemplateService} from '../service/import.transaction.template.service';
import {ParentChildRowSelection} from '../../shared/datashowbase/parent.child.row.selection';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {ImportTransactionPlatform} from '../../entities/import.transaction.platform';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../shared/datashowbase/table.crud.support.menu';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {plainToClass} from 'class-transformer';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {combineLatest} from 'rxjs';
import {ColumnConfig} from '../../shared/datashowbase/column.config';

/**
 * This table is controlled by a master data selection view.
 * TODO: Change to DatatableCRUDSupportMenu
 */
@Component({
  selector: 'import-transaction-template-table',
  template: `
    <p-table [columns]="fields" [value]="entityList"
             styleClass="sticky-table p-datatable-striped p-datatable-gridlines"
             selectionMode="single" sortMode="multiple" [multiSortMeta]="multiSortMeta"
             [dataKey]="entityKeyName" [(selection)]="selectedEntity">
      <ng-template pTemplate="header" let-fields>
        <tr>
          <th *ngFor="let field of fields" [pSortableColumn]="field.field">
            {{field.headerTranslated}}
            <p-sortIcon [field]="field.field"></p-sortIcon>
          </th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-el let-columns="fields">
        <tr [pSelectableRow]="el">
          <ng-container *ngFor="let field of fields">
            <td *ngIf="field.visible" [style.width.px]="field.width"
                [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-right': ''">
              {{getValueByPath(el, field)}}
            </td>
          </ng-container>
        </tr>
      </ng-template>
    </p-table>
    <import-transaction-edit-template *ngIf="visibleDialog"
                                      [visibleDialog]="visibleDialog"
                                      [callParam]="callParam"
                                      (closeDialog)="handleCloseDialog($event)">
    </import-transaction-edit-template>
  `,
  providers: [DialogService]
})
export class ImportTransactionTemplateTableComponent extends TableCrudSupportMenu<ImportTransactionTemplate> {
  callParam: CallParam;


  // Parent selected entity
  seclectImportTransactionPlatform: ImportTransactionPlatform;

  parentChildRowSelection: ParentChildRowSelection<ImportTransactionTemplate>;
  private languageAsKeyValue: { [key: string]: string } = {};

  constructor(private importTransactionTemplateService: ImportTransactionTemplateService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              dialogService: DialogService,
              changeDetectionStrategy: ChangeDetectorRef,
              filterService: FilterService,
              translateService: TranslateService,
              globalparameterService: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super('ImportTransactionTemplate', importTransactionTemplateService, confirmationService,
      messageToastService, activePanelService, dialogService, changeDetectionStrategy, filterService,
      translateService, globalparameterService,
      usersettingsService, [CrudMenuOptions.ParentControl, ...TableCrudSupportMenu.ALLOW_ALL_CRUD_OPERATIONS]);

    this.addColumnFeqH(DataType.String, 'templatePurpose', true, false);
    this.addColumn(DataType.String, 'templateFormatType', 'TEMPLATE_FORMAT', true, false,
      {translateValues: true});
    this.addColumnFeqH(DataType.DateString, 'validSince', true, false);
    this.addColumnFeqH(DataType.String, 'templateLanguage', true, false,
      {fieldValueFN: this.getDisplayNameForLanguage.bind(this)});
    this.multiSortMeta.push({field: 'templatePurpose', order: 1});
    this.prepareTableAndTranslate();
  }

  getDisplayNameForLanguage(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.languageAsKeyValue[dataobject['templateLanguage']];
  }

  parentSelectionChanged(seclectImportTransactionPlatform: ImportTransactionPlatform,
                         parentChildRowSelection: ParentChildRowSelection<ImportTransactionTemplate>) {
    this.seclectImportTransactionPlatform = seclectImportTransactionPlatform;
    this.parentChildRowSelection = parentChildRowSelection;
    this.readData();
  }

  readData(): void {
    if (this.seclectImportTransactionPlatform) {
      combineLatest([this.importTransactionTemplateService.getImportTransactionPlatformByPlatform(
        this.seclectImportTransactionPlatform.idTransactionImportPlatform, false),
        this.importTransactionTemplateService.getPossibleLanguagesForTemplate()]).subscribe(data => {
        this.createTranslatedValueStoreAndFilterField(data[0]);
        this.entityList = plainToClass(ImportTransactionTemplate, data[0]);
        data[1].forEach(o => {
          this.languageAsKeyValue.key = <string>o.key;
          this.languageAsKeyValue[o.key] = o.value;
        });
        this.refreshSelectedEntity();
        this.parentChildRowSelection && this.parentChildRowSelection.rowSelectionChanged(this.entityList, this.selectedEntity);
      });
    } else {
      this.entityList = [];
      this.parentChildRowSelection && this.parentChildRowSelection.rowSelectionChanged(this.entityList, this.selectedEntity);
    }
  }

  prepareCallParm(entity: ImportTransactionTemplate) {
    this.callParam = new CallParam(this.seclectImportTransactionPlatform, entity);
  }

  public prepareEditMenu(): MenuItem[] {
    let menuItems: MenuItem[] = [];
    menuItems.push({separator: true});
    if (this.seclectImportTransactionPlatform) {
      menuItems = menuItems.concat(super.prepareEditMenu(this.selectedEntity));
    }
    return menuItems;
  }
}
