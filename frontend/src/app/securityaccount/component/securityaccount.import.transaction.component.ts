import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {HelpIds} from '../../shared/help/help.ids';
import {ImportTransactionHead} from '../../entities/import.transaction.head';
import {
  ImportTransactionHeadService,
  SuccessFailedDirectImportTransaction
} from '../service/import.transaction.head.service';
import {ActivatedRoute, Params} from '@angular/router';
import {Securityaccount} from '../../entities/securityaccount';
import {Subscription} from 'rxjs';
import {SecurityaccountImportTransactionTableComponent} from './securityaccount-import-transaction-table.component';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {SingleRecordMasterViewBase} from '../../shared/masterdetail/component/single.record.master.view.base';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ConfirmationService, MenuItem} from 'primeng/api';
import {ParentChildRowSelection} from '../../lib/datashowbase/parent.child.row.selection';
import {ImportTransactionTemplateService} from '../../imptranstemplate/service/import.transaction.template.service';
import {ImportTransactionTemplate} from '../../entities/import.transaction.template';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {CombineTemplateAndImpTransPos} from './combine.template.and.imp.trans.pos';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {plainToInstance} from 'class-transformer';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {AdditionalFieldConfig, FileUploadParam} from '../../shared/generaldialog/model/file.upload.param';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {BaseSettings} from '../../lib/base.settings';


/**
 * Main component for the transaction import
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm">
      </dynamic-form>

      @if (contextMenuItems) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
      }
      <br/>
      <securityaccount-import-transaction-table></securityaccount-import-transaction-table>
    </div>

    @if (visibleEditDialog) {
      <securityaccount-import-transaction-edit-head
        [visibleDialog]="visibleEditDialog"
        [callParam]="callParam"
        (closeDialog)="handleCloseEditDialog($event)">
      </securityaccount-import-transaction-edit-head>
    }

    @if (visibleUploadFileDialog) {
      <upload-file-dialog
        [visibleDialog]="visibleUploadFileDialog"
        [fileUploadParam]="fileUploadParam"
        (closeDialog)="handleCloseImportUploadDialog($event)">
      </upload-file-dialog>
    }
  `,
  standalone: false
})
export class SecurityaccountImportTransactionComponent
  extends SingleRecordMasterViewBase<ImportTransactionHead, CombineTemplateAndImpTransPos>
  implements OnInit, OnDestroy, ParentChildRowSelection<CombineTemplateAndImpTransPos> {

  private static readonly MAIN_FIELD = 'idTransactionHead';

  // Access child component
  @ViewChild(SecurityaccountImportTransactionTableComponent, {static: true}) sitdc: SecurityaccountImportTransactionTableComponent;

  // Child Dialogs
  visibleImportEditHeadDialog = false;
  visibleUploadFileDialog = false;
  fileUploadParam: FileUploadParam;
  // callParam: CallParam;
  importTransactionTemplates: ImportTransactionTemplate[];
  successFailedDirectImportTransaction: SuccessFailedDirectImportTransaction;

  securityAccount: Securityaccount;

  private routeSubscribe: Subscription;

  constructor(private activatedRoute: ActivatedRoute,
    private importTransactionHeadService: ImportTransactionHeadService,
    private importTransactionTemplateService: ImportTransactionTemplateService,
    gps: GlobalparameterService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    translateService: TranslateService) {

    super(gps, HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT,
      SecurityaccountImportTransactionComponent.MAIN_FIELD,
      'IMPORT_SET', importTransactionHeadService, confirmationService, messageToastService, activePanelService,
      translateService);

    this.formConfig = {labelColumns: 2, nonModal: true};

    this.config = [
      DynamicFieldHelper.createFieldSelectNumber(SecurityaccountImportTransactionComponent.MAIN_FIELD,
        'IMPORT_TRANSACTION_NAME', false, {usedLayoutColumns: 6}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', AppSettings.FID_MAX_LETTERS, false,
        {usedLayoutColumns: 6, disabled: true}),
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngOnInit(): void {
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      this.securityAccount = JSON.parse(params[AppSettings.SECURITYACCOUNT.toLowerCase()]);
      this.callParam = new CallParam(this.securityAccount, null);
      this.importTransactionTemplateService.getImportTransactionPlatformByTradingPlatformPlan(
        this.securityAccount.tradingPlatformPlan.idTradingPlatformPlan, true).subscribe(
        (importTransactionTemplates: ImportTransactionTemplate[]) => {
          this.importTransactionTemplates = importTransactionTemplates;
          if (params[AppSettings.SUCCESS_FAILED_IMP_TRANS]) {
            this.successFailedDirectImportTransaction = JSON.parse(params[AppSettings.SUCCESS_FAILED_IMP_TRANS]);
            this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'FAILED_TRANS_FROM_IMPORT');
          }
          setTimeout(() => {
            this.valueChangedMainField();
            this.readData();
          });
        });
    });
  }

  readData(): void {
    this.importTransactionHeadService.getImportTransactionHeadBySecurityaccount(this.securityAccount.idSecuritycashAccount).subscribe(
      (importTransactionHeads: ImportTransactionHead[]) => {
        this.entityList = plainToInstance(ImportTransactionHead, importTransactionHeads);
        this.configObject.idTransactionHead.valueKeyHtmlOptions =
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idTransactionHead', 'name', importTransactionHeads, true);

        if (!this.selectedEntity && this.successFailedDirectImportTransaction) {
          this.selectedEntity = this.entityList.find(imporTtransactionHead =>
            imporTtransactionHead.idTransactionHead === this.successFailedDirectImportTransaction.idTransactionHead);
        }
        this.setFieldValues();
      });
  }

  setChildData(selectedEntity: ImportTransactionHead): void {
    this.sitdc.parentSelectionChanged(this.selectedEntity, this, this.importTransactionTemplates);
  }

  prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = this.getBaseEditMenu('IMPORT_SET');

    menuItems.push({separator: true});
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
      command: (event) => this.handleUploadFiles(null, this.selectedEntity, 'UPLOAD_TXT_FROM_GT_TRANSFORM', 'txt', false)
    });

    // Add menu items of child data table
    menuItems.push(...this.sitdc.prepareEditMenu());
    TranslateHelper.translateMenuItems(menuItems, this.translateService);

    return menuItems;
  }

  rowSelectionChanged(childEntityList: CombineTemplateAndImpTransPos[], childSelectedEntity: CombineTemplateAndImpTransPos) {
    this.childEntityList = childEntityList;
    this.refreshMenus();
  }

  handleUploadCSVFile(): void {
    this.importTransactionTemplateService.getCSVTemplateIdsAsValueKeyHtmlSelectOptions(
      this.securityAccount.tradingPlatformPlan.importTransactionPlatform.idTransactionImportPlatform).subscribe(vkhso => {
      const fieldConfig = [DynamicFieldHelper.createFieldSelectString('idTransactionImportTemplate',
        'IMPORT_TRANSACTION_TEMPLATE', true, {disabled: vkhso.length < 2})];
      if (vkhso.length === 1) {
        fieldConfig[0].defaultValue = vkhso[0].key;
      }
      fieldConfig[0].valueKeyHtmlOptions = vkhso;
      this.handleUploadFiles(new AdditionalFieldConfig(fieldConfig, this.submitPrepareFN.bind(this)), this.selectedEntity,
        'UPLOAD_CSV', 'csv', false);
    });
  }

  handleUploadFiles(additionalFieldConfig: AdditionalFieldConfig, importTransactionHead: ImportTransactionHead,
    titleUpload: string, acceptFileType: string, multiple: boolean): void {
    this.fileUploadParam = new FileUploadParam(HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT,
      additionalFieldConfig, acceptFileType, titleUpload, multiple, this.importTransactionHeadService,
      importTransactionHead.idTransactionHead);
    this.visibleUploadFileDialog = true;
  }

  submitPrepareFN(value: { [name: string]: any }, formData: FormData, fieldConfig: FieldConfig[]): void {
    formData.append(fieldConfig[0].field, fieldConfig[0].formControl.value);
  }

  handleCloseImportUploadDialog(processedActionData: ProcessedActionData): void {
    this.visibleUploadFileDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.setChildData(this.selectedEntity);
    }
  }

  ngOnDestroy(): void {
    super.destroy();
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
  }

  protected override prepareShowMenu(): MenuItem[] {
    const menuItems = this.sitdc.prepareShowMenu();
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  protected prepareCallParam(entity: ImportTransactionHead): void {
    this.callParam.thisObject = entity;
  }

}
