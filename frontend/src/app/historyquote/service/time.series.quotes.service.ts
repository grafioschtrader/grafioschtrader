import {Injectable} from '@angular/core';
import {AppSettings} from '../../shared/app.settings';
import {MenuItem} from 'primeng/api';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {Router} from '@angular/router';
import {TimeSeriesParam} from '../component/time.series.chart.component';

/**
 * Menu and function to show a history quotes as chart or table. It is shown in additional area.
 */
@Injectable()
export class TimeSeriesQuotesService {

  private idSecuritycurrency;
  private currencySecurity: string;
  private optionalParameters: OptionalParameters = {idPortfolio: null, idSecurityaccount: null, noMarketValue: false};
  private timeSeriesParams: TimeSeriesParam[] = [];

  constructor(private activePanelService: ActivePanelService,
              private router: Router) {
  }

  getMenuItems(idSecuritycurrency: number, currencySecurity: string, addTopSeparator: boolean,
               optionalParameters?: OptionalParameters): MenuItem[] {
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
    addTopSeparator && menuItems.push({separator: true});
    menuItems.push(
      {
        label: 'LINE_CHART',
        command: (e) => this.showEodChartTable(AppSettings.TIME_SERIE_QUOTES, true)
      }
    );
    menuItems.push(
      {
        label: 'ADD_TO LINE_CHART',
        command: (e) => this.showEodChartTable(AppSettings.TIME_SERIE_QUOTES, false),
        disabled: !isTimeSeriesShown
      }
    );
    if (!this.optionalParameters.noMarketValue) {
      menuItems.push(
        {
          label: 'HISTORY_QUOTES_TABLE',
          command: (e) => this.showEodChartTable(AppSettings.HISTORYQUOTE_P_KEY, true)
        }
      );
    }
    return menuItems;
  }

  protected isTimeSeriesShown(): boolean {
    const routeUrlComponents: string[] = /.*mainbottom:(\w*)/.exec(this.router.url);
    return routeUrlComponents && routeUrlComponents[1] === AppSettings.TIME_SERIE_QUOTES;
  }

  private showEodChartTable(routeKey: string, initializeTimeSeriesParam: boolean): void {
    if (initializeTimeSeriesParam) {
      this.timeSeriesParams = [];
    }
    this.timeSeriesParams.push(new TimeSeriesParam(this.idSecuritycurrency, this.currencySecurity,
      this.optionalParameters.idPortfolio, this.optionalParameters.idSecurityaccount));
    this.router.navigate([AppSettings.MAINVIEW_KEY + '/', {
      outlets: {
        mainbottom: [routeKey, {
          allParam: JSON.stringify(this.timeSeriesParams)
        }]
      }
    }]);
  }

}

export interface OptionalParameters {
  idPortfolio?: number;
  idSecurityaccount?: number;
  noMarketValue?: boolean;
}
