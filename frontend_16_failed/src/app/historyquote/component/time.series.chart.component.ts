import {Component, ElementRef, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {
  CrossRateRequest,
  CrossRateResponse,
  CurrenciesAndClosePrice,
  CurrencypairService
} from '../../securitycurrency/service/currencypair.service';
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
import {combineLatest, Observable, Subscription} from 'rxjs';
import {PlotlyHelper} from '../../shared/chart/plotly.helper';
import {TransactionType} from '../../shared/types/transaction.type';
import {AppHelper, Comparison} from '../../shared/helper/app.helper';
import * as moment from 'moment';
import {AppSettings} from '../../shared/app.settings';
import {SecurityTransactionPosition} from '../../entities/view/security.transaction.position';
import {MenuItem, SelectItem} from 'primeng/api';
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
import {TwoKeyMap} from '../../shared/helper/two.key.map';
import {Transaction} from '../../entities/transaction';
import {Moment} from 'moment/moment';
import {DynamicFieldModelHelper} from '../../shared/helper/dynamic.field.model.helper';
declare let Plotly: any;

interface Traces {
  [key: string]: { x: string[]; y: number[]; text: string[] };
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
        <label for="fromDate">{{'FROM_DATE' | translate}}</label>
        <p-calendar #cal appendTo="body"
                    baseZIndex="100"
                    [yearRange]="yearRange"
                    [(ngModel)]="fromDate" id="fromDate"
                    [dateFormat]="dateFormat"
                    [disabledDays]="[0,6]"
                    (onBlur)="onBlurFromDate($event)"
                    (onSelect)="onBlurFromDate($event)"
                    [minDate]="startDate" [maxDate]="endDate">
        </p-calendar>
        <i class="fa fa-undo fa-border fa-lg" (click)="onResetOldestDate($event)"></i>
        <button type="button" (click)="fiveDays($event)">5{{"D" | translate}}</button>
        <button type="button" (click)="oneMonth($event)">1M</button>
        <button type="button" (click)="threeMonth($event)">3M</button>
        <button type="button" (click)="yearToDate($event)">YTD</button>
        <button type="button" (click)="oneYear($event)">1{{"Y" | translate}}</button>
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

        <label>{{'CURRENCY' | translate}}</label>
        <p-dropdown [options]="currenciesOptions" [(ngModel)]="requestedCurrency"
                    (onChange)="handleChangeCurrency($event)">
        </p-dropdown>

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
  styles: ['button { border: 0; margin-left: 3px}']

})
export class TimeSeriesChartComponent implements OnInit, OnDestroy, IGlobalMenuAttach {
  @ViewChild('container', {static: true}) container: ElementRef;
  @ViewChild('chart', {static: true}) chartElement: ElementRef;

  startDate = new Date('2000-01-03');
  endDate = new Date();
  fromDate: Date;
  usePercentage: boolean;
  connectGaps = true;
  yearRange: string;
  dateFormat: string;
  loadedData: LoadedData[] = [];
  oldestMatchDate: string;
  youngestMatchDate: string;
  subscriptionViewSizeChanged: Subscription;
  subscriptionLatest: Subscription;
  taEditParam: TaEditParam;
  visibleTaDialog: boolean;
  contextMenuItems: MenuItem[] = [];
  requestedCurrency = '';
  currenciesOptions: SelectItem[] = [{value: '', label: ''}];
  // protected paramMap: ParamMap;
  protected transactionPositionList: SecurityTransactionPosition[] = [];
  private minValueOfY: number;
  private maxValueOfY: number;
  private taFormDefinitions: { [key: string]: TaFormDefinition };
  private indicatorDefinitions: IndicatorDefinitions;

  private crossRateMap = new TwoKeyMap<CurrenciesAndClosePrice>();
  private mainCurrency: string;
  private legendTooltipMap = new Map<string, string>();


  constructor(private messageToastService: MessageToastService,
    private usersettingsService: UserSettingsService,
    private viewSizeChangedService: ViewSizeChangedService,
    private securityService: SecurityService,
    private currencypairService: CurrencypairService,
    private gps: GlobalparameterService,
    private historyquoteService: HistoryquoteService,
    private activatedRoute: ActivatedRoute,
    private translateService: TranslateService,
    private activePanelService: ActivePanelService) {

    this.dateFormat = gps.getCalendarTwoNumberDateFormat().toLocaleLowerCase();
    this.yearRange = `2000:${new Date().getFullYear()}`;
    this.indicatorDefinitions = new IndicatorDefinitions();

  }

  get oldestDate(): Date {
    return this.oldestMatchDate ? new Date(this.oldestMatchDate) : null;
  }

  get youngestDate(): Date {
    return this.youngestMatchDate ? new Date(this.youngestMatchDate) : null;
  }

  readonly compareHistoricalFN = (h, o) => h.date === o ? Comparison.EQ : h.date > o ? Comparison.GT : Comparison.LT;
  readonly compareXaxisFN = (x, b) => x === b ? Comparison.EQ : x > b ? Comparison.GT : Comparison.LT;

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
      this.requestedCurrency = '';
    }
    if (timeSeriesParams.length === 2) {
      this.usePercentage = true;
    }

    for (let i = this.loadedData.length; i < timeSeriesParams.length; i++) {
      const stsObservable = timeSeriesParams[i].currencySecurity
        ? BusinessHelper.setSecurityTransactionSummary(this.securityService,
          timeSeriesParams[i].idSecuritycurrency, timeSeriesParams[i].idSecurityaccount
            ? [timeSeriesParams[i].idSecurityaccount] : null,
          timeSeriesParams[i].idPortfolio, true)
        : this.currencypairService.getTransactionForCurrencyPair(timeSeriesParams[i].idSecuritycurrency, true);

      const historyquoteObservable = this.historyquoteService.getDateCloseByIdSecuritycurrency(timeSeriesParams[i].idSecuritycurrency);
      const observable: Observable<any>[] = [stsObservable, historyquoteObservable];
      this.addCurrencyCrossRateObservable(timeSeriesParams, timeSeriesParams[i].currencySecurity, observable);

      this.subscriptionLatest = combineLatest(observable).subscribe((data: any[]) => {
        const nameSecuritycurrency = timeSeriesParams[i].currencySecurity
          ? new SecurityTransactionSummary(data[0].transactionPositionList, data[0].securityPositionSummary)
          : new CurrencypairWithTransaction(data[0]);

        if ((<HistoryquoteDateClose[]>data[1]).length > 0) {
          this.createTodayAsHistoryquote(nameSecuritycurrency.getSecuritycurrency().sTimestamp,
            nameSecuritycurrency.getSecuritycurrency().sLast, data[1]);
          this.loadedData.push(new LoadedData(timeSeriesParams[i].idSecuritycurrency, nameSecuritycurrency, data[1],
            timeSeriesParams[i].currencySecurity));
          if (observable.length === 3) {
            this.addCrossRateResponse(data[2]);
            this.normalizeSecurityPrice(this.loadedData[this.loadedData.length - 1]);
          }

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

  fiveDays(event): void {
    this.goNextWorkDay(moment().add(-7, 'days'), event);
  }

  oneMonth(event): void {
    this.goNextWorkDay(moment().add(-1, 'month'), event);
  }

  threeMonth(event): void {
    this.goNextWorkDay(moment().add(-3, 'month'), event);
  }

  yearToDate(event): void {
    this.goNextWorkDay(moment().startOf('year').add(1, 'days'), event);
  }

  oneYear(event): void {
    this.goNextWorkDay(moment().add(-1, 'year'), event);
  }

  private goNextWorkDay(momentDate: Moment, event): void {
    momentDate = momentDate.day() % 6 === 0 ? momentDate.add(1, 'day').day(1) : momentDate;
    this.fromDate = momentDate.toDate();
    this.onBlurFromDate(event);
  }

  togglePercentage(event): void {
    this.prepareLoadedDataAndPlot(true);
  }

  toggleConnectGaps(event): void {
    this.prepareLoadedDataAndPlot(true);
  }

  handleChangeCurrency(event) {
    this.requestedCurrency = event.value;
    this.normalizeAllSecurityPrices();
    this.prepareLoadedDataAndPlot(true);
  }

  getHistoricalLineTrace(loadedData: LoadedData): any {
    let foundStartIndex = Math.abs(AppHelper.binarySearch(loadedData.historyquotesNorm,
      moment(this.fromDate).format(AppSettings.FORMAT_DATE_SHORT_NATIVE), this.compareHistoricalFN));
    // const foundEndIndex = AppHelper.binarySearch(loadedData.historyquotes, this.youngestDate, this.compareHistoricalFN);

    while (loadedData.historyquotesNorm[foundStartIndex].close === null) {
      foundStartIndex++;
    }

    loadedData.factor = 100 / loadedData.historyquotesNorm[foundStartIndex].close;
    if (loadedData.currencySecurity) {
      this.legendTooltipMap.set(loadedData.nameSecuritycurrency.getName(),
        loadedData.nameSecuritycurrency.getName() + ' / ' + loadedData.currencySecurity);
    }
    return {
      type: 'scatter',
      mode: 'lines',
      name: loadedData.nameSecuritycurrency.getName(),
      // name: AppHelper.truncateString(loadedData.nameSecuritycurrency.getName(), 25, true),
      x: loadedData.historyquotesNorm.slice(foundStartIndex, loadedData.historyquotesNorm.length).map(historyquote => historyquote.date),
      y: loadedData.historyquotesNorm.slice(foundStartIndex, loadedData.historyquotesNorm.length).map(historyquote =>
        (this.usePercentage && historyquote.close != null) ? historyquote.close * loadedData.factor - 100 : historyquote.close),
      line: {width: 1},
      connectgaps: this.connectGaps
    };
  }

  getBuySellDivMarkForCurrency(traces: Traces, historicalLine: any, currencypairWithTransaction: CurrencypairWithTransaction): Traces {

    const cptv = new CurrencyPairTraceValues();
    this.addBuySellDivMarkForCurrency(traces, historicalLine, currencypairWithTransaction.transactionList, cptv, false);
    currencypairWithTransaction.cwtReverse && this.addBuySellDivMarkForCurrency(traces, historicalLine,
      currencypairWithTransaction.cwtReverse.transactionList, cptv, true);

    if (this.loadedData.length === 1 && !this.usePercentage) {
      const maxAmountSqrt = Math.sqrt(cptv.maxAmount);
      const minAmountSqrt = Math.sqrt(cptv.minAmount);
      const step = (maxAmountSqrt - minAmountSqrt) ? 18 / (maxAmountSqrt - minAmountSqrt) : 0;

      Object.entries(traces).forEach(
        ([key, value]) => {
          (<any>value).marker = {size: cptv.sizes[key].size.map(amount => (Math.sqrt(amount) - minAmountSqrt) * step + 6)};
        }
      );
    }
    return traces;
  }

  private addBuySellDivMarkForCurrency(traces: Traces, historicalLine: any, transactions: Transaction[],
    cptv: CurrencyPairTraceValues, reverse: boolean): void {
    transactions.filter(transaction => transaction.transactionTime >= this.fromDate.getTime())
      .forEach(transaction => {
        const transactionType: TransactionType = TransactionType[transaction.transactionType];
        const transactionTypeStr = this.getTransactionTypeStr(transaction, transactionType, reverse);
        const amount = Math.abs(transaction.cashaccountAmount);
        if (!traces[transactionTypeStr]) {
          traces[transactionTypeStr] = this.initializeBuySellTrace(transactionTypeStr, transactionType);
          cptv.sizes[transactionTypeStr] = {size: []};
        }
        const transactionDateStr = moment(transaction.transactionTime).format(AppSettings.FORMAT_DATE_SHORT_NATIVE);

        traces[transactionTypeStr].x.push(transactionDateStr);
        traces[transactionTypeStr].text.push(transaction.cashaccount.name + ':' + amount);

        if (this.loadedData.length > 1 || this.usePercentage) {
          const foundIndex = AppHelper.binarySearch(historicalLine.x, transactionDateStr, this.compareXaxisFN);
          traces[transactionTypeStr].y.push(historicalLine.y[Math.abs(foundIndex)]);
        } else {
          traces[transactionTypeStr].y.push((reverse) ? 1 / transaction.currencyExRate : transaction.currencyExRate);
          cptv.sizes[transactionTypeStr].size.push(amount);
          cptv.minAmount = Math.min(cptv.minAmount, amount);
          cptv.maxAmount = Math.max(cptv.maxAmount, amount);
        }
      });
  }

  private getTransactionTypeStr(transaction: Transaction, transactionType: TransactionType, reverse: boolean): string {
    if (reverse) {
      switch (transactionType) {
        case TransactionType.DEPOSIT:
        case TransactionType.ACCUMULATE:
          return TransactionType[TransactionType.REDUCE];
        case TransactionType.WITHDRAWAL:
        case TransactionType.REDUCE:
          return TransactionType[TransactionType.ACCUMULATE];
        default:
          return transaction.transactionType;
      }
    } else {
      return transactionType === TransactionType.DEPOSIT ? TransactionType[TransactionType.ACCUMULATE] :
        transactionType === TransactionType.WITHDRAWAL ? TransactionType[TransactionType.REDUCE] : transaction.transactionType;
    }
  }

  getBuySellDivMarkForSecurity(traces: Traces, loadedData: LoadedData, securityTransactionSummary: SecurityTransactionSummary): Traces {
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
            const foundIndex = AppHelper.binarySearch(loadedData.historicalLine.x, transactionDateStr, this.compareXaxisFN);
            traces[transactionTypeStr].y.push(loadedData.historicalLine.y[Math.abs(foundIndex)]);
          } else {
            // Y represent the normalized sell or buy price
            let normalizeFactor = 1;
            if (!this.normalizeNotNeeded(loadedData)) {
              const foundStartIndex = AppHelper.binarySearch(loadedData.historyquotes,
                moment(securityTransactionPosition.transaction.transactionTime).format(AppSettings.FORMAT_DATE_SHORT_NATIVE),
                this.compareHistoricalFN);
              normalizeFactor = loadedData.historyquotesNorm[foundStartIndex].close / loadedData.historyquotes[foundStartIndex].close;
            }
            traces[transactionTypeStr].y.push((securityTransactionPosition.quotationSplitCorrection
              ? securityTransactionPosition.quotationSplitCorrection : transaction.quotation) * normalizeFactor);
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

  callMeDeactivate(): void {
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_WATCHLIST_HISTORYQUOTES_CHART;
  }

  hideContextMenu(): void {
  }

  private addCurrencyCrossRateObservable(timeSeriesParams: TimeSeriesParam[], currencySecurity: string,
    observable: Observable<any>[]): void {
    if (timeSeriesParams[timeSeriesParams.length - 1].currencySecurity != null) {
      const currencypairList: string[] = [];
      this.crossRateMap.getValues().forEach(crossRateResponse => currencypairList.push(
        crossRateResponse.currencypair.fromCurrency + '|' + crossRateResponse.currencypair.toCurrency));
      const uniqueSecuritycurrency = new Set(this.loadedData.filter(ld => ld.currencySecurity).map(ld => ld.currencySecurity));
      uniqueSecuritycurrency.add(currencySecurity);
      observable.push(this.currencypairService.getCurrencypairForCrossRate(new CrossRateRequest(
        [...uniqueSecuritycurrency], currencypairList)));
    }
  }

  private addCrossRateResponse(crossRateResponse: CrossRateResponse): void {
    this.mainCurrency = crossRateResponse.mainCurrency;
    crossRateResponse.currenciesAndClosePrice.forEach(crr => {
      this.createTodayAsHistoryquote(crr.currencypair.sTimestamp, crr.currencypair.sLast, crr.closeAndDateList);
      this.crossRateMap.set(crr.currencypair.fromCurrency, crr.currencypair.toCurrency, crr);
    });
    const co = new Set(this.loadedData.filter(ld => ld.currencySecurity).map(ld => ld.currencySecurity));
    this.currenciesOptions = [{value: '', label: ''}];
    co.add(crossRateResponse.mainCurrency);
    co.forEach((currency) => this.currenciesOptions.push({value: currency, label: currency}));
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
        disabled: !this.possibleShowingTaIndicator(),
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

  private possibleShowingTaIndicator(): boolean {
    return this.loadedData.length === 1 && (this.loadedData[0].currencySecurity === this.requestedCurrency
      || this.requestedCurrency === '');
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
      DynamicFieldModelHelper.createConfigFieldsFromDescriptor(taFormDefinition.taFormList, '', true, 'APPLY'));
    this.visibleTaDialog = true;
  }

  private loadAndShowIndicatorDataWithMenu(taIndicators: string, iDef: IndicatorDefinition): void {
    iDef.menuItem.icon = AppSettings.ICONNAME_SQUARE_CHECK;
    iDef.shown = true;
    this.loadAndShowIndicatorData(taIndicators, iDef);
  }

  private loadAndShowIndicatorData(taIndicators: string, iDef: IndicatorDefinition): void {
    const dataModel: string = this.usersettingsService.retrieveObject(AppSettings.TA_INDICATORS_STORE + taIndicators)
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
      if (!DynamicFieldModelHelper.isDataModelEqual(existingDataModel, newDataModel, taFormDefinition.taFormList)) {
        this.deleteTaIndicator(iDef);
      }
    }
  }

  private deleteTaIndicator(iDef: IndicatorDefinition) {
    if (iDef.shown) {
      iDef.menuItem.icon = AppSettings.ICONNAME_SQUARE_EMTPY;
      iDef.shown = false;
      const traceIndices: number[] = iDef.taTraceIndicatorDataList.map(taT => taT.traceIndex).reverse();
      Plotly.deleteTraces(this.chartElement.nativeElement, traceIndices);
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
      Plotly.addTraces(this.chartElement.nativeElement, {
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
  }

  private redoTaAfterDateChange(): void {
    this.indicatorDefinitions.defMap.forEach((iDef: IndicatorDefinition, taIndicators: TaIndicators) => {
      if (iDef.shown) {
        if (this.possibleShowingTaIndicator()) {
          if (this.indicatorDefinitions.idSecuritycurrency === this.loadedData[0].idSecuritycurrency) {
            this.createTaTrace(iDef);
          } else {
            this.loadAndShowIndicatorData(TaIndicators[taIndicators], iDef);
          }
        }
      }
    });
  }

  private prepareLoadedDataAndPlot(userUserInputDate: boolean) {
    const element = this.chartElement.nativeElement;
    !userUserInputDate && this.evaluateOldestYoungestMatchDate();
    let traces = this.createAllQuotesLines();
    traces = traces.concat(this.createAllMarkerTraces());
    const layout: any = this.getLayout();
    this.plot(element, traces, layout);
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
      this.oldestMatchDate = !this.oldestMatchDate ? ld.historyquotesNorm[0].date : ld.historyquotesNorm[0].date > this.oldestMatchDate ?
        ld.historyquotesNorm[0].date : this.oldestMatchDate;
      const maxIndex = ld.historyquotesNorm.length - 1;
      this.youngestMatchDate = !this.youngestMatchDate ? ld.historyquotesNorm[maxIndex].date :
        ld.historyquotesNorm[maxIndex].date < this.youngestMatchDate ? ld.historyquotesNorm[maxIndex].date : this.youngestMatchDate;
    }
    this.matchEveryHistoryquotesYoungestDate();
    if (!this.fromDate || this.loadedData.length === 1 || this.oldestDate.getTime() > this.fromDate.getTime()) {
      this.fromDate = this.oldestDate;
    }
    this.startDate = this.oldestDate ? new Date(this.oldestDate) : new Date('2000-01-03');
    this.endDate = this.youngestDate ? new Date(this.youngestDate) : new Date();
  }

  private matchEveryHistoryquotesYoungestDate(): void {
    let i = 0;
    while (i < this.loadedData.length) {
      const foundStartIndex = AppHelper.binarySearch(this.loadedData[i].historyquotesNorm,
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
      if (ld.currencySecurity) {
        this.getBuySellDivMarkForSecurity(traces, ld, <SecurityTransactionSummary>ld.nameSecuritycurrency);
      } else {
        this.getBuySellDivMarkForCurrency(traces, ld.historicalLine, <CurrencypairWithTransaction>ld.nameSecuritycurrency);
      }
    }
    return Object.values(traces);
  }

  private plot(element: any, traces: any, layout: any): void {
    const config = PlotlyLocales.setPlotyLocales(Plotly, this.gps);
    config.modeBarButtonsToRemove = ['lasso2d', 'select2d'];
    config.displaylogo = false;
    Plotly.purge(this.chartElement.nativeElement);
    Plotly.newPlot(element, traces, layout, config).then(this.attachTooltip.bind(this));
    element.on('plotly_afterplot', this.attachTooltip.bind(this));

    PlotlyHelper.registerPlotlyClick(element, this.chartDataPointClicked.bind(this));
    if (!this.subscriptionViewSizeChanged) {
      this.subscriptionViewSizeChanged = this.viewSizeChangedService.viewSizeChanged$.subscribe(changedViewSizeType =>
        Plotly.Plots.resize(element));
    }
  }

  private createTodayAsHistoryquote(sTimestamp: number, sLast: number, historyquotes: HistoryquoteDateClose[]) {
    if (sLast) {
      const dateLastStr = moment(sTimestamp).format(AppSettings.FORMAT_DATE_SHORT_NATIVE);
      if (historyquotes.length === 0 || dateLastStr > historyquotes[historyquotes.length - 1].date) {
        const historyquoteToday: HistoryquoteDateClose = {
          date: dateLastStr, close: sLast
        };
        historyquotes.push(historyquoteToday);
      }
    }
  }

  private attachTooltip(): void {
    PlotlyHelper.attachTooltip(Plotly, this.legendTooltipMap, this.chartElement);
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
        rangeslider: {
          autorange: false,
          range: [dateNative, this.youngestMatchDate]
        },
        type: 'date'
      },
      yaxis: {
        autorange: true,
        range: [this.minValueOfY, this.maxValueOfY],
        ticksuffix: this.usePercentage ? '%' : '',
        type: 'linear'
      }
    };
    PlotlyHelper.translateLayout(this.translateService, layout);
    return layout;
  }

  private normalizeAllSecurityPrices(): void {
    this.loadedData.filter(ld => ld.currencySecurity != null).forEach(ld => this.normalizeSecurityPrice(ld));
  }

  private normalizeSecurityPrice(loadedData: LoadedData): void {
    if (this.normalizeNotNeeded(loadedData)) {
      loadedData.historyquotesNorm = loadedData.historyquotes;
    } else {
      const mainToSecurityCurrency: CurrenciesAndClosePrice = this.getCurrencypairOrReverse(this.mainCurrency, loadedData.currencySecurity);
      const requestToSecurityCurrency: CurrenciesAndClosePrice = this.getCurrencypairOrReverse(this.requestedCurrency,
        loadedData.currencySecurity);
      if (this.requestedCurrency === this.mainCurrency) {
        this.calculateHistoryquotes(loadedData, [mainToSecurityCurrency], [
          mainToSecurityCurrency.currencypair.fromCurrency !== this.mainCurrency]);
      } else if (requestToSecurityCurrency != null && (requestToSecurityCurrency.currencypair.fromCurrency === this.mainCurrency
        || requestToSecurityCurrency.currencypair.toCurrency === this.mainCurrency)) {
        this.calculateHistoryquotes(loadedData, [requestToSecurityCurrency],
          [requestToSecurityCurrency.currencypair.fromCurrency === this.mainCurrency]);
      } else {
        // Main currency is in the middle linke EUR -> CHF -> USD (requested currency: EUR, main currency: CHF,
        // security currency: USD
        const mainToRequestCurrency: CurrenciesAndClosePrice = this.getCurrencypairOrReverse(this.mainCurrency, this.requestedCurrency);
        this.calculateHistoryquotes(loadedData, [mainToSecurityCurrency, mainToRequestCurrency],
          [mainToSecurityCurrency.currencypair.fromCurrency !== this.mainCurrency,
            mainToRequestCurrency.currencypair.fromCurrency === this.mainCurrency]);
      }
    }
  }

  private normalizeNotNeeded(loadedData: LoadedData): boolean {
    return this.requestedCurrency.length === 0 || loadedData.currencySecurity == null
      || loadedData.currencySecurity === this.requestedCurrency;
  }

  private getCurrencypairOrReverse(reqesteOrMainCurrency: string, currencySecurity: string): CurrenciesAndClosePrice {
    let mainToSecurityCurrency: CurrenciesAndClosePrice = this.crossRateMap.get(reqesteOrMainCurrency, currencySecurity);
    if (mainToSecurityCurrency == null) {
      mainToSecurityCurrency = this.crossRateMap.get(currencySecurity, reqesteOrMainCurrency);
    }
    return mainToSecurityCurrency;
  }

  private calculateHistoryquotes(loadedData: LoadedData, currenciesAndClosePrice: CurrenciesAndClosePrice[],
    multiple: boolean[]) {
    const map2Loop: { [date: string]: HistoryquoteDateClose } = {};
    loadedData.historyquotesNorm = [];

    for (let i = 0; i < currenciesAndClosePrice.length; i++) {
      let cIndex = Math.abs(AppHelper.binarySearch(currenciesAndClosePrice[i].closeAndDateList,
        loadedData.historyquotes[0].date, this.compareHistoricalFN));
      for (let hIndex = 0; hIndex < loadedData.historyquotes.length; hIndex++) {
        do {
          if (loadedData.historyquotes[hIndex].date > currenciesAndClosePrice[i].closeAndDateList[cIndex].date) {
            cIndex++;
          } else if (loadedData.historyquotes[hIndex].date < currenciesAndClosePrice[i].closeAndDateList[cIndex].date) {
            hIndex++;
          }
        } while (hIndex < loadedData.historyquotes.length && cIndex < currenciesAndClosePrice[i].closeAndDateList.length
        && loadedData.historyquotes[hIndex].date !== currenciesAndClosePrice[i].closeAndDateList[cIndex].date);
        if (hIndex < loadedData.historyquotes.length && cIndex < currenciesAndClosePrice[i].closeAndDateList.length) {
          if (loadedData.historyquotes[hIndex].close != null) {
            if (i === 0) {
              const historyquoteDateClose = {
                date: loadedData.historyquotes[hIndex].date,
                close: loadedData.historyquotes[hIndex].close * (multiple[i] ? currenciesAndClosePrice[i].closeAndDateList[cIndex].close :
                  1 / currenciesAndClosePrice[i].closeAndDateList[cIndex].close)
              };
              loadedData.historyquotesNorm.push(historyquoteDateClose);
              currenciesAndClosePrice.length > 1 && (map2Loop[historyquoteDateClose.date] = historyquoteDateClose);
            } else {
              const historyquoteDateClose = map2Loop[loadedData.historyquotes[hIndex].date];
              if (historyquoteDateClose) {
                historyquoteDateClose.close *= (multiple[i] ? currenciesAndClosePrice[i].closeAndDateList[cIndex].close :
                  1 / currenciesAndClosePrice[i].closeAndDateList[cIndex].close);
              }
            }
          }
        }
      }
    }
  }
}

export class TimeSeriesParam {
  constructor(public idSecuritycurrency: number, public currencySecurity: string,
    public idPortfolio: number, public idSecurityaccount: number, fileGaps = true) {
  }
}

class LoadedData {
  public factor: number;
  public historicalLine: any;
  public historyquotesNorm: HistoryquoteDateClose[];

  constructor(public idSecuritycurrency: number, public nameSecuritycurrency: (CurrencypairWithTransaction | SecurityTransactionSummary),
    public historyquotes: HistoryquoteDateClose[], public currencySecurity: string) {
    this.historyquotesNorm = historyquotes;
  }
}

class CurrencyPairTraceValues {
  public sizes: { [key: string]: { size: number[] } } = {};
  public minAmount = Number.MAX_VALUE;
  public maxAmount = 0;
}

