import { httpResource } from '@angular/common/http';
import {
  afterNextRender,
  Component,
  effect,
  inject,
  Injector,
  input,
  numberAttribute,
  OnDestroy,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MenuItem } from '@openng/optimus-ui/api';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { plainToInstance } from 'class-transformer';
import { forkJoin, Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { ImportTransactionHead } from '../../entities/import.transaction.head';
import { ImportTransactionTemplate } from '../../entities/import.transaction.template';
import { Securityaccount } from '../../entities/securityaccount';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { ImportTransactionTemplateService } from '../../imptranstemplate/service/import.transaction.template.service';
import { BaseSettings } from '../../lib/base.settings';
import { ParentChildRowSelection } from '../../lib/datashowbase/parent.child.row.selection';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { UploadFileDialogComponent } from '../../lib/generaldialog/component/upload-file-dialog.component';
import { AdditionalFieldConfig, FileUploadParam } from '../../lib/generaldialog/model/file.upload.param';
import { HelpIds } from '../../lib/help/help.ids';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';
import { TreeNavigationStateService } from '../../lib/maintree/service/tree.navigation.state.service';
import { SingleRecordMasterViewBase } from '../../lib/masterdetail/component/single.record.master.view.base';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { CombineTemplateAndImpTransPos } from '../../securityaccount/component/combine.template.and.imp.trans.pos';
import { AppSettings } from '../../shared/app.settings';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import {
  ImportTransactionHeadService,
  SuccessFailedDirectImportTransaction
} from '../service/import.transaction.head.service';
import { GTNetImportHeadSelectDialogComponent } from './gtnet-import-head-select-dialog.component';
import { SecurityaccountImportTransactionEditHeadComponent } from './securityaccount-import-transaction-edit-head.component';
import { SecurityaccountImportTransactionTableComponent } from './securityaccount-import-transaction-table.component';

/**
 * Main component for the transaction import
 */
@Component({
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      #cmDiv
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm" />

      @if (contextMenuItems) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems" appendTo="body" />
      }
      <br />
      <securityaccount-import-transaction-table />
    </div>

    @if (visibleEditDialog) {
      <securityaccount-import-transaction-edit-head
        [visibleDialog]="visibleEditDialog"
        [callParam]="callParam"
        (closeDialog)="handleCloseEditDialog($event)" />
    }

    @if (visibleUploadFileDialog()) {
      <upload-file-dialog
        [visibleDialog]="visibleUploadFileDialog()"
        [fileUploadParam]="fileUploadParam"
        (closeDialog)="handleCloseImportUploadDialog($event)" />
    }

    @if (visibleGtnetHeadSelectDialog()) {
      <gtnet-import-head-select-dialog
        [visibleDialog]="visibleGtnetHeadSelectDialog()"
        [suggestedHeadName]="suggestedGtnetHeadName"
        [idTransactionHead]="$safeNavigationMigration(selectedEntity?.idTransactionHead)"
        (closeDialog)="handleCloseGtnetHeadSelectDialog($event)" />
    }
  `,
  imports: [
    ...DYNAMIC_FORM_IMPORTS,
    ContextMenuModule,
    SecurityaccountImportTransactionTableComponent,
    SecurityaccountImportTransactionEditHeadComponent,
    UploadFileDialogComponent,
    GTNetImportHeadSelectDialogComponent
  ]
})
export class SecurityaccountImportTransactionComponent
  extends SingleRecordMasterViewBase<ImportTransactionHead, CombineTemplateAndImpTransPos, CallParam>
  implements OnDestroy, ParentChildRowSelection<CombineTemplateAndImpTransPos>
{
  readonly id = input.required({ transform: numberAttribute });
  readonly importResult = input<string>(undefined, { alias: 'successFailedDirectImpTran' });

  private importTransactionHeadService: ImportTransactionHeadService = inject(ImportTransactionHeadService);
  private importTransactionTemplateService: ImportTransactionTemplateService = inject(ImportTransactionTemplateService);
  private treeNavState: TreeNavigationStateService = inject(TreeNavigationStateService);
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);

  private static readonly MAIN_FIELD = 'idTransactionHead';
  private static readonly STORAGE_KEY_SELECTED_HEAD = 'selectedImportTransactionHead';

  // Access child component
  readonly sitdc = viewChild(SecurityaccountImportTransactionTableComponent);

  // Child Dialogs
  readonly visibleUploadFileDialog = signal(false);
  readonly visibleGtnetHeadSelectDialog = signal(false);
  fileUploadParam: FileUploadParam;
  // callParam: CallParam;
  importTransactionTemplates: ImportTransactionTemplate[];
  successFailedDirectImportTransaction: SuccessFailedDirectImportTransaction;
  suggestedGtnetHeadName = '';

  securityAccount: Securityaccount;
  private readonly injector = inject(Injector);

  private readonly headAccountId = signal<number | undefined>(undefined);
  readonly headsResource = httpResource<ImportTransactionHead[]>(() => {
    const id = this.headAccountId();
    return id == null
      ? undefined
      : this.importTransactionHeadService.getImportTransactionHeadBySecurityaccountRequest(id);
  });
  readonly templatesResource = rxResource({
    params: () => ({ id: this.id(), importResult: this.importResult() }),
    stream: ({ params }) => {
      const account = this.treeNavState.getEntity<Securityaccount>(AppSettings.SECURITYACCOUNT_IMPORT_KEY, params.id);
      return this.loadImportTransactionTemplates(account).pipe(
        map((templates) => ({ templates, importResult: params.importResult }))
      );
    }
  });
  constructor() {
    super(
      HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT,
      SecurityaccountImportTransactionComponent.MAIN_FIELD,
      'IMPORT_SET',
      inject(ImportTransactionHeadService)
    );

    this.formConfig = { labelColumns: 2, nonModal: true };

    this.config = [
      DynamicFieldHelper.createFieldSelectNumber(
        SecurityaccountImportTransactionComponent.MAIN_FIELD,
        'IMPORT_TRANSACTION_NAME',
        false,
        { usedLayoutColumns: 6 }
      ),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', BaseSettings.FID_MAX_LETTERS, false, {
        usedLayoutColumns: 6,
        disabled: true
      })
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

    effect(() => {
      const id = this.id();
      this.importResult();
      untracked(() => {
        this.headAccountId.set(undefined);
        this.securityAccount = this.treeNavState.getEntity<Securityaccount>(AppSettings.SECURITYACCOUNT_IMPORT_KEY, id);
        this.selectedEntity = null;
        this.childEntityList = [];
        this.successFailedDirectImportTransaction = null;
        this.callParam = new CallParam(this.securityAccount, null);
      });
    });

    reportResourceErrors(this.templatesResource, this.importTransactionHeadService);
    reportResourceErrors(this.headsResource, this.importTransactionHeadService);
    effect((onCleanup) => {
      if (this.templatesResource.isLoading() || !this.templatesResource.hasValue()) return;
      const result = this.templatesResource.value();
      const render = untracked(() => {
        this.importTransactionTemplates = result.templates;
        if (result.importResult) {
          this.successFailedDirectImportTransaction = JSON.parse(result.importResult);
          this.messageToastService.showMessageI18n(
            this.successFailedDirectImportTransaction.possibleDuplicate ? InfoLevelType.WARNING : InfoLevelType.ERROR,
            this.successFailedDirectImportTransaction.possibleDuplicate
              ? 'POSSIBLE_DUPLICATE_TRANS_FROM_IMPORT'
              : 'FAILED_TRANS_FROM_IMPORT'
          );
        }
        return afterNextRender(
          () => {
            this.valueChangedMainField();
            this.readData();
          },
          { injector: this.injector }
        );
      });
      onCleanup(() => render.destroy());
    });
    effect(() => {
      if (this.headsResource.isLoading() || this.headAccountId() == null) return;
      const importTransactionHeads = this.headsResource.hasValue() ? this.headsResource.value() : [];
      untracked(() => {
        this.entityList = plainToInstance(ImportTransactionHead, importTransactionHeads);
        this.configObject.idTransactionHead.valueKeyHtmlOptions =
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
            'idTransactionHead',
            'name',
            importTransactionHeads,
            true
          );

        if (!this.selectedEntity && this.successFailedDirectImportTransaction) {
          this.selectedEntity = this.entityList.find(
            (imporTtransactionHead) =>
              imporTtransactionHead.idTransactionHead === this.successFailedDirectImportTransaction.idTransactionHead
          );
        }

        // Restore selection from localStorage if no entity is selected
        if (!this.selectedEntity) {
          const savedHeadId = localStorage.getItem(SecurityaccountImportTransactionComponent.STORAGE_KEY_SELECTED_HEAD);
          if (savedHeadId) {
            this.selectedEntity = this.entityList.find((h) => h.idTransactionHead === Number(savedHeadId));
          }
        }
        this.setFieldValues();
      });
    });
  }

  /**
   * Loads the import templates used to label imported and failed positions: the securities account's platform
   * templates (only when the account is mapped to an import platform) merged with the tenant's Grafioschtrader
   * import platform templates (only when configured), deduplicated by template id. Loading the account templates
   * conditionally avoids a backend error for accounts without an import platform.
   */
  private loadImportTransactionTemplates(
    account: Securityaccount = this.securityAccount
  ): Observable<ImportTransactionTemplate[]> {
    const accountTemplates$ = account.tradingPlatformPlan.importTransactionPlatform
      ? this.importTransactionTemplateService.getImportTransactionPlatformByTradingPlatformPlan(
          account.tradingPlatformPlan.idTradingPlatformPlan,
          true
        )
      : of<ImportTransactionTemplate[]>([]);
    const gtPlatformId = this.gpsGT.getTenantGtImportPlatformId();
    const gtTemplates$ =
      gtPlatformId != null
        ? this.importTransactionTemplateService.getImportTransactionPlatformByPlatform(gtPlatformId, true)
        : of<ImportTransactionTemplate[]>([]);
    return forkJoin([accountTemplates$, gtTemplates$]).pipe(
      map(([accountTemplates, gtTemplates]) => {
        const byId = new Map<number, ImportTransactionTemplate>();
        [...accountTemplates, ...gtTemplates].forEach((t) => byId.set(t.idTransactionImportTemplate, t));
        return [...byId.values()];
      })
    );
  }

  readData(): void {
    const id = this.securityAccount?.idSecuritycashAccount;
    if (this.headAccountId() === id) this.headsResource.reload();
    else this.headAccountId.set(id);
  }

  protected override setFieldValues(): void {
    super.setFieldValues();
    // Save selection to localStorage
    if (this.selectedEntity?.idTransactionHead) {
      localStorage.setItem(
        SecurityaccountImportTransactionComponent.STORAGE_KEY_SELECTED_HEAD,
        String(this.selectedEntity.idTransactionHead)
      );
    }
  }

  setChildData(selectedEntity: ImportTransactionHead): void {
    this.sitdc().parentSelectionChanged(this.selectedEntity, this, this.importTransactionTemplates);
  }

  prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = this.getBaseEditMenu('IMPORT_SET');

    menuItems.push({ separator: true });
    menuItems.push({
      label: 'UPLOAD_CSV' + BaseSettings.DIALOG_MENU_SUFFIX,
      disabled: !this.selectedEntity,
      command: (event) => this.handleUploadCSVFile()
    });
    menuItems.push({
      label: 'UPLOAD_PDFS' + BaseSettings.DIALOG_MENU_SUFFIX,
      disabled: !this.selectedEntity,
      command: (event) => this.handleUploadFiles(null, this.selectedEntity, 'UPLOAD_PDFs', 'pdf', true)
    });

    menuItems.push({
      label: 'UPLOAD_TXT_FROM_GT_TRANSFORM' + BaseSettings.DIALOG_MENU_SUFFIX,
      disabled: !this.selectedEntity,
      command: (event) =>
        this.handleUploadFiles(null, this.selectedEntity, 'UPLOAD_TXT_FROM_GT_TRANSFORM', 'txt', false)
    });

    menuItems.push({ separator: true });
    menuItems.push({
      label: 'ROLLBACK_IMPORT' + BaseSettings.DIALOG_MENU_SUFFIX,
      disabled: !this.selectedEntity || !this.hasImportedPositions(),
      command: () => this.handleRollbackImport()
    });
    menuItems.push({
      label: 'CREATE_GTNET_IMPORT_FROM_MISSING' + BaseSettings.DIALOG_MENU_SUFFIX,
      disabled: !this.gps.useGtnet() || !this.hasMissingSecurities(),
      command: () => this.handleCreateGtnetImportFromMissing()
    });

    // Add menu items of child data table
    menuItems.push(...this.sitdc().prepareEditMenu());

    return menuItems;
  }

  rowSelectionChanged(
    childEntityList: CombineTemplateAndImpTransPos[],
    childSelectedEntity: CombineTemplateAndImpTransPos
  ) {
    this.childEntityList = childEntityList;
    this.refreshMenus();
  }

  handleUploadCSVFile(): void {
    const head = this.selectedEntity;
    const idPlatform = this.selectedEntity.useGtPlatform
      ? this.gpsGT.getTenantGtImportPlatformId()
      : this.securityAccount.tradingPlatformPlan.importTransactionPlatform.idTransactionImportPlatform;
    this.importTransactionTemplateService
      .getCSVTemplateIdsAsValueKeyHtmlSelectOptions(idPlatform)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((vkhso) => {
        if (this.selectedEntity?.idTransactionHead !== head.idTransactionHead) {
          return;
        }
        const fieldConfig = [
          DynamicFieldHelper.createFieldSelectString(
            'idTransactionImportTemplate',
            'IMPORT_TRANSACTION_TEMPLATE',
            true,
            { disabled: vkhso.length < 2 }
          )
        ];
        if (vkhso.length === 1) {
          fieldConfig[0].defaultValue = vkhso[0].key;
        }
        fieldConfig[0].valueKeyHtmlOptions = vkhso;
        this.handleUploadFiles(
          new AdditionalFieldConfig(fieldConfig, this.submitPrepareFN.bind(this)),
          head,
          'UPLOAD_CSV',
          'csv',
          false
        );
      });
  }

  handleUploadFiles(
    additionalFieldConfig: AdditionalFieldConfig,
    importTransactionHead: ImportTransactionHead,
    titleUpload: string,
    acceptFileType: string,
    multiple: boolean
  ): void {
    this.fileUploadParam = new FileUploadParam(
      HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT,
      additionalFieldConfig,
      acceptFileType,
      titleUpload,
      multiple,
      this.importTransactionHeadService,
      importTransactionHead.idTransactionHead
    );
    this.visibleUploadFileDialog.set(true);
  }

  submitPrepareFN(value: { [name: string]: any }, formData: FormData, fieldConfig: FieldConfig[]): void {
    formData.append(fieldConfig[0].field, fieldConfig[0].formControl.value);
  }

  handleCloseImportUploadDialog(processedActionData: ProcessedActionData): void {
    this.visibleUploadFileDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.setChildData(this.selectedEntity);
    }
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  protected override prepareShowMenu(): MenuItem[] {
    const menuItems = this.sitdc().prepareShowMenu();
    return menuItems;
  }

  protected prepareCallParam(entity: ImportTransactionHead): void {
    this.callParam = new CallParam(this.securityAccount, entity);
  }

  /**
   * Checks if there are import positions with missing securities that have ISIN or ticker symbol.
   */
  private hasMissingSecurities(): boolean {
    return (
      this.childEntityList?.some(
        (ctaitp) =>
          ctaitp.importTransactionPos.security === null &&
          (ctaitp.importTransactionPos.isin || ctaitp.importTransactionPos.symbolImp)
      ) ?? false
    );
  }

  /**
   * Checks if at least one import position of the selected head has already become a transaction.
   */
  private hasImportedPositions(): boolean {
    return this.childEntityList?.some((ctaitp) => ctaitp.importTransactionPos.idTransaction != null) ?? false;
  }

  /**
   * Asks for confirmation and then deletes the transactions created by the selected import, newest first, so that the
   * whole import can be run again. The positions are reloaded afterwards.
   */
  handleRollbackImport(): void {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService, 'MSG_CONFIRM_ROLLBACK_IMPORT', () =>
      this.importTransactionHeadService
        .rollbackImportedTransactions(this.selectedEntity.idTransactionHead)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((noRecord: number) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_ROLLBACK_IMPORT', { noRecord });
          this.setChildData(this.selectedEntity);
        })
    );
  }

  /**
   * Opens the GTNet import head selection dialog.
   * The backend reads missing securities directly from ImportTransactionPos.
   */
  handleCreateGtnetImportFromMissing(): void {
    this.suggestedGtnetHeadName = this.selectedEntity?.name || '';
    this.visibleGtnetHeadSelectDialog.set(true);
  }

  /**
   * Handles closing of the GTNet import head selection dialog.
   * If positions were created successfully, stores the idTransactionHead in localStorage
   * so the GTNet import tab can use it for auto-assigning securities.
   */
  handleCloseGtnetHeadSelectDialog(processedActionData: ProcessedActionData): void {
    this.visibleGtnetHeadSelectDialog.set(false);
    if (processedActionData.action === ProcessedAction.CREATED && this.selectedEntity) {
      // Store idTransactionHead for the GTNet tab to read
      localStorage.setItem(AppSettings.ID_TRANSACTION_HEAD, String(this.selectedEntity.idTransactionHead));
    }
  }
}
