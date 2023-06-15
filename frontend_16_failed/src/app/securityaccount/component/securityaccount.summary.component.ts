import {Component, NgZone, OnDestroy, OnInit} from '@angular/core';
import {SecurityaccountService} from '../service/securityaccount.service';
import {ActivatedRoute, Params, Router} from '@angular/router';

import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';
import {SecurityaccountTable} from './securityaccountTable';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {Subscription} from 'rxjs';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {HelpIds} from '../../shared/help/help.ids';
import {OptionalParameters, TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {
  ImportTransactionHeadService,
  SuccessFailedDirectImportTransaction
} from '../service/import.transaction.head.service';

import {InfoLevelType} from '../../shared/message/info.leve.type';
import {AppSettings} from '../../shared/app.settings';
import {NgxFileDropEntry} from 'ngx-file-drop';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {FilterService} from 'primeng/api';
import {AppHelper} from '../../shared/helper/app.helper';

/**
 * It is the summary for a single security account with its securities.
 */
@Component({
  templateUrl: '../view/securityaccount.table.html'
})
export class SecurityaccountSummaryComponent extends SecurityaccountTable implements OnInit, OnDestroy {

  private routeSubscribe: Subscription;
  private idSecurityaccount: number;

  constructor(private ngZone: NgZone,
              protected importTransactionHeadService: ImportTransactionHeadService,
              timeSeriesQuotesService: TimeSeriesQuotesService,
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
              usersettingsService: UserSettingsService) {
    super(timeSeriesQuotesService, activePanelService, messageToastService, securityaccountService, productIconService,
      activatedRoute, router, chartDataService, filterService, translateService, gps, usersettingsService);
  }

  ngOnInit() {
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      this.idSecurityaccount = +params['id'];
      this.securityAccount = JSON.parse(params[AppSettings.SECURITYACCOUNT.toLowerCase()]);
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

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT;
  }

  ngOnDestroy(): void {
    super.destroy();
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
  }

  public dropped(files: NgxFileDropEntry[]): void {
    AppHelper.processDroppedFiles(files, this.messageToastService, 'pdf', this.uploadTransactionFiles.bind(this));
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
        const data = {object: JSON.stringify(this.securityAccount)};
        data[AppSettings.SUCCESS_FAILED_IMP_TRANS] = JSON.stringify(sfdit);
        if (sfdit.failed) {
          this.ngZone.run(() => this.router.navigate([`${AppSettings.MAINVIEW_KEY}/${AppSettings.SECURITYACCOUNT_TAB_MENU_KEY}`,
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


