import { httpResource } from '@angular/common/http';
import { Component, OnDestroy, effect, inject, input, numberAttribute, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { DatePicker } from '@openng/optimus-ui/datepicker';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { SelectModule } from '@openng/optimus-ui/select';
import { TableModule } from '@openng/optimus-ui/table';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { NgxFileDropEntry, NgxFileDropModule } from 'ngx-file-drop';
import { Securityaccount } from '../../entities/securityaccount';
import { SecurityPositionGrandSummary } from '../../entities/view/security.position.grand.summary';
import { SecurityPositionSummary } from '../../entities/view/security.position.summary';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { OptionalParameters } from '../../historyquote/service/time.series.quotes.service';
import {
  ImportTransactionHeadService,
  SuccessFailedDirectImportTransaction
} from '../../imptransaction/service/import.transaction.head.service';
import { BaseSettings } from '../../lib/base.settings';
import { HelpIds } from '../../lib/help/help.ids';
import { AppHelper } from '../../lib/helper/app.helper';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';
import { TreeNavigationStateService } from '../../lib/maintree/service/tree.navigation.state.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { SecurityaccountService } from '../../securityaccount/service/securityaccount.service';
import { SecurityTransferCreateDialogComponent } from '../../securityaction/component/security-transfer-create-dialog.component';
import { AppSettings } from '../../shared/app.settings';
import { TransactionCashaccountTableComponent } from '../../transaction/component/transaction-cashaccount-table.component';
import { TransactionSecurityEditComponent } from '../../transaction/component/transaction-security-edit.component';
import { TransactionSecurityMarginTreetableComponent } from '../../transaction/component/transaction-security-margin-treetable.component';
import { TransactionSecurityTableComponent } from '../../transaction/component/transaction-security-table.component';
import { TransactionCallParam } from '../../transaction/component/transaction.call.parm';
import { SecurityaccountTable } from './securityaccountTable';

/**
 * It is the summary for a single security account with its securities.
 */
@Component({
  templateUrl: '../view/securityaccount.table.html',
  imports: [
    TranslateModule,
    TableModule,
    DatePicker,
    FormsModule,
    SelectModule,
    TooltipModule,
    ContextMenuModule,
    AngularSvgIconModule,
    NgxFileDropModule,
    TransactionSecurityTableComponent,
    TransactionSecurityMarginTreetableComponent,
    TransactionCashaccountTableComponent,
    TransactionSecurityEditComponent
  ]
})
export class SecurityaccountSummaryComponent extends SecurityaccountTable implements OnDestroy {
  readonly id = input.required({ transform: numberAttribute });

  protected importTransactionHeadService: ImportTransactionHeadService = inject(ImportTransactionHeadService);

  private idSecurityaccount: number;

  private readonly summaryRequest = signal<
    ReturnType<SecurityaccountService['getPositionSummarySecurityaccountRequest']> | undefined
  >(undefined);
  readonly positionResource = httpResource<SecurityPositionGrandSummary>(() => this.summaryRequest());
  constructor() {
    super();

    effect(() => {
      const id = this.id();
      untracked(() => {
        this.idSecurityaccount = id;
        this.securityAccount = this.injector
          .get(TreeNavigationStateService)
          .getEntity<Securityaccount>(AppSettings.SECURITYACCOUNT_SUMMERY_ROUTE_KEY, this.idSecurityaccount);
        this.readData();
      });
    });

    reportResourceErrors(this.positionResource, this.securityaccountService);
    effect(() => {
      const loading = this.positionResource.isLoading();
      const data = this.positionResource.hasValue() ? this.positionResource.value() : undefined;
      untracked(() => {
        this.dataLoading.set(loading);
        if (!loading && data) {
          this.getDataToView(data);
          this.initTableTextTranslation();
          this.changeToOpenChart();
        }
      });
    });
  }

  override readData(): void {
    this.selectedSecurityPositionSummary = null;
    const request = this.securityaccountService.getPositionSummarySecurityaccountRequest(
      this.idSecurityaccount,
      this.securityaccountGroupBase.defaultGroup,
      this.includeClosedPosition,
      this.untilDate
    );
    const current = this.summaryRequest();
    if (current?.url === request.url && current.params.toString() === request.params.toString())
      this.positionResource.reload();
    else this.summaryRequest.set(request);
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT;
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  /** True when the securities account is mapped to its own import platform. */
  override get accountHasOwnPlatform(): boolean {
    return !!this.securityAccount?.tradingPlatformPlan?.importTransactionPlatform;
  }

  /** True when the tenant has configured a Grafioschtrader import platform. */
  override get gtPlatformConfigured(): boolean {
    return this.injector.get(GlobalparameterGTService).getTenantGtImportPlatformId() != null;
  }

  /**
   * The drop zone is shown when the account has its own import platform or the tenant provides a Grafioschtrader
   * import platform (so GT receipt PDFs can be dropped even onto accounts without their own platform mapping).
   */
  override get showDropZone(): boolean {
    return this.accountHasOwnPlatform || this.gtPlatformConfigured;
  }

  public override dropped(files: NgxFileDropEntry[]): void {
    AppHelper.processDroppedFiles(files, this.messageToastService, 'pdf', this.uploadTransactionFiles.bind(this));
  }

  protected override extendEditMenu(menuItems: MenuItem[], securityPositionSummary: SecurityPositionSummary): void {
    menuItems.push({
      label: 'TRANSFER_SECURITY' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: (e) => this.handleTransferSecurity(securityPositionSummary),
      disabled:
        !securityPositionSummary ||
        securityPositionSummary.units === 0 ||
        this.isMarginProduct(securityPositionSummary.security)
    });
  }

  private handleTransferSecurity(securityPositionSummary: SecurityPositionSummary): void {
    const dialogService = this.injector.get(DialogService);
    this.translateService.get('CREATE_SECURITY_TRANSFER').subscribe((title) => {
      const ref = dialogService.open(SecurityTransferCreateDialogComponent, {
        header: title,
        width: '500px',
        modal: true,
        closable: true,
        data: {
          idSecurity: securityPositionSummary.security.idSecuritycurrency,
          securityName: securityPositionSummary.security.name,
          idSecurityaccountSource: this.securityAccount?.idSecuritycashAccount,
          sourceAccountName: this.securityAccount?.name,
          units: securityPositionSummary.units
        }
      });
      if (!ref) return;
      ref.onClose.subscribe((result) => {
        if (result) {
          this.readData();
        }
      });
    });
  }

  protected extendTransactionParamData(transactionCallParam: TransactionCallParam): void {
    transactionCallParam.securityaccount = this.securityAccount;
  }

  protected getOptionalParameters(): OptionalParameters {
    return { idSecurityaccount: this.idSecurityaccount };
  }

  private uploadTransactionFiles(formData: FormData): void {
    // An account without its own import platform can only import through the GT platform; otherwise honour the choice.
    const useGtPlatform = !this.accountHasOwnPlatform || (this.gtPlatformConfigured && this.useGtPlatformDrop);
    formData.append('useGtPlatform', String(useGtPlatform));
    this.importTransactionHeadService
      .uploadPdfFileSecurityAccountTransactions(this.idSecurityaccount, formData)
      .subscribe((sfdit: SuccessFailedDirectImportTransaction) => {
        const data: any = {};
        data[AppSettings.SUCCESS_FAILED_IMP_TRANS] = JSON.stringify(sfdit);
        if (sfdit.failed) {
          this.router.navigate([
            `${BaseSettings.MAINVIEW_KEY}/${AppSettings.SECURITYACCOUNT_TAB_MENU_KEY}`,
            this.idSecurityaccount,
            data
          ]);
        } else {
          // Success created transactions
          this.messageToastService.showMessageI18nEnableHtml(InfoLevelType.INFO, 'CREATED_TRANS_FROM_IMPORT', {
            noOfImportedTransactions: sfdit.noOfImportedTransactions,
            noOfDifferentSecurities: sfdit.noOfDifferentSecurities
          });
          this.readData();
        }
      });
  }
}
