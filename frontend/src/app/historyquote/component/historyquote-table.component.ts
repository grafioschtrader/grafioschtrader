import {Component, OnDestroy} from '@angular/core';

import {Historyquote, HistoryquoteCreateType} from '../../entities/historyquote';
import {HistoryquoteService} from '../service/historyquote.service';
import {ActivatedRoute} from '@angular/router';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {AppSettings} from '../../shared/app.settings';
import {combineLatest, Subscription} from 'rxjs';
import {AppHelper} from '../../shared/helper/app.helper';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {SecurityTransactionSummary} from '../../entities/view/security.transaction.summary';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {CurrencypairWithTransaction} from '../../entities/view/currencypair.with.transaction';
import {TableCrudSupportMenu} from '../../shared/datashowbase/table.crud.support.menu';
import {TranslateService} from '@ngx-translate/core';
import {INameSecuritycurrency} from '../../entities/view/iname.securitycurrency';
import {SecurityTransactionPosition} from '../../entities/view/security.transaction.position';
import {DataType} from '../../dynamic-form/models/data.type';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {HelpIds} from '../../shared/help/help.ids';
import {TimeSeriesParam} from './time.series.chart.component';
import {FilterType} from '../../shared/datashowbase/filter.type';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {Securitycurrency} from '../../entities/securitycurrency';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {plainToClass} from 'class-transformer';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {HistoryquotesWithMissings} from '../model/historyquotes.with.missings';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {FileUploadParam} from '../../shared/generaldialog/model/file.upload.param';
import {ColumnConfig} from '../../shared/datashowbase/column.config';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {DialogService} from 'primeng/dynamicdialog';

/**
 * Shows the history quotes in a table
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-panel>
        <p-header>
          <h4>{{entityNameUpper | translate}} {{nameSecuritycurrency?.getName()}}</h4>
          <ng-container *ngIf="security">
            {{'TRADING_FROM_TO' | translate }}: {{getDateByFormat(security.activeFromDate)}}
            - {{getDateByFormat(security.activeToDate)}}
          </ng-container>
        </p-header>
        <historyquote-quality [historyquoteQuality]="historyquotesWithMissings?.historyquoteQuality"
                              [securitycurrency]="historyquotesWithMissings?.securitycurrency">
        </historyquote-quality>
      </p-panel>
      <div class="datatable">
        <p-table #table [columns]="fields" [value]="entityList" selectionMode="single" [(selection)]="selectedEntity"
                 styleClass="sticky-table p-datatable-striped p-datatable-gridlines"
                 responsiveLayout="scroll"
                 [first]="firstRow" (onPage)="onPage($event)" sortMode="multiple" [multiSortMeta]="multiSortMeta"
                 [paginator]="true" [rows]="20" [dataKey]="entityKeyName">
          <ng-template pTemplate="header" let-fields>
            <tr>
              <th *ngFor="let field of fields" [pSortableColumn]="field.field"
                  [style.width.px]="field.width" [pTooltip]="field.headerTooltipTranslated">
                {{field.headerTranslated}}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            </tr>
            <tr *ngIf="hasFilter">
              <th *ngFor="let field of fields" [ngSwitch]="field.filterType" style="overflow:visible;">
                <ng-container *ngSwitchCase="FilterType.likeDataType">
                  <ng-container [ngSwitch]="field.dataType">
                    <p-columnFilter *ngSwitchCase="field.dataType === DataType.DateString || field.dataType === DataType.DateNumeric
                              ? field.dataType : ''" [field]="field.field" display="menu" [showOperator]="true"
                                    [matchModeOptions]="customMatchModeOptions" [matchMode]="'gtNoFilter'">
                      <ng-template pTemplate="filter" let-value let-filter="filterCallback">
                        <p-calendar #cal [ngModel]="value" [dateFormat]="baseLocale.dateFormat"
                                    (onSelect)="filter($event)"
                                    monthNavigator="true" yearNavigator="true" yearRange="2000:2099"
                                    (onInput)="filter(cal.value)">
                        </p-calendar>
                      </ng-template>
                    </p-columnFilter>
                    <p-columnFilter *ngSwitchCase="DataType.Numeric" type="numeric" [field]="field.field"
                                    [locale]="formLocale"
                                    minFractionDigits="2" display="menu"></p-columnFilter>
                  </ng-container>
                </ng-container>
                <ng-container *ngSwitchCase="FilterType.withOptions">
                  <p-dropdown [options]="field.filterValues" [style]="{'width':'100%'}"
                              (onChange)="table.filter($event.value, field.field, 'equals')"></p-dropdown>
                </ng-container>
              </th>
            </tr>
          </ng-template>

          <ng-template pTemplate="body" let-el let-columns="fields">
            <tr [pSelectableRow]="el">
              <ng-container *ngFor="let field of fields">
                <td *ngIf="field.visible" [style.width.px]="field.width"
                    [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-right': ''">
                  <ng-container [ngSwitch]="field.templateName">
                    <ng-container *ngSwitchCase="'icon'">
                      <svg-icon [name]="getValueByPath(el, field)"
                                [svgStyle]="{ 'width.px':14, 'height.px':14 }"></svg-icon>
                    </ng-container>
                    <ng-container *ngSwitchDefault>
                      {{getValueByPath(el, field)}}
                    </ng-container>
                  </ng-container>
                </td>
              </ng-container>
            </tr>
          </ng-template>
        </p-table>
        <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"
                       appendTo="body"></p-contextMenu>
      </div>
    </div>
    <historyquote-edit *ngIf="visibleDialog"
                       [visibleDialog]="visibleDialog"
                       [callParam]="callParam"
                       (closeDialog)="handleCloseDialog($event)">
    </historyquote-edit>

    <upload-file-dialog *ngIf="visibleUploadFileDialog"
                        [visibleDialog]="visibleUploadFileDialog"
                        [fileUploadParam]="fileUploadParam"
                        (closeDialog)="handleCloseDialogAndRead($event)">
    </upload-file-dialog>

    <historyquote-quality-fill-gaps *ngIf="visibleFillGapsDialog"
                                    [visibleDialog]="visibleFillGapsDialog"
                                    [historyquoteQuality]="historyquotesWithMissings?.historyquoteQuality"
                                    [securitycurrency]="historyquotesWithMissings?.securitycurrency"
                                    (closeDialog)="handleCloseDialogAndRead($event)">
    </historyquote-quality-fill-gaps>

    <historyquote-delete-dialog *ngIf="visibleDeleteHistoryquotes"
                                [visibleDialog]="visibleDeleteHistoryquotes"
                                [idSecuritycurrency]="historyquotesWithMissings.securitycurrency.idSecuritycurrency"
                                [historyquoteQuality]="historyquotesWithMissings.historyquoteQuality"
                                (closeDialog)="handleCloseDialogAndRead($event)">
    </historyquote-delete-dialog>
  `,
  providers: [DialogService]
})
export class HistoryquoteTableComponent extends TableCrudSupportMenu<Historyquote> implements OnDestroy {

  private static SVG = '.svg';
  private static createTypeIconMap: { [key: number]: string } = {
    [HistoryquoteCreateType.CONNECTOR_CREATED]: 'connector',
    [HistoryquoteCreateType.MANUAL_IMPORTED]: 'import',
    [HistoryquoteCreateType.FILLED_CLOSED_LINEAR_TRADING_DAY]: 'fill_linear',
    [HistoryquoteCreateType.CALCULATED]: 'calculation',
    [HistoryquoteCreateType.ADD_MODIFIED_USER]: 'edit',
    [HistoryquoteCreateType.FILL_GAP_BY_CONNECTOR]: 'gap_fill'
  };
  private static iconLoadDone = false;

  callParam: HistoryquoteSecurityCurrency;
  firstRow: number;

  nameSecuritycurrency: INameSecuritycurrency;
  historyquotesWithMissings: HistoryquotesWithMissings;
  security: Security;
  visibleUploadFileDialog = false;
  fileUploadParam: FileUploadParam;
  visibleFillGapsDialog = false;
  visibleDeleteHistoryquotes = false;
  importQuotesMenu: MenuItem = {
    label: 'IMPORT_QUOTES' + AppSettings.DIALOG_MENU_SUFFIX,
    command: (event) => this.uploadImportQuotes()
  };
  fillGapsMenu: MenuItem = {
    label: 'HISTORYQUOTE_FILL_GAPS' + AppSettings.DIALOG_MENU_SUFFIX,
    command: (event) => this.fillLinearGap()
  };
  deleteCreateTypesMenu: MenuItem = {
    label: 'DELETE_CREATE_TYPES_QUOTES' + AppSettings.DIALOG_MENU_SUFFIX,
    command: (event) => this.deleteCreateTypeQuotes()
  };
  protected transactionPositionList: SecurityTransactionPosition[] = [];
  private routeSubscribe: Subscription;
  private timeSeriesParams: TimeSeriesParam[];
  private historyquoteSpecMenuItems: MenuItem[];

  constructor(private iconReg: SvgIconRegistryService,
              private securitService: SecurityService,
              private currencypairService: CurrencypairService,
              private activatedRoute: ActivatedRoute,
              private historyquoteService: HistoryquoteService,
              private dataChangedService: DataChangedService,
              usersettingsService: UserSettingsService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              dialogService: DialogService,
              filterService: FilterService,
              gps: GlobalparameterService,
              translateService: TranslateService) {
    super(AppSettings.HISTORYQUOTE, historyquoteService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService);

    HistoryquoteTableComponent.registerIcons(this.iconReg);
    this.addColumnFeqH(DataType.DateString, 'date', true, false,
      {filterType: FilterType.likeDataType, export: true});
    this.addColumn(DataType.NumericInteger, 'createType', 'T', true, true,
      {fieldValueFN: this.getCreateTypeIcon.bind(this), templateName: 'icon', width: 20});
    this.addColumnFeqH(DataType.DateTimeNumeric, 'createModifyTime', true, true);
    this.addColumnFeqH(DataType.Numeric, 'volume', true, false, {export: true});
    this.addColumnFeqH(DataType.Numeric, 'open', true, false, {
      minFractionDigits: 5,
      maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS,
      export: true
    });
    this.addColumnFeqH(DataType.Numeric, 'high', true, false,
      {maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS, export: true});
    this.addColumnFeqH(DataType.Numeric, 'low', true, false,
      {maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS, export: true});
    this.addColumnFeqH(DataType.Numeric, 'close', true, false, {
      minFractionDigits: 5,
      maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS, filterType: FilterType.likeDataType, export: true
    });
    this.multiSortMeta.push({field: 'date', order: -1});
    this.prepareTableAndTranslate();
  }

  private static registerIcons(iconReg: SvgIconRegistryService): void {
    if (!HistoryquoteTableComponent.iconLoadDone) {
      for (const [key, iconName] of Object.entries(HistoryquoteTableComponent.createTypeIconMap)) {
        iconReg.loadSvg(AppSettings.PATH_ASSET_ICONS + iconName + HistoryquoteTableComponent.SVG, iconName);
      }
      HistoryquoteTableComponent.iconLoadDone = false;
    }
  }

  readData(): void {
    this.readAndShowData(this.timeSeriesParams[0]);
    // Changing history quotes an affect some others viewed parts
    this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.UPDATED, new Historyquote()));
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_WATCHLIST_HISTORYQUOTES;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
    this.usersettingsService.saveSingleValue(AppSettings.HISTORYQUOTE_TABLE_SETTINGS_STORE, this.rowsPerPage);
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
  }

  getCreateTypeIcon(historyquote: Historyquote, field: ColumnConfig): string {
    return HistoryquoteTableComponent.createTypeIconMap[HistoryquoteCreateType[historyquote.createType]];
  }

  prepareMenu(): void {
    this.historyquoteSpecMenuItems = [
      this.importQuotesMenu,
      {label: 'EXPORT_CSV', command: (event) => this.downloadCSvFile(this.historyquotesWithMissings.historyquoteList)},
      this.deleteCreateTypesMenu,
    ];
    if (this.security && this.hasRightsForCreateEntity(null)) {
      this.historyquoteSpecMenuItems.push(this.fillGapsMenu);
    }
    TranslateHelper.translateMenuItems(this.historyquoteSpecMenuItems, this.translateService);
  }

  uploadImportQuotes(): void {
    this.fileUploadParam = new FileUploadParam(HelpIds.HELP_WATCHLIST_HISTORYQUOTES, null,
      'csv', 'IMPORT_QUOTES', false, this.historyquoteService,
      this.nameSecuritycurrency.getSecuritycurrency().idSecuritycurrency, this.historyquotesWithMissings.supportedCSVFormats,
      AppSettings.HIST_SUPPORTED_CSV_FORMAT);

    this.visibleUploadFileDialog = true;
  }

  handleCloseDialog(processedActionData: ProcessedActionData): void {
    super.handleCloseDialog(processedActionData);
  }

  handleCloseDialogAndRead(processedActionData: ProcessedActionData): void {
    this.visibleUploadFileDialog = false;
    this.visibleFillGapsDialog = false;
    this.visibleDeleteHistoryquotes = false;
    this.readDataAndProposeDataChangedEvent(processedActionData);
  }

  deleteCreateTypeQuotes(): void {
    this.visibleDeleteHistoryquotes = true;
  }

  fillLinearGap(): void {
    this.visibleFillGapsDialog = true;
  }

  resetMenu(historyquote: Historyquote): void {
    if (!this.security || !this.security.idLinkSecuritycurrency) {
      this.contextMenuItems = this.prepareEditMenu(this.selectedEntity);
      this.contextMenuItems.push({separator: true});
      this.importQuotesMenu.disabled = !this.hasRightsForDeleteEntity(null);
      this.fillGapsMenu.disabled = !this.historyquotesWithMissings.historyquoteQuality
        || this.historyquotesWithMissings.historyquoteQuality.totalMissing === 0 &&
        this.historyquotesWithMissings.historyquoteQuality.toManyAsCalendar === 0;
      this.deleteCreateTypesMenu.disabled = !this.historyquotesWithMissings.historyquoteQuality
        || !this.historyquotesWithMissings.historyquoteQuality.filledLinear
        && !this.historyquotesWithMissings.historyquoteQuality.manualImported;
      this.contextMenuItems.push(...this.historyquoteSpecMenuItems);
    } else {
      this.contextMenuItems = null;
    }
    this.activePanelService.activatePanel(this, {editMenu: this.contextMenuItems});
  }

  getDateByFormat(date: string): string {
    return AppHelper.getDateByFormat(this.gps, date);
  }

  protected initialize(): void {
    this.rowsPerPage = this.usersettingsService.readSingleValue(AppSettings.HISTORYQUOTE_TABLE_SETTINGS_STORE) || 30;
    this.routeSubscribe = this.activatedRoute.paramMap.subscribe(paramMap => {
      const paramObject = AppHelper.createParamObjectFromParamMap(paramMap);
      this.timeSeriesParams = paramObject.allParam;
      this.readAndShowData(this.timeSeriesParams[0]);
    });
  }

  protected prepareCallParam(entity: Historyquote) {
    // For a new history quote the idSecuritycurrency is needed
    this.callParam = new HistoryquoteSecurityCurrency(entity, this.nameSecuritycurrency.getSecuritycurrency());
  }

  protected getId(entity: Historyquote): number {
    return entity.idHistoryQuote;
  }

  protected beforeDelete(entity: Historyquote): Historyquote {
    return plainToClass(Historyquote, entity);
  }

  /**
   * The creation of a history quote depends on the right on the security or currency
   */
  protected hasRightsForCreateEntity(histroyquote: Historyquote): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteAuditable(this.gps,
      this.nameSecuritycurrency.getSecuritycurrency());
  }

  /**
   * The deletion of a history quote depends on the right on the security or currency
   */
  protected override hasRightsForDeleteEntity(histroyquote: Historyquote): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteAuditable(this.gps,
      this.nameSecuritycurrency.getSecuritycurrency());
  }

  private readAndShowData(timeSeriesParam: TimeSeriesParam): void {
    const stsObservable = timeSeriesParam.currencySecurity
      ? BusinessHelper.setSecurityTransactionSummary(this.securitService,
        timeSeriesParam.idSecuritycurrency, null, null, false)
      : this.currencypairService.getTransactionForCurrencyPair(timeSeriesParam.idSecuritycurrency, false);
    const historyquoteObservable = this.historyquoteService.getHistoryqoutesByIdSecuritycurrencyWithMissing(
      timeSeriesParam.idSecuritycurrency, !timeSeriesParam.currencySecurity);

    this.firstRow = 0;
    combineLatest([stsObservable, historyquoteObservable]).subscribe((data: any[]) => {
      this.nameSecuritycurrency = timeSeriesParam.currencySecurity
        ? new SecurityTransactionSummary(data[0].transactionPositionList, data[0].securityPositionSummary)
        : new CurrencypairWithTransaction(data[0]);
      this.historyquotesWithMissings = data[1];
      this.entityList = this.historyquotesWithMissings.historyquoteList;
      this.security = <Security>(timeSeriesParam.currencySecurity ? this.historyquotesWithMissings.securitycurrency : null);
      this.prepareMenu();
      this.refreshSelectedEntity();
      setTimeout(() => this.firstRow = this.firstRowIndexOnPage);
    });
  }

  private readDataAndProposeDataChangedEvent(processedActionData: ProcessedActionData): void {
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

}

export class HistoryquoteSecurityCurrency {
  constructor(public historyquote: Historyquote, public securitycurrency: Securitycurrency) {
  }

  get showName(): string {
    return this.securitycurrency.hasOwnProperty('name') ? (<Security>this.securitycurrency).name :
      (<Currencypair>this.securitycurrency).fromCurrency + '/' + (<Currencypair>this.securitycurrency).toCurrency;
  }
}

