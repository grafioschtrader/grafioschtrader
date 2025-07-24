import {Component} from '@angular/core';
import {ImportTransactionTemplate} from '../../entities/import.transaction.template';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {ImportTransactionTemplateService} from '../service/import.transaction.template.service';
import {ParentChildRowSelection} from '../../lib/datashowbase/parent.child.row.selection';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {ImportTransactionPlatform} from '../../entities/import.transaction.platform';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {plainToClass} from 'class-transformer';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {combineLatest} from 'rxjs';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';

/**
 * Component for import transaction template, one row one template. This table is controlled by a master data selection view.
 * TODO: Change to DatatableCRUDSupportMenu
 */
@Component({
  selector: 'import-transaction-template-table',
  template: `
    <p-table [columns]="fields" [value]="entityList"
             selectionMode="single" sortMode="multiple" [multiSortMeta]="multiSortMeta"
             [dataKey]="entityKeyName" [(selection)]="selectedEntity"
             stripedRows showGridlines>
      <ng-template #header let-fields>
        <tr>
          @for (field of fields; track field) {
            <th [pSortableColumn]="field.field"
                [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
              {{ field.headerTranslated }}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          }
        </tr>
      </ng-template>
      <ng-template #body let-el let-columns="fields">
        <tr [pSelectableRow]="el">
          @for (field of fields; track field) {
            @if (field.visible) {
              <td [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                  [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
              || field.dataType===DataType.NumericInteger)? 'text-right': ''">
                @switch (field.templateName) {
                  @case ('owner') {
                    <span [style]='isNotSingleModeAndOwner(field, el)? "font-weight:500": null'>
                   {{ getValueByPath(el, field) }}</span>
                  }
                  @default {
                    {{ getValueByPath(el, field) }}
                  }
                }
              </td>
            }
          }
        </tr>
      </ng-template>
    </p-table>
    @if (visibleDialog) {
      <import-transaction-edit-template [visibleDialog]="visibleDialog"
                                        [callParam]="callParam"
                                        (closeDialog)="handleCloseDialog($event)">
      </import-transaction-edit-template>
    }
  `,
  providers: [DialogService],
  standalone: false
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

    this.addColumnFeqH(DataType.String, 'templatePurpose', true, false, {templateName: AppSettings.OWNER_TEMPLATE});
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
