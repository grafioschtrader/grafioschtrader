import { requireInitialized } from '../../lib/helper/required-state';
import { inject, Service } from '@angular/core';
import { AppSettings } from '../../shared/app.settings';
import { MenuItem } from '@openng/optimus-ui/api';
import { Router } from '@angular/router';
import { TimeSeriesParam } from '../model/time.series.param';
import { BaseSettings } from '../../lib/base.settings';
import { TransactionReceiptService } from '../../transaction/service/transaction.receipt.service';

/**
 * Menu and function to show a history quotes as chart or table. It is shown in additional area.
 */
@Service()
export class TimeSeriesQuotesService {
  private router = inject(Router);
  private transactionReceiptService = inject(TransactionReceiptService);

  private initializedIdSecuritycurrency?: number;

  private get idSecuritycurrency(): number {
    return requireInitialized(this.initializedIdSecuritycurrency, 'TimeSeriesQuotesService.idSecuritycurrency');
  }

  private set idSecuritycurrency(value: number) {
    this.initializedIdSecuritycurrency = value;
  }
  private currencySecurity: string | null = null;
  private optionalParameters: OptionalParameters = { idPortfolio: null, idSecurityaccount: null, noMarketValue: false };
  private timeSeriesParams: TimeSeriesParam[] = [];

  getMenuItems(
    idSecuritycurrency: number,
    currencySecurity: string | null,
    addTopSeparator: boolean,
    optionalParameters?: OptionalParameters
  ): MenuItem[] {
    this.idSecuritycurrency = idSecuritycurrency;
    this.currencySecurity = currencySecurity;
    if (optionalParameters) {
      this.optionalParameters = optionalParameters;
    }
    const isTimeSeriesShown = this.isTimeSeriesShown();
    if (!isTimeSeriesShown) {
      this.timeSeriesParams = [];
    }

    const menuItems: MenuItem[] = [];
    addTopSeparator && menuItems.push({ separator: true });
    menuItems.push({
      label: 'LINE_CHART',
      command: (e) => this.showEodChartTable(AppSettings.TIME_SERIE_QUOTES, true)
    });
    menuItems.push({
      label: 'ADD_TO_LINE_CHART',
      command: (e) => this.showEodChartTable(AppSettings.TIME_SERIE_QUOTES, false),
      disabled: !isTimeSeriesShown
    });
    if (!this.optionalParameters.noMarketValue) {
      menuItems.push({
        label: 'HISTORY_QUOTES_TABLE',
        command: (e) => this.showEodChartTable(AppSettings.HISTORYQUOTE_P_KEY, true)
      });
      menuItems.push({
        label: 'SEASONALITY',
        command: (e) => this.showEodChartTable(AppSettings.SEASONALITY, true)
      });
    }
    if (currencySecurity) {
      // Securities only: currency pairs pass null as currencySecurity and have no security transactions.
      menuItems.push({ separator: true });
      menuItems.push({
        label: 'TRANSACTION_RECEIPTS' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: (e) =>
          this.transactionReceiptService.openDialog(
            this.idSecuritycurrency,
            this.optionalParameters.idPortfolio,
            this.optionalParameters.idSecurityaccount
          )
      });
    }
    return menuItems;
  }

  protected isTimeSeriesShown(): boolean {
    const routeUrlComponents = /.*mainbottom:(\w*)/.exec(this.router.url);
    return routeUrlComponents?.[1] === AppSettings.TIME_SERIE_QUOTES;
  }

  private showEodChartTable(routeKey: string, initializeTimeSeriesParam: boolean): void {
    if (initializeTimeSeriesParam) {
      this.timeSeriesParams = [];
    }
    this.timeSeriesParams.push(
      new TimeSeriesParam(
        this.idSecuritycurrency,
        this.currencySecurity,
        this.optionalParameters.idPortfolio,
        this.optionalParameters.idSecurityaccount
      )
    );
    this.router.navigate([
      BaseSettings.MAINVIEW_KEY + '/',
      {
        outlets: {
          mainbottom: [
            routeKey,
            {
              allParam: JSON.stringify(this.timeSeriesParams)
            }
          ]
        }
      }
    ]);
  }
}

export interface OptionalParameters {
  idPortfolio?: number | null;
  idSecurityaccount?: number | null;
  noMarketValue?: boolean | null;
}
