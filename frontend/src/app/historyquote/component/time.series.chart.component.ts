import {ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {HistoryquoteService} from '../service/historyquote.service';
import {ActivatedRoute} from '@angular/router';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {ViewSizeChangedService} from '../../shared/layout/service/view.size.changed.service';
import {TranslateService} from '@ngx-translate/core';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {HelpIds} from '../../shared/help/help.ids';
import {CurrencypairWithTransaction} from '../../entities/view/currencypair.with.transaction';
import {SecurityTransactionSummary} from '../../entities/view/security.transaction.summary';
import {PlotlyLocales} from '../../shared/plotlylocale/plotly.locales';
import {combineLatest, Subscription} from 'rxjs';
import {NameSecuritycurrency} from '../../entities/view/name.securitycurrency';
import {PlotlyHelper} from '../../shared/chart/plotly.helper';
import {TransactionType} from '../../shared/types/transaction.type';
import {AppHelper, Comparison} from '../../shared/helper/app.helper';
import * as moment from 'moment';
import {AppSettings} from '../../shared/app.settings';
import {SecurityTransactionPosition} from '../../entities/view/security.transaction.position';
import {Helper} from '../../helper/helper';
import {MenuItem} from 'primeng/api';
import {
  IndicatorDefinition,
  IndicatorDefinitions,
  TaEditParam,
  TaEditReturn,
  TaFormDefinition,
  TaIndicators,
  TaTraceIndicatorData
} from './indicator.definitions';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {HistoryquoteDateClose} from '../../entities/projection/historyquote.date.close';
import {PlotlyService} from 'angular-plotly.js';

interface Traces {
  [key: string]: { x: string[], y: number[], text: string[] };
}


/**
 * Technical indicator (TA) can only be shown for one security or currency pair. When there are a 2nd trace is added the TA is
 * removed.
 */
@Component({
  template: `
      <div #container class="fullChart" [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}"
           (click)="onComponentClick($event)" (contextmenu)="onRightClick($event)">
          <div class="input-w">
              <label for="fromDate">{{'DATE_FROM' | translate}}</label>
              <p-calendar [(ngModel)]="fromDate" id="fromDate" appendTo="body"
                          monthNavigator="true" yearNavigator="true" [yearRange]="yearRange"
                          [defaultDate]="oldestDate"
                          [locale]="language"
                          (onBlur)="onBlurFromDate($event)"
                          (onSelect)="onBlurFromDate($event)"
                          [dateFormat]="dateFormat"
                          [disabledDays]="[0,6]"
                          [minDate]="oldestDate" [maxDate]="youngestDate"></p-calendar>
              <i class="fa fa-undo fa-border fa-lg" (click)="onResetOldestDate($event)"></i>
              <!--
                <ng-container *ngIf="this.loadedData.length===1">
               -->
              <label>{{'PERCENTAGE' | translate}}</label>
              <input type="checkbox" [(ngModel)]="usePercentage" (change)="togglePercentage($event)">
              <!--
              </ng-container>
              -->
              <label>{{'CONNECT_GAPS' | translate}}</label>
              <input type="checkbox" [(ngModel)]="connectGaps" (change)="toggleConnectGaps($event)">
          </div>
          <div #chart class="plot-container">
          </div>
          <p-contextMenu #contextMenu [model]="contextMenuItems" [target]="container" appendTo="body"></p-contextMenu>
      </div>
      <indicator-edit *ngIf="visibleTaDialog"
                      [visibleDialog]="visibleTaDialog" [taEditParam]="taEditParam"
                      (closeDialog)="handleCloseTaDialog($event)">
      </indicator-edit>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TimeSeriesChartComponent implements OnInit, OnDestroy, IGlobalMenuAttach {
  @ViewChild('container', {static: true}) container: ElementRef;
  @ViewChild('chart', {static: true}) chartElement: ElementRef;

  language;

  fromDate: Date;
  usePercentage: boolean;
  connectGaps = true;
  yearRange: string;
  dateFormat: string;
  loadedData: LoadedData[] = [];
  oldestMatchDate: string;
  youngestMatchDate: string;
  minValueOfY: number;
  maxValueOfY: number;
  subscriptionViewSizeChanged: Subscription;
  subscriptionLatest: Subscription;
  // protected paramMap: ParamMap;
  protected transactionPositionList: SecurityTransactionPosition[] = [];

  // For technical indicator edit
  taFormDefinitions: { [key: string]: TaFormDefinition };
  taEditParam: TaEditParam;
  visibleTaDialog: boolean;
  contextMenuItems: MenuItem[] = [];
  indicatorDefinitions: IndicatorDefinitions;


  readonly compareHistoricalFN = (h, o) => h.date === o ? Comparison.EQ : h.date > o ? Comparison.GT : Comparison.LT;
  readonly compareXaxisFN = (x, b) => x === b ? Comparison.EQ : x > b ? Comparison.GT : Comparison.LT;


  constructor(private plotlyService: PlotlyService,
              private messageToastService: MessageToastService,
              private usersettingsService: UserSettingsService,
              private viewSizeChangedService: ViewSizeChangedService,
              private securityService: SecurityService,
              private currencypairService: CurrencypairService,
              private gps: GlobalparameterService,
              private changeDetectionStrategy: ChangeDetectorRef,
              private historyquoteService: HistoryquoteService,
              private activatedRoute: ActivatedRoute,
              private translateService: TranslateService,
              private activePanelService: ActivePanelService) {

    this.dateFormat = gps.getCalendarTwoNumberDateFormat().toLocaleLowerCase();
    this.language = Helper.CALENDAR_LANG[gps.getUserLang()];
    this.yearRange = `2000:${new Date().getFullYear()}`;
    this.indicatorDefinitions = new IndicatorDefinitions();
  }

  get oldestDate(): Date {
    return this.oldestMatchDate ? new Date(this.oldestMatchDate) : null;
  }

  get youngestDate(): Date {
    return this.youngestMatchDate ? new Date(this.youngestMatchDate) : null;
  }


  ///////////////////////////////////////////////////////
  // Chart methods
  ///////////////////////////////////////////////////////

  ngOnInit(): void {
    this.activePanelService.registerPanel(this);
    // this.setCalendarRange();
    this.activatedRoute.paramMap.subscribe(paramMap => {
      const paramObject = AppHelper.createParamObjectFromParamMap(paramMap);
      this.prepareChart(paramObject.allParam);
    });

    this.historyquoteService.getAllTaForms().subscribe(formDefinition => this.taFormDefinitions = formDefinition);
  }

  ngOnDestroy(): void {
    this.subscriptionLatest && this.subscriptionLatest.unsubscribe();
    this.subscriptionViewSizeChanged && this.subscriptionViewSizeChanged.unsubscribe();
    this.activePanelService.destroyPanel(this);
  }

  prepareChart(timeSeriesParams: TimeSeriesParam[]): void {

    if (timeSeriesParams.length === 1 || timeSeriesParams.length - 1 !== this.loadedData.length) {
      this.loadedData = [];
    }

    if (timeSeriesParams.length === 2) {
      this.usePercentage = true;
    }

    for (let i = this.loadedData.length; i < timeSeriesParams.length; i++) {
      const stsObservable = (timeSeriesParams[i].isCurrencypair)
        ? this.currencypairService.getTransactionForCurrencyPair(timeSeriesParams[i].idSecuritycurrency)
        : BusinessHelper.setSecurityTransactionSummary(this.securityService,
          timeSeriesParams[i].idSecuritycurrency, timeSeriesParams[i].idSecurityaccount ? [timeSeriesParams[i].idSecurityaccount] : null,
          timeSeriesParams[i].idPortfolio, true);

      const historyquoteObservable = this.historyquoteService.getDateCloseByIdSecuritycurrency(timeSeriesParams[i].idSecuritycurrency);
      this.subscriptionLatest = combineLatest([stsObservable, historyquoteObservable]).subscribe((data: any[]) => {

        const nameSecuritycurrency = (timeSeriesParams[i].isCurrencypair)
          ? new CurrencypairWithTransaction(data[0])
          : new SecurityTransactionSummary(data[0].transactionPositionList, data[0].securityPositionSummary);

        if ((<HistoryquoteDateClose[]>data[1]).length > 0) {
          this.createTodayAsHistoryquote(nameSecuritycurrency, data[1]);
          this.loadedData.push(new LoadedData(timeSeriesParams[i].idSecuritycurrency, nameSecuritycurrency, data[1],
            timeSeriesParams[i].isCurrencypair));


          if (i === timeSeriesParams.length - 1) {
            this.prepareLoadedDataAndPlot(false);
          }
        } else {
          this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'HISTORIC_NO_DATA',
            {securityName: nameSecuritycurrency.getName()});
        }
      });
    }
  }

  onBlurFromDate(event): void {
    if (!this.fromDate || this.fromDate.getTime() < this.oldestDate.getTime()) {
      this.fromDate = this.oldestDate;
    } else if (this.fromDate.getTime() > new Date().getTime()) {
      this.fromDate = new Date();
    }

    this.matchEveryHistoryquotesYoungestDate();
    this.prepareLoadedDataAndPlot(true);
  }

  onResetOldestDate(event): void {
    this.fromDate = null;
    this.onBlurFromDate(event);
  }

  togglePercentage(event): void {
    this.prepareLoadedDataAndPlot(true);
  }

  toggleConnectGaps(event): void {
    this.prepareLoadedDataAndPlot(true);
  }

  getHistoricalLineTrace(loadedData: LoadedData): any {
    let foundStartIndex = Math.abs(AppHelper.binarySearch(loadedData.historyquotes,
      moment(this.fromDate).format(AppSettings.FORMAT_DATE_SHORT_NATIVE), this.compareHistoricalFN));
    // const foundEndIndex = AppHelper.binarySearch(loadedData.historyquotes, this.youngestDate, this.compareHistoricalFN);

    while (loadedData.historyquotes[foundStartIndex].close === null) {
      foundStartIndex++;
    }

    loadedData.factor = 100 / loadedData.historyquotes[foundStartIndex].close;
    return {
      type: 'scatter',
      mode: 'lines',
      name: AppHelper.truncateString(loadedData.nameSecuritycurrency.getName(), 25, true),
      x: loadedData.historyquotes.slice(foundStartIndex, loadedData.historyquotes.length).map(historyquote => historyquote.date),
      y: loadedData.historyquotes.slice(foundStartIndex, loadedData.historyquotes.length).map(historyquote =>
        (this.usePercentage && historyquote.close != null) ? historyquote.close * loadedData.factor - 100 : historyquote.close),
      line: {width: 1},
      connectgaps: this.connectGaps
    };
  }


  getBuySellDivMarkForCurrency(traces: Traces, historicalLine: any, currencypairWithTransaction: CurrencypairWithTransaction): Traces {

    const sizes: { [key: string]: { size: number[] } } = {};
    let minAmount = Number.MAX_VALUE;
    let maxAmount = 0;

    currencypairWithTransaction.transactionList.filter(transaction => transaction.transactionTime >= this.fromDate.getTime())
      .forEach(transaction => {

        const transactionType: TransactionType = TransactionType[transaction.transactionType];

        const transactionTypeStr: string = transactionType === TransactionType.DEPOSIT ? TransactionType[TransactionType.ACCUMULATE] :
          transactionType === TransactionType.WITHDRAWAL ? TransactionType[TransactionType.REDUCE] : transaction.transactionType;

        if (transactionType === TransactionType.DEPOSIT || transactionType === TransactionType.WITHDRAWAL
          || transactionType === TransactionType.ACCUMULATE || transactionType === TransactionType.REDUCE) {
          let amount = Math.abs(transaction.cashaccountAmount);
          if (amount > 50) {
            if (!traces[transactionTypeStr]) {
              traces[transactionTypeStr] = this.initializeBuySellTrace(transactionTypeStr, transactionType);
              sizes[transactionTypeStr] = {size: []};
            }
            const transactionDateStr = moment(transaction.transactionTime).format(AppSettings.FORMAT_DATE_SHORT_NATIVE);

            traces[transactionTypeStr].x.push(transactionDateStr);
            traces[transactionTypeStr].text.push(transaction.cashaccount.name);

            switch (transactionType) {
              case TransactionType.ACCUMULATE:
              case TransactionType.REDUCE:
                amount = amount / transaction.currencyExRate;
                break;
              case TransactionType.DEPOSIT:
              case TransactionType.WITHDRAWAL:

                break;
            }
            if (this.loadedData.length > 1 || this.usePercentage) {
              const foundIndex = AppHelper.binarySearch(historicalLine.x, transactionDateStr, this.compareXaxisFN);
              traces[transactionTypeStr].y.push(historicalLine.y[Math.abs(foundIndex)]);
            } else {
              traces[transactionTypeStr].y.push(transaction.currencyExRate);
              sizes[transactionTypeStr].size.push(amount);
              minAmount = Math.min(minAmount, amount);
              maxAmount = Math.max(maxAmount, amount);
            }
          }
        }
      });

    if (this.loadedData.length === 1 && !this.usePercentage) {
      const maxAmountSqrt = Math.sqrt(maxAmount);
      const minAmountSqrt = Math.sqrt(minAmount);
      const step = (maxAmountSqrt - minAmountSqrt) ? 18 / (maxAmountSqrt - minAmountSqrt) : 0;

      Object.entries(traces).forEach(
        ([key, value]) => {
          (<any>value).marker = {size: sizes[key].size.map(amount => (Math.sqrt(amount) - minAmountSqrt) * step + 6)};
        }
      );
    }
    return traces;
  }

  getBuySellDivMarkForSecurity(traces: Traces, historicalLine: any, securityTransactionSummary: SecurityTransactionSummary): Traces {
    securityTransactionSummary.transactionPositionList.filter(stp => stp.transaction.transactionTime >= this.fromDate.getTime())
      .forEach(securityTransactionPosition => {

        const transaction = securityTransactionPosition.transaction;
        const transactionTypeStr = transaction.transactionType;
        const transactionType: TransactionType = TransactionType[transactionTypeStr];

        if (transactionType === TransactionType.ACCUMULATE || transactionType === TransactionType.REDUCE
          || transactionType === TransactionType.DIVIDEND) {
          if (!traces[transactionTypeStr]) {
            traces[transactionTypeStr] = this.initializeBuySellTrace(transactionTypeStr, transactionType);
          }

          const transactionDateStr = moment(transaction.transactionTime).format(AppSettings.FORMAT_DATE_SHORT_NATIVE);
          traces[transactionTypeStr].x.push(transactionDateStr);
          if (transactionType === TransactionType.DIVIDEND || this.loadedData.length > 1 || this.usePercentage) {
            const foundIndex = AppHelper.binarySearch(historicalLine.x, transactionDateStr, this.compareXaxisFN);
            traces[transactionTypeStr].y.push(historicalLine.y[Math.abs(foundIndex)]);
          } else {
            traces[transactionTypeStr].y.push(securityTransactionPosition.quotationSplitCorrection
              ? securityTransactionPosition.quotationSplitCorrection : transaction.quotation);
          }
          traces[transactionTypeStr].text.push(transaction.cashaccount.name);
        }
      });

    return traces;
  }

  chartDataPointClicked(dataPointIndex: number) {
  }

  initializeBuySellTrace(transactionTypeStr: string, transactionType: TransactionType) {
    const trace = {
      x: [], y: [], text: [],
      mode: 'markers',
      type: 'scatter',
      opacity: 0.75,
      name: transactionTypeStr,
      textfont: {
        family: 'Times New Roman'
      },
      marker: {size: transactionType === TransactionType.DIVIDEND ? 6 : 8}
    };
    this.translateTrace(trace);
    return trace;
  }

  translateTrace(trace) {
    this.translateService.get(trace.name).subscribe(transText => trace.name = transText);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    this.activePanelService.activatePanel(this, {
      showMenu: this.getShowMenu()
    });
  }

  onRightClick(event): void {
    this.resetMenu();
  }

  private resetMenu(): void {
    this.contextMenuItems = this.getShowMenu();
    this.activePanelService.activatePanel(this, {
      showMenu: this.contextMenuItems,
      editMenu: null
    });
  }

  private getShowMenu() {
    const menuItems: MenuItem[] = [];
    this.indicatorDefinitions.defMap.forEach((iDef: IndicatorDefinition, taIndicators: TaIndicators) => {
      iDef.menuItem = {
        label: TaIndicators[taIndicators],
        icon: (iDef.shown) ? AppSettings.ICONNAME_SQUARE_CHECK : AppSettings.ICONNAME_SQUARE_EMTPY,
        command: (event) => this.onOffIndicatorEvent(event, TaIndicators[taIndicators], iDef),
        disabled: this.loadedData.length !== 1,
        items: [{
          label: `EDIT_RECORD|${TaIndicators[taIndicators]}`,
          command: () => this.editIndicatorParam(iDef, TaIndicators[taIndicators])
        }]
      };
      menuItems.push(iDef.menuItem);
    });

    TranslateHelper.translateMenuItems(menuItems, this.translateService, false);
    return menuItems;
  }

  private onOffIndicatorEvent(event, taIndicators: string, iDef: IndicatorDefinition): void {
    this.onOffIndicator(event.item, taIndicators, iDef);
  }

  private onOffIndicator(menuItem: MenuItem, taIndicators: string, iDef: IndicatorDefinition): void {
    if (menuItem.icon === AppSettings.ICONNAME_SQUARE_EMTPY) {
      this.loadAndShowIndicatorDataWithMenu(taIndicators, iDef);
    } else {
      this.deleteTaIndicator(iDef);
    }
  }


  private editIndicatorParam(iDef: IndicatorDefinition, taIndicators: string): void {
    const taFormDefinition: TaFormDefinition = this.taFormDefinitions[taIndicators];
    const dataModel = this.usersettingsService.retrieveObject(AppSettings.TA_INDICATORS_STORE + taIndicators);
    this.taEditParam = new TaEditParam(taIndicators,
      dataModel ? dataModel : taFormDefinition.defaultDataModel,
      DynamicFieldHelper.createConfigFieldsFromDescriptor(taFormDefinition.taFormList, '', true, 'APPLY'));
    this.visibleTaDialog = true;
   // this.changeDetectionStrategy.markForCheck();
  }

  handleCloseTaDialog(processedActionData: ProcessedActionData): void {
    this.visibleTaDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      const taEditReturn: TaEditReturn = processedActionData.data;
      const taFormDefinition: TaFormDefinition = this.taFormDefinitions[taEditReturn.taIndicators];
      const iDef: IndicatorDefinition = this.indicatorDefinitions.defMap.get(TaIndicators[taEditReturn.taIndicators]);
      this.deleteShownTaTraceWhenInputDataHasChanged(taEditReturn.taIndicators, iDef, taEditReturn.taDynamicDataModel,
        taFormDefinition);
      this.usersettingsService.saveObject(AppSettings.TA_INDICATORS_STORE + taEditReturn.taIndicators,
        taEditReturn.taDynamicDataModel);
      this.loadAndShowIndicatorDataWithMenu(taEditReturn.taIndicators, iDef);
    }
  }

  private loadAndShowIndicatorDataWithMenu(taIndicators: string, iDef: IndicatorDefinition): void {
    iDef.menuItem.icon = AppSettings.ICONNAME_SQUARE_CHECK;
    iDef.shown = true;
    this.loadAndShowIndicatorData(taIndicators, iDef);
  }

  private loadAndShowIndicatorData(taIndicators: string, iDef: IndicatorDefinition): void {
    const dataModel = this.usersettingsService.retrieveObject(AppSettings.TA_INDICATORS_STORE + taIndicators)
      || this.taFormDefinitions[taIndicators].defaultDataModel;
    this.indicatorDefinitions.idSecuritycurrency = this.loadedData[0].idSecuritycurrency;
    this.historyquoteService.getTaWithShortMediumLongInputPeriod(taIndicators, this.indicatorDefinitions.idSecuritycurrency,
      dataModel).subscribe((taTraceIndicatorDataList: TaTraceIndicatorData[]) => {
      // const iDef: IndicatorDefinition = this.indicatorDefinitions.defMap.get(TaIndicators[taIndicators]);
      iDef.taTraceIndicatorDataList = taTraceIndicatorDataList;
      this.createTaTrace(iDef);
    });
  }


  private deleteShownTaTraceWhenInputDataHasChanged(taIndicator: string, iDef: IndicatorDefinition, newDataModel: any,
                                                    taFormDefinition: TaFormDefinition): void {
    if (iDef.shown) {
      const existingDataModel = this.usersettingsService.retrieveObject(AppSettings.TA_INDICATORS_STORE + taIndicator);
      if (!DynamicFieldHelper.isDataModelEqual(existingDataModel, newDataModel, taFormDefinition.taFormList)) {
        this.deleteTaIndicator(iDef);
      }
    }
  }

  private deleteTaIndicator(iDef: IndicatorDefinition) {
    if (this.loadedData.length > 1) {
      // iDef.menuItem.disabled = true;
    }
    if (iDef.shown) {
      iDef.menuItem.icon = AppSettings.ICONNAME_SQUARE_EMTPY;
      iDef.shown = false;
      const traceIndices: number[] = iDef.taTraceIndicatorDataList.map(taT => taT.traceIndex).reverse();
      this.plotlyService.getPlotly().deleteTraces(this.chartElement.nativeElement, traceIndices);
      iDef.taTraceIndicatorDataList.forEach(taT => taT.traceIndex = undefined);
      this.adjustTATraceIndices(traceIndices);
    }
  }

  private adjustTATraceIndices(traceIndices: number[]): void {
    for (const index of traceIndices) {
      this.indicatorDefinitions.defMap.forEach((iDefI: IndicatorDefinition, taIndicators: TaIndicators) => {
        if (iDefI.shown) {
          iDefI.taTraceIndicatorDataList.forEach(ttid =>
            ttid.traceIndex = ttid.traceIndex > index ? ttid.traceIndex - 1 : ttid.traceIndex);
        }
      });
    }
  }

  private createTaTrace(iDef: IndicatorDefinition): void {
    iDef.taTraceIndicatorDataList.forEach((taTraceIndicatorData: TaTraceIndicatorData) => {
      const foundStartIndex = AppHelper.binarySearch(taTraceIndicatorData.taIndicatorData,
        moment(this.fromDate).format(AppSettings.FORMAT_DATE_SHORT_NATIVE), this.compareHistoricalFN);
      this.plotlyService.getPlotly().addTraces(this.chartElement.nativeElement, {
        type: 'scatter',
        mode: 'lines',
        name: `${taTraceIndicatorData.traceName} (${taTraceIndicatorData.period})`,
        x: taTraceIndicatorData.taIndicatorData.slice(foundStartIndex, taTraceIndicatorData.taIndicatorData.length)
          .map(taIndicatorData => taIndicatorData.date),
        y: taTraceIndicatorData.taIndicatorData.slice(foundStartIndex, taTraceIndicatorData.taIndicatorData.length)
          .map(taIndicatorData => (this.usePercentage)
            ? taIndicatorData.value * this.loadedData[0].factor - 100 : taIndicatorData.value),
        line: {width: 1}
      });
      taTraceIndicatorData.traceIndex = this.chartElement.nativeElement.data.length - 1;
    });
   // this.changeDetectionStrategy.markForCheck();
  }

  private redoTaAfterDateChange(): void {
    this.indicatorDefinitions.defMap.forEach((iDef: IndicatorDefinition, taIndicators: TaIndicators) => {
      if (iDef.shown) {
        if (this.loadedData.length === 1) {
          if (this.indicatorDefinitions.idSecuritycurrency === this.loadedData[0].idSecuritycurrency) {
            this.createTaTrace(iDef);
          } else {
            this.loadAndShowIndicatorData(TaIndicators[taIndicators], iDef);
          }
        }
      }
    });
  }


  callMeDeactivate(): void {
   // this.changeDetectionStrategy.markForCheck();
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_WATCHLIST_HISTORYQUOTES_CHART;
  }

  hideContextMenu(): void {
  }

  private prepareLoadedDataAndPlot(userUserInputDate: boolean) {
    const element = this.chartElement.nativeElement;
    !userUserInputDate && this.evaluateOldestYoungestMatchDate();
    let traces = this.createAllQuotesLines();
    traces = traces.concat(this.createAllMarkerTraces());

    const layout: any = this.getLayout();
    this.plot(element, traces, layout, userUserInputDate);
    this.redoTaAfterDateChange();
  }

  /**
   * Attention: Year range of calendar can only set once
   */
  private setCalendarRange(): void {
    if (this.oldestDate && this.youngestDate) {
      // Not used yet
      this.yearRange = `${this.oldestDate.getFullYear()}:${this.youngestDate.getFullYear()}`;
    } else {
      this.gps.getStartFeedDateAsTime().subscribe(time => {
          this.yearRange = `${new Date(time).getFullYear()}:${new Date().getFullYear()}`;
        }
      );
    }
  }

  private evaluateOldestYoungestMatchDate(): void {
    this.oldestMatchDate = null;
    this.youngestMatchDate = null;
    for (const ld of this.loadedData) {
      this.oldestMatchDate = !this.oldestMatchDate ? ld.historyquotes[0].date : ld.historyquotes[0].date > this.oldestMatchDate ?
        ld.historyquotes[0].date : this.oldestMatchDate;
      const maxIndex = ld.historyquotes.length - 1;
      this.youngestMatchDate = !this.youngestMatchDate ? ld.historyquotes[maxIndex].date :
        ld.historyquotes[maxIndex].date < this.youngestMatchDate ? ld.historyquotes[maxIndex].date : this.youngestMatchDate;
    }
    this.matchEveryHistoryquotesYoungestDate();
    if (!this.fromDate || this.loadedData.length === 1 || this.oldestDate.getTime() > this.fromDate.getTime()) {
      this.fromDate = this.oldestDate;
    }
  }

  private matchEveryHistoryquotesYoungestDate(): void {
    let i = 0;
    while (i < this.loadedData.length) {
      const foundStartIndex = AppHelper.binarySearch(this.loadedData[i].historyquotes,
        moment(this.fromDate).format(AppSettings.FORMAT_DATE_SHORT_NATIVE), this.compareHistoricalFN);
      if (foundStartIndex < 0) {
        this.fromDate = moment(this.fromDate).add(1, 'days').toDate();
        i = 0;
        continue;
      }
      i++;
    }
  }

  private createAllQuotesLines(): any[] {
    const traces: any[] = [];
    for (const ld of this.loadedData) {
      ld.historicalLine = this.getHistoricalLineTrace(ld);
      this.minValueOfY = Math.min(this.minValueOfY, ...ld.historicalLine.y);
      this.maxValueOfY = Math.max(this.maxValueOfY, ...ld.historicalLine.y);
      traces.push(ld.historicalLine);
    }
    return traces;
  }

  private createAllMarkerTraces(): any[] {
    const traces: Traces = {};
    for (const ld of this.loadedData) {
      if (ld.isCurrencypair) {
        this.getBuySellDivMarkForCurrency(traces, ld.historicalLine, <CurrencypairWithTransaction>ld.nameSecuritycurrency);
      } else {
        this.getBuySellDivMarkForSecurity(traces, ld.historicalLine, <SecurityTransactionSummary>ld.nameSecuritycurrency);
      }
    }
    return Object.values(traces);
  }

  private plot(element: any, traces: any, layout: any, userUserInputDate: boolean): void {
    const config = PlotlyLocales.setPlotyLocales(this.plotlyService.getPlotly(), this.gps);
    config.modeBarButtonsToRemove = ['lasso2d', 'select2d'];
    config.displaylogo = false;
    this.plotlyService.getPlotly().purge(this.chartElement.nativeElement);
    this.plotlyService.getPlotly().newPlot(element, traces, layout, config);
  // this.changeDetectionStrategy.markForCheck();
    PlotlyHelper.registerPlotlyClick(element, this.chartDataPointClicked.bind(this));
    if (!this.subscriptionViewSizeChanged) {

      this.subscriptionViewSizeChanged = this.viewSizeChangedService.viewSizeChanged$.subscribe(changedViewSizeType =>
        this.plotlyService.getPlotly().Plots.resize(element));
    }
  }

  private createTodayAsHistoryquote(nameSecuritycurrency: NameSecuritycurrency, historyquotes: HistoryquoteDateClose[]) {
    if (nameSecuritycurrency.getSecuritycurrency().sLast) {
      const dateLastStr = moment(nameSecuritycurrency.getSecuritycurrency().sTimestamp).format(AppSettings.FORMAT_DATE_SHORT_NATIVE);
      if (historyquotes.length === 0 || dateLastStr > historyquotes[historyquotes.length - 1].date) {
        const historyquoteToday: HistoryquoteDateClose = {
          date: dateLastStr, close: nameSecuritycurrency.getSecuritycurrency().sLast
        };
        historyquotes.push(historyquoteToday);
      }
    }
  }

  private getLayout(): any {

    const dateNative = moment(this.fromDate).format(AppSettings.FORMAT_DATE_SHORT_NATIVE);

    const layout = {
      title: 'LINE_CHART',
      showlegend: true,
      legend: PlotlyHelper.getLegendUnderChart(11),
      xaxis: {
        autorange: false,
        range: [dateNative, this.youngestMatchDate],
        rangeselector: {
          buttons: this.getRangeSelectorButtons(),
        },
        rangeslider: {
          autorange: false,
          range: [dateNative, this.youngestMatchDate]
        },
        type: 'date'
      },
      yaxis: {
        autorange: true,
        range: [this.minValueOfY, this.maxValueOfY],
        ticksuffix: this.loadedData.length > 1 || this.usePercentage ? '%' : '',
        type: 'linear'
      }
    };
    PlotlyHelper.translateLayout(this.translateService, layout);
    return layout;
  }

  private getRangeSelectorButtons(): any {
    const duration = moment.duration(moment(this.youngestDate).diff(moment(this.fromDate)));
    const months = duration.asMonths();
    const buttons: any[] = [];
    if (months >= 1) {
      buttons.push({count: 1, label: '1m', step: 'month', stepmode: 'backward'});
    }
    if (months >= 6) {
      buttons.push({count: 6, label: '6m', step: 'month', stepmode: 'backward'});
    }
    buttons.push({label: 'ALL', step: 'all'});
    return buttons;
  }


}

export class TimeSeriesParam {
  constructor(public idSecuritycurrency: number, public isCurrencypair: boolean,
              public idPortfolio: number, public idSecurityaccount: number, fileGaps = true) {
  }
}

class LoadedData {
  public factor: number;
  public historicalLine: any;

  constructor(public idSecuritycurrency: number, public nameSecuritycurrency: (CurrencypairWithTransaction | SecurityTransactionSummary),
              public historyquotes: HistoryquoteDateClose[], public isCurrencypair: boolean) {
  }
}



