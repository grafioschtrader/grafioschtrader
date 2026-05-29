import {Component, EventEmitter, Injector, Input, OnChanges, OnDestroy, Output, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Historyquote} from '../../entities/historyquote';
import {HistoryquoteService} from '../service/historyquote.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {AppSettings} from '../../shared/app.settings';
import {combineLatest} from 'rxjs';
import {AppHelper} from '../../lib/helper/app.helper';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {SecurityTransactionSummary} from '../../entities/view/security.transaction.summary';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {CurrencypairWithTransaction} from '../../entities/view/currencypair.with.transaction';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {INameSecuritycurrency} from '../../entities/view/iname.securitycurrency';
import {SecurityTransactionPosition} from '../../entities/view/security.transaction.position';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {HelpIds} from '../../lib/help/help.ids';
import {TimeSeriesParam} from './time.series.chart.component';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {HistoryquoteTableBase} from './historyquote-table.base';
import {Securitycurrency} from '../../entities/securitycurrency';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {plainToClass} from 'class-transformer';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {HistoryquotesWithMissings} from '../model/historyquotes.with.missings';
import {DataChangedService} from '../../lib/maintree/service/data.changed.service';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {FileUploadParam} from '../../lib/generaldialog/model/file.upload.param';
import {AngularSvgIconModule, SvgIconRegistryService} from 'angular-svg-icon';
import {DialogService} from 'primeng/dynamicdialog';
import {BaseSettings} from '../../lib/base.settings';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {HistoryquoteQualityComponent} from './historyquote-quality.component';
import {HistoryquoteEditComponent} from './historyquote-edit.component';
import {UploadFileDialogComponent} from '../../lib/generaldialog/component/upload-file-dialog.component';
import {HistoryquoteQualityFillGapsComponent} from './historyquote-quality-fill-gaps.component';
import {HistoryquoteDeleteDialogComponent} from './historyquote-delete-dialog.component';
import {GlobalSessionNames} from '../../lib/global.session.names';

/**
 * Shows the history quotes in a table. Hosted by {@link HistoryquoteHostComponent}, which feeds
 * it the time-series params parsed from the route and listens for {@code showLegacyRequested}
 * to swap to the legacy archive view.
 */
@Component({
  selector: 'historyquote-table',
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <div class="datatable">
        <configurable-table
          [data]="entityList"
          [fields]="fields"
          [dataKey]="entityKeyName"
          [selectionMode]="'single'"
          [(selection)]="selectedEntity"
          [multiSortMeta]="multiSortMeta"
          [customSortFn]="customSort.bind(this)"
          [paginator]="true"
          [rows]="20"
          [firstRowIndex]="firstRow"
          (pageChange)="onPage($event)"
          [stripedRows]="true"
          [showGridlines]="true"
          [hasFilter]="hasFilter"
          [customMatchModeOptions]="customMatchModeOptions"
          [minDate]="minDate"
          [maxDate]="maxDate"
          [baseLocale]="baseLocale"
          [formLocale]="formLocale"
          [containerClass]="''"
          [contextMenuEnabled]="!!contextMenuItems"
          [showContextMenu]="isActivated()"
          [contextMenuItems]="contextMenuItems"
          [valueGetterFn]="getValueByPath.bind(this)">

          <!-- Caption content projected into table header -->
          <div caption>
            <h4>{{ entityNameUpper | translate }} {{ nameSecuritycurrency?.getName() }}</h4>
            @if (security) {
              <span>{{ 'TRADING_FROM_TO' | translate }}: {{ getDateByFormat(security.activeFromDate) }}
              - {{ getDateByFormat(security.activeToDate) }}</span>
            }
            <historyquote-quality [historyquoteQuality]="historyquotesWithMissings?.historyquoteQuality"
                                  [securitycurrency]="historyquotesWithMissings?.securitycurrency">
            </historyquote-quality>
          </div>

          <!-- Custom icon cell template for svg-icon rendering -->
          <ng-template #iconCell let-row let-field="field" let-value="value">
            <svg-icon [name]="value" [svgStyle]="{ 'width.px':14, 'height.px':14 }"></svg-icon>
          </ng-template>

        </configurable-table>
      </div>
    </div>
    @if (visibleDialog) {
      <historyquote-edit [visibleDialog]="visibleDialog"
                         [callParam]="callParam"
                         (closeDialog)="handleCloseDialog($event)">
      </historyquote-edit>
    }

    @if (visibleUploadFileDialog) {
      <upload-file-dialog [visibleDialog]="visibleUploadFileDialog"
                          [fileUploadParam]="fileUploadParam"
                          (closeDialog)="handleCloseDialogAndRead($event)">
      </upload-file-dialog>
    }

    @if (visibleFillGapsDialog) {
      <historyquote-quality-fill-gaps [visibleDialog]="visibleFillGapsDialog"
                                      [historyquoteQuality]="historyquotesWithMissings?.historyquoteQuality"
                                      [securitycurrency]="historyquotesWithMissings?.securitycurrency"
                                      (closeDialog)="handleCloseDialogAndRead($event)">
      </historyquote-quality-fill-gaps>
    }

    @if (visibleDeleteHistoryquotes) {
      <historyquote-delete-dialog [visibleDialog]="visibleDeleteHistoryquotes"
                                  [idSecuritycurrency]="historyquotesWithMissings.securitycurrency.idSecuritycurrency"
                                  [historyquoteQuality]="historyquotesWithMissings.historyquoteQuality"
                                  (closeDialog)="handleCloseDialogAndRead($event)">
      </historyquote-delete-dialog>
    }
  `,
  providers: [DialogService],
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    ConfigurableTableComponent,
    AngularSvgIconModule,
    HistoryquoteQualityComponent,
    HistoryquoteEditComponent,
    UploadFileDialogComponent,
    HistoryquoteQualityFillGapsComponent,
    HistoryquoteDeleteDialogComponent
  ]
})
export class HistoryquoteTableComponent extends HistoryquoteTableBase<Historyquote> implements OnChanges, OnDestroy {
  @Input() timeSeriesParams: TimeSeriesParam[];
  @Output() showLegacyRequested = new EventEmitter<void>();

  minDate: Date = new Date(sessionStorage.getItem(GlobalSessionNames.OLDEST_TRADING_DAY) ?? BaseSettings.OLDEST_TRADING_DAY_FALLBACK);
  maxDate: Date = new Date('2099-12-31');

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
    label: 'IMPORT_QUOTES' + BaseSettings.DIALOG_MENU_SUFFIX,
    command: (event) => this.uploadImportQuotes()
  };
  fillGapsMenu: MenuItem = {
    label: 'HISTORYQUOTE_FILL_GAPS' + BaseSettings.DIALOG_MENU_SUFFIX,
    command: (event) => this.fillLinearGap()
  };
  deleteCreateTypesMenu: MenuItem = {
    label: 'DELETE_CREATE_TYPES_QUOTES' + BaseSettings.DIALOG_MENU_SUFFIX,
    command: (event) => this.deleteCreateTypeQuotes()
  };
  showLegacyMenu: MenuItem = {
    label: 'HISTORYQUOTE_SHOW_LEGACY',
    command: (event) => this.showLegacyRequested.emit()
  };
  protected transactionPositionList: SecurityTransactionPosition[] = [];
  private historyquoteSpecMenuItems: MenuItem[];


  constructor(private iconReg: SvgIconRegistryService,
    private securityService: SecurityService,
    private currencypairService: CurrencypairService,
    private historyquoteService: HistoryquoteService,
    private dataChangedService: DataChangedService,
    usersettingsService: UserSettingsService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    gps: GlobalparameterService,
    translateService: TranslateService,
    injector: Injector) {
    super(AppSettings.HISTORYQUOTE, historyquoteService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService, injector);

    HistoryquoteTableBase.registerCreateTypeIcons(this.iconReg);
    this.addDateColumn();
    this.addCreateTypeColumn();
    this.addColumnFeqH(DataType.DateTimeString, 'createModifyTime', true, true);
    this.addOhlcvColumns();
    this.applyDefaultDateSort();
    this.prepareTableAndTranslate();
  }

  readData(): void {
    this.readAndShowData(this.timeSeriesParams[0]);
    // Changing history quotes an affect some others viewed parts
    this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.UPDATED, new Historyquote()));
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST_HISTORYQUOTES;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
    this.usersettingsService.saveSingleValue(AppSettings.HISTORYQUOTE_TABLE_SETTINGS_STORE, this.rowsPerPage);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['timeSeriesParams'] && this.timeSeriesParams && this.timeSeriesParams.length > 0) {
      this.readAndShowData(this.timeSeriesParams[0]);
    }
  }

  prepareMenu(): void {
    // Both bulk-quote actions are always rendered. Permission state is reflected by the .disabled flag set in
    // resetMenu() rather than by hiding the item, so a limited-edit user without rights on the parent security still
    // sees the menu but cannot trigger the action.
    this.historyquoteSpecMenuItems = [
      this.deleteCreateTypesMenu,
      this.fillGapsMenu,
    ];
    // Always show the legacy menu (even when legacyCount is 0) so the user can
    // reach the legacy view to import a CSV when the shadow archive is empty.
    // Reset the label to the translation key on every prepareMenu invocation —
    // we append the live count below after translation.
    this.showLegacyMenu.label = 'HISTORYQUOTE_SHOW_LEGACY';
    this.historyquoteSpecMenuItems.push(this.showLegacyMenu);
    this.historyquoteSpecMenuItems.push({separator: true});
    this.historyquoteSpecMenuItems.push(this.importQuotesMenu);
    this.historyquoteSpecMenuItems.push({label: 'EXPORT_CSV',
      command: (event) => this.downloadCSvFile(this.historyquotesWithMissings.historyquoteList)});
    TranslateHelper.translateMenuItems(this.historyquoteSpecMenuItems, this.translateService);
    const legacyCount = this.historyquotesWithMissings?.legacyCount ?? 0;
    this.showLegacyMenu.label = `${this.showLegacyMenu.label} (${legacyCount})`;
  }

  uploadImportQuotes(): void {
    this.fileUploadParam = new FileUploadParam(HelpIds.HELP_WATCHLIST_HISTORYQUOTES, null,
      'csv', 'IMPORT_QUOTES', false, this.historyquoteService,
      this.nameSecuritycurrency.getSecuritycurrency().idSecuritycurrency, this.historyquotesWithMissings.supportedCSVFormats,
      BaseSettings.CSV_EXPORT_FORMAT);

    this.visibleUploadFileDialog = true;
  }

  override handleCloseDialog(processedActionData: ProcessedActionData): void {
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

  override resetMenu(historyquote: Historyquote): void {
    if (!this.security.idLinkSecuritycurrency) {
      this.contextMenuItems = this.prepareEditMenu(this.selectedEntity);
      this.contextMenuItems.push({separator: true});
      this.importQuotesMenu.disabled = !this.hasRightsForDeleteEntity(null);
      // Both bulk-quote actions require owner-or-higher-privileges on the parent security (mirroring the backend
      // gate hasRightsOrPrivilegesForEditingOrDelete). The data-quality preconditions follow.
      const noEditRights = !this.hasRightsForCreateEntity(null);
      this.fillGapsMenu.disabled = noEditRights
        || !this.historyquotesWithMissings.historyquoteQuality
        || (this.historyquotesWithMissings.historyquoteQuality.totalMissing === 0
          && this.historyquotesWithMissings.historyquoteQuality.toManyAsCalendar === 0);
      this.deleteCreateTypesMenu.disabled = noEditRights
        || !this.historyquotesWithMissings.historyquoteQuality
        || (!this.historyquotesWithMissings.historyquoteQuality.filledLinear
          && !this.historyquotesWithMissings.historyquoteQuality.manualImported);
      this.contextMenuItems.push(...this.historyquoteSpecMenuItems);
    } else {
      this.contextMenuItems = null;
    }
    this.activePanelService.activatePanel(this, {editMenu: this.contextMenuItems});
  }

  getDateByFormat(date: string): string {
    return AppHelper.getDateByFormat(this.gps, date);
  }

  protected override initialize(): void {
    this.rowsPerPage = this.usersettingsService.readSingleValue(AppSettings.HISTORYQUOTE_TABLE_SETTINGS_STORE) || 30;
    if (this.timeSeriesParams && this.timeSeriesParams.length > 0) {
      this.readAndShowData(this.timeSeriesParams[0]);
    }
  }

  protected prepareCallParam(entity: Historyquote) {
    // For a new history quote the idSecuritycurrency is needed
    this.callParam = new HistoryquoteSecurityCurrency(entity, this.nameSecuritycurrency.getSecuritycurrency());
  }

  protected override getId(entity: Historyquote): number {
    return entity.idHistoryQuote;
  }

  protected override beforeDelete(entity: Historyquote): Historyquote {
    return plainToClass(Historyquote, entity);
  }

  /**
   * The creation of a history quote depends on the right on the security or currency
   */
  protected override hasRightsForCreateEntity(historyquote: Historyquote): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteAuditable(this.gps,
      this.nameSecuritycurrency.getSecuritycurrency());
  }

  /**
   * The deletion of a history quote depends on the right on the security or currency
   */
  protected override hasRightsForDeleteEntity(historyquote: Historyquote): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteAuditable(this.gps,
      this.nameSecuritycurrency.getSecuritycurrency());
  }

  private readAndShowData(timeSeriesParam: TimeSeriesParam): void {
    const stsObservable = timeSeriesParam.currencySecurity
      ? BusinessHelper.getSecurityTransactionSummary(this.securityService,
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

