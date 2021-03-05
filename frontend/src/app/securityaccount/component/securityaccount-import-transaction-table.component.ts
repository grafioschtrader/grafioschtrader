import {ChangeDetectorRef, Component, OnDestroy} from '@angular/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {ParentChildRowSelection} from '../../shared/datashowbase/parent.child.row.selection';
import {ImportTransactionHead} from '../../entities/import.transaction.head';
import {AppSettings} from '../../shared/app.settings';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ImportTransactionPosService} from '../service/import.transaction.pos.service';
import {ImportTransactionTemplate} from '../../entities/import.transaction.template';
import {FailedParsedTemplateState} from '../../imptranstemplate/component/failed.parsed.template.state';
import {ProcessedAction} from '../../shared/types/processed.action';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {AppHelper} from '../../shared/helper/app.helper';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {CombineTemplateAndImpTransPos} from './combine.template.and.imp.trans.pos';
import {toClipboard} from 'copee';
import {ImportSettings} from './import.settings';
import {Transaction} from '../../entities/transaction';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {ConfirmationService, MenuItem} from 'primeng/api';
import {Security} from '../../entities/security';
import {
  AfterSetSecurity,
  CallBackSetSecurityWithAfter
} from '../../securitycurrency/component/securitycurrency-search-and-set.component';
import {SupplementCriteria} from '../../securitycurrency/model/supplement.criteria';


/**
 * This table is controlled by a master data selection view.
 */
@Component({
  selector: 'securityaccount-import-transaction-table',
  template: `

    <div class="datatable">
      <p-table [columns]="fields" [value]="entityList" [(selection)]="selectedEntities"
               dataKey="importTransactionPos.idTransactionPos" [paginator]="true" [rows]="50"
               [rowsPerPageOptions]="[20,30,50,80]"
               selectionMode="multiple" (onRowExpand)="onRowExpand($event)" sortMode="multiple"
               [multiSortMeta]="multiSortMeta"
               styleClass="sticky-table p-datatable-striped p-datatable-gridlines">
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th style="width:24px"></th>

            <th style="width: 2.25em">
              <p-tableHeaderCheckbox></p-tableHeaderCheckbox>
            </th>
            <ng-container *ngFor="let field of fields">
              <th *ngIf="field.visible" [pSortableColumn]="field.field" [style.width.px]="field.width">
                {{field.headerTranslated}}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            </ng-container>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-expanded="expanded" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td>
              <a href="#" [pRowToggler]="el">
                <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
              </a>
            </td>
            <td>
              <p-tableCheckbox [value]="el"></p-tableCheckbox>
            </td>
            <ng-container *ngFor="let field of fields">
              <td *ngIf="field.visible" [style.width.px]="field.width"
                  [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-right': ''">
                <ng-container [ngSwitch]="field.templateName">
                  <ng-container *ngSwitchCase="'check'">
                                      <span><i [ngClass]="{'fa fa-check': getValueByPath(el, field)}"
                                               aria-hidden="true"></i></span>
                  </ng-container>
                  <ng-container *ngSwitchDefault>
                    {{getValueByPath(el, field)}}
                  </ng-container>
                </ng-container>
              </td>
            </ng-container>
          </tr>
        </ng-template>

        <ng-template pTemplate="rowexpansion" let-el let-columns="fields">
          <tr>
            <td [attr.colspan]="numberOfVisibleColumns + 2">
              <template-form-check-dialog-result-failed *ngIf="failedParsedTemplateStateList.length > 0"
                                                        [failedParsedTemplateStateList]="failedParsedTemplateStateList">
              </template-form-check-dialog-result-failed>
              <securityaccount-import-extended-info-filename *ngIf="failedParsedTemplateStateList.length > 0"
                                                             [combineTemplateAndImpTransPos]="el">
              </securityaccount-import-extended-info-filename>
              <securityaccount-import-extended-info *ngIf="failedParsedTemplateStateList.length === 0"
                                                    [combineTemplateAndImpTransPos]="el">
              </securityaccount-import-extended-info>
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="paginatorleft" let-state>
          {{selectedEntities.length}} {{'SELECTED_FROM' | translate}} {{entityList.length}}

        </ng-template>

      </p-table>
    </div>
    <securitycurrency-search-and-set *ngIf="visibleSetSecurityDialog"
                                     [visibleDialog]="visibleSetSecurityDialog"
                                     [callBackSetSecurityWithAfter]="this"
                                     [supplementCriteria]="supplementCriteria"
                                     (closeDialog)="handleOnCloseSetDialog($event)">
    </securitycurrency-search-and-set>

    <securityaccount-import-set-cashaccount *ngIf="visibleSetCashaccountDialog"
                                            [visibleDialog]="visibleSetCashaccountDialog"
                                            [combineTemplateAndImpTransPos]="selectedEntities"
                                            [idSecuritycashAccount]="seclectImportTransactionHead.securityaccount.idSecuritycashAccount"
                                            (closeDialog)="handleOnCloseSetDialog($event)">
    </securityaccount-import-set-cashaccount>
  `
})
export class SecurityaccountImportTransactionTableComponent extends TableConfigBase
  implements OnDestroy, CallBackSetSecurityWithAfter {
  private readonly ITP = 'IMPORT_TRANSACTION_POS';

  supplementCriteria: SupplementCriteria;
  entityList: CombineTemplateAndImpTransPos[] = [];
  selectedEntities: CombineTemplateAndImpTransPos[] = [];
  seclectImportTransactionHead: ImportTransactionHead;
  importTransactionTemplates: ImportTransactionTemplate[];
  failedParsedTemplateStateList: FailedParsedTemplateState[];

  visibleSetSecurityDialog = false;
  visibleSetCashaccountDialog = false;

  parentChildRowSelection: ParentChildRowSelection<CombineTemplateAndImpTransPos>;

  constructor(private importTransactionPosService: ImportTransactionPosService,
              private confirmationService: ConfirmationService,
              private messageToastService: MessageToastService,
              changeDetectionStrategy: ChangeDetectorRef,
              translateService: TranslateService,
              globalparameterService: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(changeDetectionStrategy, usersettingsService, translateService, globalparameterService);
    this.supplementCriteria = new SupplementCriteria(true, false);

    this.addColumn(DataType.NumericInteger, ImportSettings.IMPORT_TRANSACTION_POS + 'idFilePart', 'IMPORT_ID_FILE_PART', true, false);
    this.addColumn(DataType.String, 'fileType', 'FILE_TYPE', true, false);
    this.addColumn(DataType.DateString, ImportSettings.IMPORT_TRANSACTION_POS + 'transactionTime', 'DATE', true, false, {width: 60});
    this.addColumn(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'transactionType', 'TRANSACTION_TYPE_IMP', true, false,
      {width: 60, translateValues: true});
    this.addColumn(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'cashaccount.name', 'ACCOUNT', true, false);

    this.addColumn(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'currencyExRate', 'EXCHANGE_RATE', true, true);
    this.addColumnFeqH(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'isin',  true, true, {width: 100});
    this.addColumn(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'symbolImp', 'SYMBOL', true, true);
    this.addColumn(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'security.name', 'SECURITY', true, true,
      {width: 200});
    this.addColumn(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'currencyAccount', 'ACCOUNT_CURRENCY', true, false);
    this.addColumn(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'units', 'QUANTITY', true, false);
    this.addColumn(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'quotation', 'QUOTATION_DIV', true,
      false, {maxFractionDigits: 5});
    this.addColumnFeqH(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'diffCashaccountAmount', true, false);
    this.addColumn(DataType.Boolean, ImportSettings.IMPORT_TRANSACTION_POS + 'readyForTransaction', 'IMPORT_TRANSACTIONAL', true, true, {
      templateName: 'check'
    });
    this.addColumn(DataType.Boolean, ImportSettings.IMPORT_TRANSACTION_POS + 'idTransaction', 'IMPORT_HAS_TRANSACTION', true, true, {
      templateName: 'check'
    });
    this.addColumn(DataType.String, ImportSettings.IMPORT_TRANSACTION_TEMPLATE + 'templatePurpose', 'TEMPLATE_PURPOSE', false, true,
      {width: 150});
    this.addColumn(DataType.DateString, ImportSettings.IMPORT_TRANSACTION_TEMPLATE + 'validSince', 'VALID_SINCE', false, true,
      {width: 60});

    this.multiSortMeta.push({field: 'transactionTime', order: 1});
    this.prepareTableAndTranslate();
    this.readTableDefinition(AppSettings.IMPORT_TRANSACTION_POS_TABLE_SETTINGS_STORE);
  }

  parentSelectionChanged(seclectImportTransactionHead: ImportTransactionHead,
                         parentChildRowSelection: ParentChildRowSelection<CombineTemplateAndImpTransPos>,
                         importTransactionTemplates: ImportTransactionTemplate[]) {
    this.seclectImportTransactionHead = seclectImportTransactionHead;
    this.parentChildRowSelection = parentChildRowSelection;
    this.importTransactionTemplates = importTransactionTemplates;
    this.readData();
  }

  readData(): void {
    if (this.seclectImportTransactionHead) {
      this.importTransactionPosService.getCombineTemplateAndImpTransPosListByTransactionHead(
        this.seclectImportTransactionHead.idTransactionHead).subscribe((combineTemplateAndImpTransPos: CombineTemplateAndImpTransPos[]) => {
        this.createTranslatedValueStoreAndFilterField(combineTemplateAndImpTransPos);
        this.entityList = combineTemplateAndImpTransPos;
        this.parentChildRowSelection && this.parentChildRowSelection.rowSelectionChanged(this.entityList, null);
      });
    } else {
      this.entityList = [];
      this.parentChildRowSelection && this.parentChildRowSelection.rowSelectionChanged(this.entityList, null);
    }
  }

  onRowExpand(event): void {
    const ctaitp: CombineTemplateAndImpTransPos = event.data;
    this.failedParsedTemplateStateList = [];
    if (ctaitp.importTransactionPos.importTransactionPosFailedList) {
      ctaitp.importTransactionPos.importTransactionPosFailedList.forEach(importTransactionPosFailed => {
        const importTransactionTemplate = this.importTransactionTemplates.find(itt =>
          itt.idTransactionImportTemplate === importTransactionPosFailed.idTransactionImportTemplate);
        this.failedParsedTemplateStateList.push(new FailedParsedTemplateState(importTransactionPosFailed.lastMatchingProperty,
          importTransactionPosFailed.errorMessage,
          importTransactionTemplate.templatePurpose, importTransactionTemplate.validSince,
          importTransactionTemplate.templateLanguage));
      });
    }
  }

  /**
   * Save for one or more the selected security to the importtransactionpos.
   * @param security Chosen security
   */
  setSecurity(security: Security, afterSetSecurity: AfterSetSecurity): void {
    this.importTransactionPosService.setSecurity(security.idSecuritycurrency,
      this.selectedEntities.map(combineTemplateAndImpTransPos =>
        combineTemplateAndImpTransPos.importTransactionPos.idTransactionPos)).subscribe(rcImportTransactionPosList => {
      afterSetSecurity.afterSetSecurity();
    });
  }


  prepareCallParm(entity: CombineTemplateAndImpTransPos) {
  }

  public prepareShowMenu(): MenuItem[] {
    return super.getMenuShowOptions();
  }

  public prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({separator: true});
    menuItems.push({
      label: 'DELETE_RECORDS|' + this.ITP, command: (event) => this.handleDeleteEntities(),
      disabled: !this.selectedEntities || this.selectedEntities.length === 0,
    });
    menuItems.push({separator: true});

    menuItems.push({
      label: 'COPY_FILENAME_TO_CLIPBOARD', disabled: !this.selectedEntities || this.selectedEntities.length !== 1
        || !this.selectedEntities[0].fullPath,
      command: (event) => toClipboard(this.selectedEntities[0].importTransactionPos.fileNameOriginal)
    });

    menuItems.push({separator: true});
    menuItems.push({
      label: 'ACCEPT_TOTAL_DIFF', disabled: !this.selectedEntities || this.selectedEntities.length === 0,
      command: (event) => this.acceptTotalDiff()
    });

    menuItems.push({
      label: 'IMPORT_ADJUST_MULTIPLICATION', disabled: !this.selectedEntities || this.selectedEntities.length === 0
        || !this.selectedEntities.every(ctaitp => !!ctaitp.importTransactionPos.calcCashaccountAmount),
      command: (event) => this.adjustExchangeRateOrQuotation(),
    });


    menuItems.push({
      label: 'SET_SECURITY', disabled: !this.selectedEntities || this.selectedEntities.length === 0
        || !this.selectedEntities.every(ctaitPos => Transaction.isSecurityTransaction(ctaitPos.importTransactionPos.transactionType)),
      command: (event) => this.visibleSetSecurityDialog = true
    });


    menuItems.push({
      label: 'SET_CASHACCOUNT', disabled: !this.selectedEntities || this.selectedEntities.length === 0,
      command: (event) => this.visibleSetCashaccountDialog = true
    });
    menuItems.push({separator: true});
    menuItems.push({
      label: 'IMPORT_CREATE_TRANSACTION', command: (event) => this.handleCreateTransactions(),
      disabled: !this.selectedEntities || this.selectedEntities.length === 0,
    });

    return menuItems;
  }


  adjustExchangeRateOrQuotation(): void {
    this.importTransactionPosService.adjustCurrencyExRateOrQuotation(this.selectedEntities.map((ctaitp: CombineTemplateAndImpTransPos) =>
      ctaitp.importTransactionPos.idTransactionPos)).subscribe(importTransactionPosList =>
      this.showMessageAndReadData('MSG_ACCEPTED_ADJUST_MULTIPLICATION', importTransactionPosList.length));

  }

  acceptTotalDiff(): void {
    this.importTransactionPosService.acceptTotalDiff(this.selectedEntities.map((ctaitp: CombineTemplateAndImpTransPos) =>
      ctaitp.importTransactionPos.idTransactionPos)).subscribe(importTransactionPosList =>
      this.showMessageAndReadData('MSG_ACCEPTED_TOTAL_DIFF', importTransactionPosList.length));
  }

  showMessageAndReadData(messageKey: string, noRecord: number): void {
    this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
      messageKey, {noRecord: noRecord});
    this.readData();
  }


  handleDeleteEntities(): void {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORDS|' + this.ITP, () => {
        this.importTransactionPosService.deleteMultiple(this.selectedEntities.map(ctaitp =>
          ctaitp.importTransactionPos.idTransactionPos)).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORDS', {i18nRecord: this.ITP});
          this.selectedEntities = [];
          this.readData();
        });
      });
  }

  handleCreateTransactions(): void {
    this.importTransactionPosService.createAndSaveTransactions(this.selectedEntities.map(ctaitp =>
      ctaitp.importTransactionPos.idTransactionPos)).subscribe(rc => this.readData());
  }

  handleOnCloseSetDialog(processedActionData: ProcessedActionData): void {
    this.visibleSetSecurityDialog = false;
    this.visibleSetCashaccountDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  ngOnDestroy(): void {
    this.writeTableDefinition(AppSettings.IMPORT_TRANSACTION_POS_TABLE_SETTINGS_STORE);
  }

}
