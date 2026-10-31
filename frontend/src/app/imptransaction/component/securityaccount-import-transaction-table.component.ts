import { requireInitialized } from '../../lib/helper/required-state';
import { Component, DestroyRef, inject, OnDestroy, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, take } from 'rxjs';

import { AngularSvgIconModule, SvgIconRegistryService } from 'angular-svg-icon';
import { ConfirmationService, MenuItem } from '@openng/optimus-ui/api';
import { toClipboard } from 'copee';

import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ParentChildRowSelection } from '../../lib/datashowbase/parent.child.row.selection';
import { ImportTransactionHead } from '../../entities/import.transaction.head';
import { AppSettings } from '../../shared/app.settings';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { ImportTransactionPosService } from '../service/import.transaction.pos.service';
import { ImportTransactionTemplate } from '../../entities/import.transaction.template';
import { FailedParsedTemplateState } from '../../imptranstemplate/component/failed.parsed.template.state';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { AppHelper } from '../../lib/helper/app.helper';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import type { CombineTemplateAndImpTransPos } from '../../securityaccount/component/combine.template.and.imp.trans.pos';
import { ImportSettings } from './import.settings';
import { Transaction } from '../../entities/transaction';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { Security } from '../../entities/security';
import { SecuritycurrencySearchAndSetDialogComponent } from '../../securitycurrency/component/securitycurrency-search-and-set-dialog.component';
import { SupplementCriteria } from '../../securitycurrency/model/supplement.criteria';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { ColumnConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import type { ImportTransactionPos } from '../../entities/import.transaction.pos';
import { BaseSettings } from '../../lib/base.settings';
import { TemplateFormCheckDialogResultFailedComponent } from '../../imptranstemplate/component/template-form-check-dialog-result-failed.component';
import { SecurityaccountImportExtendedInfoFilenameComponent } from './securityaccount-import-extended-info-filename.component';
import { SecurityaccountImportExtendedInfoComponent } from './securityaccount-import-extended-info.component';
import { SecurityaccountImportSetCashaccountDialogComponent } from './securityaccount-import-set-cashaccount-dialog.component';
import { Helper } from '../../lib/helper/helper';

/**
 * This table is controlled by a master data selection view.
 */
@Component({
  selector: 'securityaccount-import-transaction-table',
  template: `
    <configurable-table
      [data]="entityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="'importTransactionPos.idTransactionPos'"
      [selection]="selectedEntities()"
      (selectionChange)="onSelectionChange($event)"
      [selectionMode]="'multiple'"
      [paginator]="true"
      [rows]="rowsPerPage ?? 20"
      (pageChange)="onPage($event)"
      [multiSortMeta]="multiSortMeta"
      [sortMode]="'multiple'"
      [stripedRows]="true"
      [showGridlines]="true"
      [expandable]="true"
      [expandedRowTemplate]="expandedRowContent"
      [customSortFn]="customSorter"
      [valueGetterFn]="valueGetter"
      [baseLocale]="baseLocale"
      [containerClass]="'datatable'">
      <ng-template #iconCell let-row let-field="field" let-value="value">
        <svg-icon [name]="value" [svgStyle]="{ 'width.px': 16, 'height.px': 16 }" />
      </ng-template>
    </configurable-table>

    <ng-template #expandedRowContent let-row>
      @if (getFailedParsedTemplateStateList(row).length > 0) {
        <template-form-check-dialog-result-failed
          [failedParsedTemplateStateList]="getFailedParsedTemplateStateList(row)" />
      }
      @if (getFailedParsedTemplateStateList(row).length > 0) {
        <securityaccount-import-extended-info-filename [combineTemplateAndImpTransPos]="row" />
      }
      @if (getFailedParsedTemplateStateList(row).length === 0) {
        <securityaccount-import-extended-info [combineTemplateAndImpTransPos]="row" />
      }
    </ng-template>

    @if (visibleSetCashaccountDialog()) {
      @let accountId = selectImportTransactionHead?.securityaccount?.idSecuritycashAccount;
      @if (accountId != null) {
        <securityaccount-import-set-cashaccount
          [visibleDialog]="visibleSetCashaccountDialog()"
          [combineTemplateAndImpTransPos]="selectedEntities()"
          [idSecuritycashAccount]="accountId"
          (closeDialog)="handleOnCloseSetDialog($event)" />
      }
    }
  `,
  imports: [
    ConfigurableTableComponent,
    AngularSvgIconModule,
    TemplateFormCheckDialogResultFailedComponent,
    SecurityaccountImportExtendedInfoFilenameComponent,
    SecurityaccountImportExtendedInfoComponent,
    SecurityaccountImportSetCashaccountDialogComponent
  ]
})
export class SecurityaccountImportTransactionTableComponent extends TableConfigBase implements OnDestroy {
  private importTransactionPosService: ImportTransactionPosService = inject(ImportTransactionPosService);
  private confirmationService: ConfirmationService = inject(ConfirmationService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private iconReg: SvgIconRegistryService = inject(SvgIconRegistryService);
  private dialogService: DialogService = inject(DialogService);

  public static CHECK_OK = 'A';
  public static TRANSACTION_ERROR = 'E';
  public static createTypeIconMap: { [key: string]: string } = {
    ['T']: 'pdfastxt',
    ['P']: 'pdf',
    ['C']: 'csv',
    // user for possible has transaction
    [SecurityaccountImportTransactionTableComponent.CHECK_OK]: 'check',
    ['B']: 'checkcrossedout',
    [SecurityaccountImportTransactionTableComponent.TRANSACTION_ERROR]: 'transerror'
  };

  private static iconLoadDone = false;
  supplementCriteria: SupplementCriteria;
  readonly entityList = signal<CombineTemplateAndImpTransPos[]>([]);
  private readonly destroyRef = inject(DestroyRef);
  private readSubscription?: Subscription;
  readonly selectedEntities = signal<CombineTemplateAndImpTransPos[]>([]);
  selectImportTransactionHead: ImportTransactionHead | null = null;
  private initializedImportTransactionTemplates?: ImportTransactionTemplate[];

  get importTransactionTemplates(): ImportTransactionTemplate[] {
    return requireInitialized(
      this.initializedImportTransactionTemplates,
      'SecurityaccountImportTransactionTableComponent.importTransactionTemplates'
    );
  }

  set importTransactionTemplates(value: ImportTransactionTemplate[]) {
    this.initializedImportTransactionTemplates = value;
  }
  readonly visibleSetCashaccountDialog = signal(false);
  parentChildRowSelection?: ParentChildRowSelection<CombineTemplateAndImpTransPos>;
  private readonly ITP = 'IMPORT_TRANSACTION_POS';

  constructor() {
    super();
    this.rowsPerPage = 50;
    this.supplementCriteria = new SupplementCriteria(true, false);
    SecurityaccountImportTransactionTableComponent.registerIcons(this.iconReg);

    this.addColumnFeqH(DataType.NumericInteger, ImportSettings.IMPORT_TRANSACTION_POS + 'idFilePart', true, false);

    this.addColumn(DataType.String, 'fileTypeIcon', AppSettings.INSTRUMENT_HEADER, true, false, {
      fieldValueFN: this.getFileTypeIcon.bind(this),
      templateName: 'icon',
      width: 25
    });
    this.addColumn(
      DataType.DateTimeString,
      ImportSettings.IMPORT_TRANSACTION_POS + 'transactionTime',
      'DATE',
      true,
      false,
      { width: 120 }
    );
    this.addColumn(
      DataType.String,
      ImportSettings.IMPORT_TRANSACTION_POS + 'transactionType',
      'TRANSACTION_TYPE_IMP',
      true,
      false,
      { width: 60, translateValues: TranslateValue.NORMAL }
    );
    this.addColumn(
      DataType.String,
      ImportSettings.IMPORT_TRANSACTION_POS + 'cashaccount.name',
      AppSettings.CASHACCOUNT.toUpperCase(),
      true,
      false
    );
    this.addColumn(
      DataType.String,
      ImportSettings.IMPORT_TRANSACTION_POS + 'currencyExRate',
      'EXCHANGE_RATE',
      true,
      true
    );
    this.addColumnFeqH(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'isin', true, true, { width: 100 });
    this.addColumnFeqH(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'symbolImp', true, true);
    this.addColumn(
      DataType.String,
      ImportSettings.IMPORT_TRANSACTION_POS + 'security.name',
      AppSettings.SECURITY.toUpperCase(),
      true,
      true,
      { width: 200 }
    );
    this.addColumn(
      DataType.String,
      ImportSettings.IMPORT_TRANSACTION_POS + 'currencyAccount',
      'ACCOUNT_CURRENCY',
      true,
      false
    );
    this.addColumn(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'units', 'QUANTITY', true, false);
    this.addColumn(
      DataType.Numeric,
      ImportSettings.IMPORT_TRANSACTION_POS + 'quotation',
      'QUOTATION_DIV',
      true,
      false,
      { maxFractionDigits: this.gps.getMaxFractionDigits() }
    );
    this.addColumnFeqH(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'diffCashaccountAmount', true, false);
    this.addColumn(
      DataType.Boolean,
      ImportSettings.IMPORT_TRANSACTION_POS + 'readyForTransaction',
      'IMPORT_TRANSACTIONAL',
      true,
      true,
      {
        templateName: 'check'
      }
    );
    this.addColumn(
      DataType.Boolean,
      ImportSettings.IMPORT_TRANSACTION_POS + 'idTransaction',
      'IMPORT_HAS_TRANSACTION',
      true,
      true,
      {
        fieldValueFN: SecurityaccountImportTransactionTableComponent.hasTransaction,
        templateName: 'icon'
      }
    );
    this.addColumn(
      DataType.String,
      ImportSettings.IMPORT_TRANSACTION_POS + 'idTransactionMaybe',
      'IMPORT_HAS_MAYBE_TRANSACTION',
      true,
      true,
      { fieldValueFN: this.getMayBeHasTransactionIcon, templateName: 'icon' }
    );
    this.addColumn(
      DataType.String,
      ImportSettings.IMPORT_TRANSACTION_TEMPLATE + 'templatePurpose',
      'TEMPLATE_PURPOSE',
      false,
      true,
      { width: 150 }
    );
    this.addColumn(
      DataType.DateString,
      ImportSettings.IMPORT_TRANSACTION_TEMPLATE + 'validSince',
      'VALID_SINCE',
      false,
      true,
      { width: 60 }
    );

    this.multiSortMeta.push({
      field: ImportSettings.IMPORT_TRANSACTION_POS + 'transactionTime',
      order: 1
    });
    this.prepareTableAndTranslate();
    this.readTableDefinition(AppSettings.IMPORT_TRANSACTION_POS_TABLE_SETTINGS_STORE);
  }

  public static hasTransaction(
    entity: CombineTemplateAndImpTransPos,
    field: ColumnConfig,
    valueField: any
  ): string | undefined {
    const icon = entity.importTransactionPos.idTransaction
      ? SecurityaccountImportTransactionTableComponent.CHECK_OK
      : entity.importTransactionPos.transactionError
        ? SecurityaccountImportTransactionTableComponent.TRANSACTION_ERROR
        : null;
    return icon == null ? undefined : SecurityaccountImportTransactionTableComponent.createTypeIconMap[icon];
  }

  private static registerIcons(iconReg: SvgIconRegistryService): void {
    if (!SecurityaccountImportTransactionTableComponent.iconLoadDone) {
      for (const [key, iconName] of Object.entries(SecurityaccountImportTransactionTableComponent.createTypeIconMap)) {
        iconReg.loadSvg(BaseSettings.PATH_ASSET_ICONS + iconName + BaseSettings.SVG, iconName);
      }
      SecurityaccountImportTransactionTableComponent.iconLoadDone = false;
    }
  }

  parentSelectionChanged(
    selectImportTransactionHead: ImportTransactionHead | null,
    parentChildRowSelection: ParentChildRowSelection<CombineTemplateAndImpTransPos>,
    importTransactionTemplates: ImportTransactionTemplate[]
  ) {
    if (this.selectImportTransactionHead?.idTransactionHead !== selectImportTransactionHead?.idTransactionHead) {
      this.entityList.set([]);
      this.selectedEntities.set([]);
      this.visibleSetCashaccountDialog.set(false);
    }
    this.selectImportTransactionHead = selectImportTransactionHead;
    this.parentChildRowSelection = parentChildRowSelection;
    this.importTransactionTemplates = importTransactionTemplates;
    this.readData();
  }

  readData(): void {
    this.readSubscription?.unsubscribe();
    const headId = this.selectImportTransactionHead?.idTransactionHead;
    if (headId != null) {
      this.readSubscription = this.importTransactionPosService
        .getCombineTemplateAndImpTransPosListByTransactionHead(headId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((combineTemplateAndImpTransPos: CombineTemplateAndImpTransPos[]) => {
          this.createTranslatedValueStoreAndFilterField(combineTemplateAndImpTransPos);
          this.entityList.set(combineTemplateAndImpTransPos);
          const selectedIds = new Set(this.selectedEntities().map((row) => row.importTransactionPos.idTransactionPos));
          this.selectedEntities.set(
            combineTemplateAndImpTransPos.filter((row) => selectedIds.has(row.importTransactionPos.idTransactionPos))
          );
          this.parentChildRowSelection && this.parentChildRowSelection.rowSelectionChanged(this.entityList(), null);
        });
    } else {
      this.entityList.set([]);
      this.selectedEntities.set([]);
      this.parentChildRowSelection && this.parentChildRowSelection.rowSelectionChanged(this.entityList(), null);
    }
  }

  /**
   * Takes over the selection and lets the master view rebuild its menus. Needed because the row checkbox changes the
   * selection on the native change event, i.e. after the click event the master view refreshes its menus on.
   *
   * @param selectedEntities - The rows selected now
   */
  onSelectionChange(selection: CombineTemplateAndImpTransPos | CombineTemplateAndImpTransPos[] | null): void {
    this.selectedEntities.set(Helper.multipleSelection(selection));
    this.parentChildRowSelection?.rowSelectionChanged(this.entityList(), null);
  }

  /**
   * Builds the failed parsed template state list for a given row.
   * This is called when a row is expanded to show error details.
   *
   * @param ctaitp - The combined template and import transaction position data
   * @returns Array of failed parsed template states
   */
  getFailedParsedTemplateStateList(ctaitp: CombineTemplateAndImpTransPos): FailedParsedTemplateState[] {
    const failedStates: FailedParsedTemplateState[] = [];
    if (ctaitp?.importTransactionPos?.importTransactionPosFailedList) {
      ctaitp.importTransactionPos.importTransactionPosFailedList.forEach((importTransactionPosFailed) => {
        const importTransactionTemplate = this.importTransactionTemplates.find(
          (itt) => itt.idTransactionImportTemplate === importTransactionPosFailed.idTransactionImportTemplate
        );
        if (importTransactionTemplate) {
          failedStates.push(
            new FailedParsedTemplateState(
              importTransactionPosFailed.lastMatchingProperty,
              importTransactionPosFailed.errorMessage,
              importTransactionTemplate.templatePurpose,
              importTransactionTemplate.validSince,
              importTransactionTemplate.templateLanguage
            )
          );
        }
      });
    }
    return failedStates;
  }

  public prepareShowMenu(): MenuItem[] | null {
    return super.getMenuShowOptions();
  }

  public prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({ separator: true });
    menuItems.push({
      label: 'DELETE_RECORDS|' + this.ITP,
      command: (event) => this.handleDeleteEntities(),
      disabled: !this.selectedEntities() || this.selectedEntities().length === 0
    });
    menuItems.push({ separator: true });

    menuItems.push({
      label: 'COPY_FILENAME_TO_CLIPBOARD',
      disabled:
        !this.selectedEntities() || this.selectedEntities().length !== 1 || !this.selectedEntities()[0].fullPath,
      command: (event) => toClipboard(this.selectedEntities()[0].importTransactionPos.fileNameOriginal)
    });

    menuItems.push({ separator: true });
    menuItems.push({
      label: '_ACCEPT_TOTAL_DIFF',
      disabled:
        !this.selectedEntities() ||
        this.selectedEntities().length === 0 ||
        this.selectedEntities().some((ctaitp) => this.hasTransactionOrMaybe(ctaitp.importTransactionPos)),
      command: (event) => this.acceptTotalDiff()
    });

    menuItems.push({
      label: 'IMPORT_ADJUST_MULTIPLICATION',
      disabled:
        !this.selectedEntities() ||
        this.selectedEntities().length === 0 ||
        !this.selectedEntities().every((ctaitp) => !!ctaitp.importTransactionPos.calcCashaccountAmount) ||
        this.selectedEntities().some((ctaitp) => this.hasTransactionOrMaybe(ctaitp.importTransactionPos)),
      command: (event) => this.adjustExchangeRateOrQuotation()
    });

    menuItems.push({
      label: 'SET_SECURITY' + BaseSettings.DIALOG_MENU_SUFFIX,
      disabled:
        !this.selectedEntities() ||
        this.selectedEntities().length === 0 ||
        !this.selectedEntities().every((ctaitp) =>
          Transaction.isSecurityTransaction(ctaitp.importTransactionPos.transactionType)
        ) ||
        this.selectedEntities().some((ctaitp) => this.hasTransactionOrMaybe(ctaitp.importTransactionPos)),
      command: (event) => this.openSetSecurityDialog()
    });

    menuItems.push({
      label: 'SET_CASHACCOUNT' + BaseSettings.DIALOG_MENU_SUFFIX,
      disabled:
        !this.selectedEntities() ||
        this.selectedEntities().length === 0 ||
        this.selectedEntities().some((ctaitp) => this.hasTransactionOrMaybe(ctaitp.importTransactionPos)),
      command: (event) => this.visibleSetCashaccountDialog.set(true)
    });
    menuItems.push({ separator: true });
    menuItems.push({
      label: '_IGNORE_MAYBE_TRANSACTION' + BaseSettings.DIALOG_MENU_SUFFIX,
      disabled:
        !this.selectedEntities() ||
        this.selectedEntities().length === 0 ||
        this.selectedEntities().some(
          (ctaitp) =>
            ctaitp.importTransactionPos.idTransactionMaybe === null ||
            ctaitp.importTransactionPos.idTransactionMaybe === 0
        ),
      command: (event) => this.removeMayBeTransaction(0)
    });

    menuItems.push({
      label: '_IGNORE_MAYBE_TRANSACTION_UNDO' + BaseSettings.DIALOG_MENU_SUFFIX,
      disabled:
        !this.selectedEntities() ||
        this.selectedEntities().length === 0 ||
        this.selectedEntities().some((ctaitp) => ctaitp.importTransactionPos.idTransactionMaybe !== 0),
      command: (event) => this.removeMayBeTransaction(null)
    });

    menuItems.push({ separator: true });
    menuItems.push({
      label: 'IMPORT_CREATE_TRANSACTION',
      command: (event) => this.handleCreateTransactions(),
      disabled:
        !this.selectedEntities() ||
        this.selectedEntities().length === 0 ||
        !this.selectedEntities().every((ctaitp) => ctaitp.importTransactionPos.readyForTransaction) ||
        this.selectedEntities().some((ctaitp) => this.hasTransactionOrMaybe(ctaitp.importTransactionPos))
    });
    return menuItems;
  }

  adjustExchangeRateOrQuotation(): void {
    this.importTransactionPosService
      .adjustCurrencyExRateOrQuotation(
        this.selectedEntities().map(
          (ctaitp: CombineTemplateAndImpTransPos) => ctaitp.importTransactionPos.idTransactionPos
        )
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((importTransactionPosList) =>
        this.showMessageAndReadData('MSG_ACCEPTED_ADJUST_MULTIPLICATION', importTransactionPosList.length)
      );
  }

  acceptTotalDiff(): void {
    this.importTransactionPosService
      .acceptTotalDiff(
        this.selectedEntities().map(
          (ctaitp: CombineTemplateAndImpTransPos) => ctaitp.importTransactionPos.idTransactionPos
        )
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((importTransactionPosList) =>
        this.showMessageAndReadData('MSG_ACCEPTED_TOTAL_DIFF', importTransactionPosList.length)
      );
  }

  /**
   * Opens the security search dialog via DialogService and saves the selected security to the import transaction positions.
   */
  private openSetSecurityDialog(): void {
    this.translateService
      .get('SET_SECURITY')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((title) => {
        const ref = this.dialogService.open(SecuritycurrencySearchAndSetDialogComponent, {
          header: title,
          width: '720px',
          modal: true,
          resizable: false,
          closable: true,
          closeOnEscape: true,
          data: { supplementCriteria: this.supplementCriteria }
        });
        if (!ref) return;
        ref.onClose.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((security: Security) => {
          const securityId = security?.idSecuritycurrency;
          if (securityId != null) {
            this.importTransactionPosService
              .setSecurity(
                securityId,
                this.selectedEntities().map((ctaitp) => ctaitp.importTransactionPos.idTransactionPos)
              )
              .pipe(takeUntilDestroyed(this.destroyRef))
              .subscribe(() => this.readData());
          }
        });
      });
  }

  removeMayBeTransaction(idTransactionMayBe: number | null): void {
    this.importTransactionPosService
      .setIdTransactionMayBe(
        idTransactionMayBe,
        this.selectedEntities().map(
          (combineTemplateAndImpTransPos) => combineTemplateAndImpTransPos.importTransactionPos.idTransactionPos
        )
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((rcImportTransactionPosList) => this.readData());
  }

  showMessageAndReadData(messageKey: string, noRecord: number): void {
    this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, messageKey, { noRecord });
    this.readData();
  }

  handleDeleteEntities(): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORDS|' + this.ITP,
      () => {
        this.importTransactionPosService
          .deleteMultiple(this.selectedEntities().map((ctaitp) => ctaitp.importTransactionPos.idTransactionPos))
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((response) => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORDS', {
              i18nRecord: this.ITP
            });
            this.selectedEntities.set([]);
            this.readData();
          });
      }
    );
  }

  handleCreateTransactions(): void {
    this.importTransactionPosService
      .createAndSaveTransactions(this.selectedEntities().map((ctaitp) => ctaitp.importTransactionPos.idTransactionPos))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((rc) => this.readData());
  }

  handleOnCloseSetDialog(processedActionData: ProcessedActionData): void {
    this.visibleSetCashaccountDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  getFileTypeIcon(entity: CombineTemplateAndImpTransPos, field: ColumnConfig, valueField: any): string | undefined {
    const fileType = entity.importTransactionPos.fileType;
    return fileType == null ? undefined : SecurityaccountImportTransactionTableComponent.createTypeIconMap[fileType];
  }

  getMayBeHasTransactionIcon(entity: CombineTemplateAndImpTransPos, field: ColumnConfig, valueField: any): string {
    return SecurityaccountImportTransactionTableComponent.createTypeIconMap[
      entity.importTransactionPos.idTransactionMaybe == null
        ? ''
        : entity.importTransactionPos.idTransactionMaybe > 0
          ? SecurityaccountImportTransactionTableComponent.CHECK_OK
          : 'B'
    ];
  }

  ngOnDestroy(): void {
    this.writeTableDefinition(AppSettings.IMPORT_TRANSACTION_POS_TABLE_SETTINGS_STORE);
  }

  private hasTransactionOrMaybe(itp: ImportTransactionPos): boolean {
    return itp.idTransaction != null || (itp.idTransactionMaybe != null && itp.idTransactionMaybe > 0);
  }
}
