import {Component, Injector, NgZone, OnDestroy, OnInit} from '@angular/core';
import {SecurityaccountService} from '../service/securityaccount.service';
import {ActivatedRoute, Params, Router} from '@angular/router';

import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';
import {SecurityaccountTable} from './securityaccountTable';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {Subscription} from 'rxjs';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {HelpIds} from '../../lib/help/help.ids';
import {OptionalParameters, TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {
  ImportTransactionHeadService,
  SuccessFailedDirectImportTransaction
} from '../../imptransaction/service/import.transaction.head.service';

import {InfoLevelType} from '../../lib/message/info.leve.type';
import {AppSettings} from '../../shared/app.settings';
import {NgxFileDropEntry, NgxFileDropModule} from 'ngx-file-drop';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {FilterService, MenuItem} from 'primeng/api';
import {AppHelper} from '../../lib/helper/app.helper';
import {AlarmSetupService} from '../../algo/service/alarm.setup.service';
import {BaseSettings} from '../../lib/base.settings';
import {TreeNavigationStateService} from '../../lib/maintree/service/tree.navigation.state.service';
import {Securityaccount} from '../../entities/securityaccount';
import {SecurityPositionSummary} from '../../entities/view/security.position.summary';
import {SecurityTransferCreateComponent} from '../../securityaction/component/security-transfer-create.component';
import {DialogService} from 'primeng/dynamicdialog';
import {CommonModule} from '@angular/common';
import {TableModule} from 'primeng/table';
import {DatePicker} from 'primeng/datepicker';
import {FormsModule} from '@angular/forms';
import {SelectModule} from 'primeng/select';
import {TooltipModule} from 'primeng/tooltip';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TransactionSecurityTableComponent} from '../../transaction/component/transaction-security-table.component';
import {TransactionSecurityMarginTreetableComponent} from '../../transaction/component/transaction-security-margin-treetable.component';
import {TransactionCashaccountTableComponent} from '../../transaction/component/transaction-cashaccount-table.component';
import {TransactionSecurityEditComponent} from '../../transaction/component/transaction-security-edit.component';

/**
 * It is the summary for a single security account with its securities.
 */
@Component({
    templateUrl: '../view/securityaccount.table.html',
    standalone: true,
    imports: [CommonModule, TranslateModule, TableModule, DatePicker, FormsModule, SelectModule, TooltipModule, ContextMenuModule,
      NgxFileDropModule, TransactionSecurityTableComponent, TransactionSecurityMarginTreetableComponent,
      TransactionCashaccountTableComponent, TransactionSecurityEditComponent]
})
export class SecurityaccountSummaryComponent extends SecurityaccountTable implements OnInit, OnDestroy {

  private routeSubscribe: Subscription;
  private idSecurityaccount: number;

  constructor(private ngZone: NgZone,
              protected importTransactionHeadService: ImportTransactionHeadService,
              timeSeriesQuotesService: TimeSeriesQuotesService,
              alarmSetupService: AlarmSetupService,
              activePanelService: ActivePanelService,
              messageToastService: MessageToastService,
              securityaccountService: SecurityaccountService,
              productIconService: ProductIconService,
              activatedRoute: ActivatedRoute,
              router: Router,
              chartDataService: ChartDataService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService,
              injector: Injector) {
    super(timeSeriesQuotesService, alarmSetupService, activePanelService, messageToastService, securityaccountService,
      productIconService, activatedRoute, router, chartDataService, filterService, translateService, gps, usersettingsService, injector);
  }

  ngOnInit() {
    const treeNavState = this.injector.get(TreeNavigationStateService);
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      this.idSecurityaccount = +params['id'];
      this.securityAccount = treeNavState.getEntity<Securityaccount>(
        AppSettings.SECURITYACCOUNT_SUMMERY_ROUTE_KEY, this.idSecurityaccount);
      this.readData();
    });
  }

  override readData() {
    this.selectedSecurityPositionSummary = null;
    this.securityaccountService.getPositionSummarySecurityaccount(this.idSecurityaccount, this.securityaccountGroupBase.defaultGroup,
      this.includeClosedPosition, this.untilDate)
      .subscribe((data: SecurityPositionGrandSummary) => {
          this.getDataToView(data);
          this.initTableTextTranslation();
          this.changeToOpenChart();
        }
      );
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT;
  }

  ngOnDestroy(): void {
    super.destroy();
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
  }

  public dropped(files: NgxFileDropEntry[]): void {
    AppHelper.processDroppedFiles(files, this.messageToastService, 'pdf', this.uploadTransactionFiles.bind(this));
  }

  protected override extendEditMenu(menuItems: MenuItem[], securityPositionSummary: SecurityPositionSummary): void {
    menuItems.push({
      label: 'TRANSFER_SECURITY' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: (e) => this.handleTransferSecurity(securityPositionSummary),
      disabled: !securityPositionSummary || securityPositionSummary.units === 0
        || this.isMarginProduct(securityPositionSummary.security)
    });
  }

  private handleTransferSecurity(securityPositionSummary: SecurityPositionSummary): void {
    const dialogService = this.injector.get(DialogService);
    this.translateService.get('CREATE_SECURITY_TRANSFER').subscribe(title => {
      const ref = dialogService.open(SecurityTransferCreateComponent, {
        header: title, width: '500px', modal: true, closable: true,
        data: {
          idSecurity: securityPositionSummary.security.idSecuritycurrency,
          securityName: securityPositionSummary.security.name,
          idSecurityaccountSource: this.securityAccount?.idSecuritycashAccount,
          sourceAccountName: this.securityAccount?.name,
          units: securityPositionSummary.units
        }
      });
      ref.onClose.subscribe(result => {
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
    return {idSecurityaccount: this.idSecurityaccount};
  }

  private uploadTransactionFiles(formData: FormData): void {
    this.importTransactionHeadService.uploadPdfFileSecurityAccountTransactions(this.idSecurityaccount, formData).subscribe(
      (sfdit: SuccessFailedDirectImportTransaction) => {
        const data: any = {};
        data[AppSettings.SUCCESS_FAILED_IMP_TRANS] = JSON.stringify(sfdit);
        if (sfdit.failed) {
          this.ngZone.run(() => this.router.navigate([`${BaseSettings.MAINVIEW_KEY}/${AppSettings.SECURITYACCOUNT_TAB_MENU_KEY}`,
            this.idSecurityaccount, data])).then();
        } else {
          // Success created transactions
          this.messageToastService.showMessageI18nEnableHtml(InfoLevelType.INFO, 'CREATED_TRANS_FROM_IMPORT',
            {
              noOfImportedTransactions: sfdit.noOfImportedTransactions,
              noOfDifferentSecurities: sfdit.noOfDifferentSecurities
            });
          this.readData();
        }
      });
  }

}


