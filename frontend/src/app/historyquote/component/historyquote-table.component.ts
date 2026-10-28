import {
  afterNextRender,
  Component,
  EventEmitter,
  Injector,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  signal,
  SimpleChanges
} from '@angular/core';

import { Historyquote } from '../../entities/historyquote';
import { HistoryquoteSecurityCurrency } from '../model/historyquote.security.currency';
import { HistoryquoteService } from '../service/historyquote.service';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { AppSettings } from '../../shared/app.settings';
import { combineLatest, Subscription } from 'rxjs';
import { AppHelper } from '../../lib/helper/app.helper';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { SecurityTransactionSummary } from '../../entities/view/security.transaction.summary';
import { CurrencypairService } from '../../securitycurrency/service/currencypair.service';
import { CurrencypairWithTransaction } from '../../entities/view/currencypair.with.transaction';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { INameSecuritycurrency } from '../../entities/view/iname.securitycurrency';
import { SecurityTransactionPosition } from '../../entities/view/security.transaction.position';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { SecurityService } from '../../securitycurrency/service/security.service';
import {
  HistoryquoteDeleteBounds,
  HistoryquoteFillGapsBounds
} from '../../securitycurrency/model/historyquote.quality.group';
import { HelpIds } from '../../lib/help/help.ids';
import { TimeSeriesParam } from '../model/time.series.param';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { HistoryquoteTableBase } from './historyquote-table.base';
import { Security } from '../../entities/security';
import { plainToClass } from 'class-transformer';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { HistoryquotesWithMissings } from '../model/historyquotes.with.missings';
import { DataChangedService } from '../../lib/maintree/service/data.changed.service';
import { ConfirmationService, FilterService, MenuItem } from '@openng/optimus-ui/api';
import { FileUploadParam } from '../../lib/generaldialog/model/file.upload.param';
import { AngularSvgIconModule, SvgIconRegistryService } from 'angular-svg-icon';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BaseSettings } from '../../lib/base.settings';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { HistoryquoteQualityComponent } from './historyquote-quality.component';
import { HistoryquoteEditComponent } from './historyquote-edit.component';
import { UploadFileDialogComponent } from '../../lib/generaldialog/component/upload-file-dialog.component';
import { HistoryquoteQualityFillGapsDialogComponent } from './historyquote-quality-fill-gaps-dialog.component';
import { HistoryquoteDeleteDialogComponent } from './historyquote-delete-dialog.component';
import { GlobalSessionNames } from '../../lib/global.session.names';

/**
 * Shows the history quotes in a table. Hosted by {@link HistoryquoteHostComponent}, which feeds
 * it the time-series params parsed from the route and listens for {@code showLegacyRequested}
 * to swap to the legacy archive view.
 */
@Component({
  selector: 'historyquote-table',
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      #cmDiv
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <div class="datatable">
        <configurable-table
          [data]="entityList()"
          [fields]="fields"
          [fieldsRevision]="fieldsRevision()"
          [dataKey]="entityKeyName"
          [selectionMode]="'single'"
          [(selection)]="selectedEntity"
          [multiSortMeta]="multiSortMeta"
          [customSortFn]="customSorter"
          [paginator]="true"
          [rows]="20"
          [firstRowIndex]="firstRow()"
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
          [contextMenuEnabled]="!!contextMenuItems()"
          [showContextMenu]="isActivated()"
          [contextMenuItems]="contextMenuItems()"
          [valueGetterFn]="valueGetter">
          <!-- Caption content projected into table header -->
          <div caption>
            <h4>
              {{ entityNameUpper | translate }}
              {{ nameSecuritycurrency()?.getName() }}
            </h4>
            @if (security(); as security) {
              <span
                >{{ 'TRADING_FROM_TO' | translate }}: {{ getDateByFormat(security.activeFromDate) }} -
                {{ getDateByFormat(security.activeToDate) }}</span
              >
            }
            <historyquote-quality
              [historyquoteQuality]="historyquotesWithMissings()?.historyquoteQuality"
              [securitycurrency]="historyquotesWithMissings()?.securitycurrency" />
          </div>

          <!-- Custom icon cell template for svg-icon rendering -->
          <ng-template #iconCell let-row let-field="field" let-value="value">
            <svg-icon [name]="value" [svgStyle]="{ 'width.px': 14, 'height.px': 14 }" />
          </ng-template>
        </configurable-table>
      </div>
    </div>
    @if (visibleDialog()) {
      <historyquote-edit
        [visibleDialog]="visibleDialog()"
        [callParam]="callParam"
        (closeDialog)="handleCloseDialog($event)" />
    }

    @if (visibleUploadFileDialog()) {
      <upload-file-dialog
        [visibleDialog]="visibleUploadFileDialog()"
        [fileUploadParam]="fileUploadParam"
        (closeDialog)="handleCloseDialogAndRead($event)" />
    }

    @if (visibleFillGapsDialog()) {
      <historyquote-quality-fill-gaps
        [visibleDialog]="visibleFillGapsDialog()"
        [historyquoteQuality]="historyquotesWithMissings()?.historyquoteQuality"
        [securitycurrency]="historyquotesWithMissings()?.securitycurrency"
        [fillGapsBounds]="fillGapsBounds"
        (closeDialog)="handleCloseDialogAndRead($event)" />
    }

    @if (visibleDeleteHistoryquotes()) {
      <historyquote-delete-dialog
        [visibleDialog]="visibleDeleteHistoryquotes()"
        [idSecuritycurrency]="historyquotesWithMissings().securitycurrency.idSecuritycurrency"
        [historyquoteQuality]="historyquotesWithMissings().historyquoteQuality"
        [deleteBounds]="deleteBounds"
        (closeDialog)="handleCloseDialogAndRead($event)" />
    }
  `,
  providers: [DialogService],
  imports: [
    TranslateModule,
    ConfigurableTableComponent,
    AngularSvgIconModule,
    HistoryquoteQualityComponent,
    HistoryquoteEditComponent,
    UploadFileDialogComponent,
    HistoryquoteQualityFillGapsDialogComponent,
    HistoryquoteDeleteDialogComponent
  ]
})
export class HistoryquoteTableComponent extends HistoryquoteTableBase<Historyquote> implements OnChanges, OnDestroy {
  @Input() timeSeriesParams: TimeSeriesParam[];
  @Output() showLegacyRequested = new EventEmitter<void>();

  minDate: Date = new Date(
    sessionStorage.getItem(GlobalSessionNames.OLDEST_TRADING_DAY) ?? BaseSettings.OLDEST_TRADING_DAY_FALLBACK
  );
  maxDate: Date = new Date('2099-12-31');

  /** Id of the legacy menu item, whose translated label receives the number of archived rows. */
  private static readonly SHOW_LEGACY_MENU_ID = 'showLegacy';

  callParam: HistoryquoteSecurityCurrency;
  readonly firstRow = signal(0);

  readonly nameSecuritycurrency = signal<INameSecuritycurrency>(null);
  readonly historyquotesWithMissings = signal<HistoryquotesWithMissings>(null);
  /** The instrument when it is a security, null for a currency pair and undefined until the first response. */
  readonly security = signal<Security>(undefined);
  readonly visibleUploadFileDialog = signal(false);
  fileUploadParam: FileUploadParam;
  readonly visibleFillGapsDialog = signal(false);
  /** Date boundaries of the fill gaps dialog, loaded before it is shown so its date field can be built at once. */
  fillGapsBounds: HistoryquoteFillGapsBounds;
  readonly visibleDeleteHistoryquotes = signal(false);
  /** Oldest and most recent stored price, loaded before the delete dialog is shown. */
  deleteBounds: HistoryquoteDeleteBounds;
  protected transactionPositionList: SecurityTransactionPosition[] = [];
  /** Request of the shown instrument; a newer instrument replaces a request that is still pending. */
  private loadSubscription: Subscription;

  constructor(
    private iconReg: SvgIconRegistryService,
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
    injector: Injector
  ) {
    super(
      AppSettings.HISTORYQUOTE,
      historyquoteService,
      confirmationService,
      messageToastService,
      activePanelService,
      dialogService,
      filterService,
      translateService,
      gps,
      usersettingsService,
      injector
    );

    HistoryquoteTableBase.registerCreateTypeIcons(this.iconReg);
    this.addDateColumn();
    this.addCreateTypeColumn();
    this.addColumnFeqH(DataType.DateTimeString, 'createModifyTime', true, true);
    this.addOhlcvColumns();
    this.applyDefaultDateSort();
    this.prepareTableAndTranslate();
    this.destroyRef.onDestroy(() => this.loadSubscription?.unsubscribe());
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

  uploadImportQuotes(): void {
    this.fileUploadParam = new FileUploadParam(
      HelpIds.HELP_WATCHLIST_HISTORYQUOTES,
      null,
      'csv',
      'IMPORT_QUOTES',
      false,
      this.historyquoteService,
      this.nameSecuritycurrency().getSecuritycurrency().idSecuritycurrency,
      this.historyquotesWithMissings().supportedCSVFormats,
      BaseSettings.CSV_EXPORT_FORMAT
    );

    this.visibleUploadFileDialog.set(true);
  }

  override handleCloseDialog(processedActionData: ProcessedActionData): void {
    super.handleCloseDialog(processedActionData);
  }

  handleCloseDialogAndRead(processedActionData: ProcessedActionData): void {
    this.visibleUploadFileDialog.set(false);
    this.visibleFillGapsDialog.set(false);
    this.visibleDeleteHistoryquotes.set(false);
    this.readDataAndProposeDataChangedEvent(processedActionData);
  }

  /**
   * The stored period is fetched before the dialog is created, because its date fields must be configured with the
   * selectable range while the dynamic form is built.
   */
  deleteCreateTypeQuotes(): void {
    this.historyquoteService
      .getDeleteBounds(this.historyquotesWithMissings().securitycurrency.idSecuritycurrency)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((deleteBounds) => {
        this.deleteBounds = deleteBounds;
        this.visibleDeleteHistoryquotes.set(true);
      });
  }

  /**
   * The date boundaries are fetched before the dialog is created, because its date field must be configured with the
   * selectable range while the dynamic form is built.
   */
  fillLinearGap(): void {
    this.securityService
      .getFillGapsBounds(this.historyquotesWithMissings().securitycurrency.idSecuritycurrency)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((fillGapsBounds) => {
        this.fillGapsBounds = fillGapsBounds;
        this.visibleFillGapsDialog.set(true);
      });
  }

  override resetMenu(historyquote: Historyquote): void {
    const historyquotesWithMissings = this.historyquotesWithMissings();
    // security is null for a currency pair (see readAndShowData) and undefined until the first response arrives;
    // only a security whose quotes are derived from another instrument (idLinkSecuritycurrency) has no own menu.
    if (historyquotesWithMissings && !this.security()?.idLinkSecuritycurrency) {
      const legacyCount = historyquotesWithMissings.legacyCount ?? 0;
      this.publishTableMenu(
        historyquote,
        [
          ...(this.prepareEditMenu(historyquote) ?? []),
          { separator: true },
          ...this.createHistoryquoteMenuItems(historyquotesWithMissings)
        ],
        (translated) => {
          const showLegacy = translated.find(
            (menuItem) => menuItem.id === HistoryquoteTableComponent.SHOW_LEGACY_MENU_ID
          );
          showLegacy && (showLegacy.label = `${showLegacy.label} (${legacyCount})`);
        }
      );
    } else {
      this.publishTableMenu(historyquote, null);
    }
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
    this.callParam = new HistoryquoteSecurityCurrency(entity, this.nameSecuritycurrency().getSecuritycurrency());
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
    return AuditHelper.hasRightsForEditingOrDeleteAuditable(
      this.gps,
      this.nameSecuritycurrency().getSecuritycurrency()
    );
  }

  /**
   * The deletion of a history quote depends on the right on the security or currency
   */
  protected override hasRightsForDeleteEntity(historyquote: Historyquote): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteAuditable(
      this.gps,
      this.nameSecuritycurrency().getSecuritycurrency()
    );
  }

  /**
   * The view specific actions after the CRUD items. Both bulk-quote actions are always shown; missing rights or data
   * quality preconditions disable them, so a limited-edit user still sees them. The legacy view is always reachable,
   * also with an empty archive, so a CSV can be imported there.
   */
  private createHistoryquoteMenuItems(historyquotesWithMissings: HistoryquotesWithMissings): MenuItem[] {
    const quality = historyquotesWithMissings.historyquoteQuality;
    // Both bulk-quote actions require owner-or-higher-privileges on the parent security (mirroring the backend
    // gate hasRightsOrPrivilegesForEditingOrDelete). The data-quality preconditions follow.
    const noEditRights = !this.hasRightsForCreateEntity(null);
    return [
      {
        label: 'DELETE_CREATE_TYPES_QUOTES' + BaseSettings.DIALOG_MENU_SUFFIX,
        disabled: noEditRights || !quality || (!quality.filledLinear && !quality.manualImported),
        command: () => this.deleteCreateTypeQuotes()
      },
      {
        label: 'HISTORYQUOTE_FILL_GAPS' + BaseSettings.DIALOG_MENU_SUFFIX,
        disabled: noEditRights || !quality || (quality.totalMissing === 0 && quality.toManyAsCalendar === 0),
        command: () => this.fillLinearGap()
      },
      {
        id: HistoryquoteTableComponent.SHOW_LEGACY_MENU_ID,
        label: 'HISTORYQUOTE_SHOW_LEGACY',
        command: () => this.showLegacyRequested.emit()
      },
      { separator: true },
      {
        label: 'IMPORT_QUOTES' + BaseSettings.DIALOG_MENU_SUFFIX,
        disabled: !this.hasRightsForDeleteEntity(null),
        command: () => this.uploadImportQuotes()
      },
      {
        label: 'EXPORT_CSV',
        command: () => this.downloadCSvFile(this.historyquotesWithMissings().historyquoteList)
      }
    ];
  }

  private readAndShowData(timeSeriesParam: TimeSeriesParam): void {
    const stsObservable = timeSeriesParam.currencySecurity
      ? BusinessHelper.getSecurityTransactionSummary(
          this.securityService,
          timeSeriesParam.idSecuritycurrency,
          null,
          null,
          false
        )
      : this.currencypairService.getTransactionForCurrencyPair(timeSeriesParam.idSecuritycurrency, false);
    const historyquoteObservable = this.historyquoteService.getHistoryqoutesByIdSecuritycurrencyWithMissing(
      timeSeriesParam.idSecuritycurrency,
      !timeSeriesParam.currencySecurity
    );

    this.loadSubscription?.unsubscribe();
    this.loadSubscription = combineLatest([stsObservable, historyquoteObservable]).subscribe((data: any[]) => {
      const historyquotesWithMissings: HistoryquotesWithMissings = data[1];
      this.nameSecuritycurrency.set(
        timeSeriesParam.currencySecurity
          ? new SecurityTransactionSummary(data[0].transactionPositionList, data[0].securityPositionSummary)
          : new CurrencypairWithTransaction(data[0])
      );
      this.historyquotesWithMissings.set(historyquotesWithMissings);
      this.applyPriceFractionDigits(historyquotesWithMissings.securitycurrency.priceFractionDigits);
      this.notifyViewChanged();
      this.security.set(
        <Security>(timeSeriesParam.currencySecurity ? historyquotesWithMissings.securitycurrency : null)
      );
      this.entityList.set(historyquotesWithMissings.historyquoteList);
      this.refreshSelectedEntity();
      // New rows reset the paginator; the page the user was on is restored once they are rendered.
      this.firstRow.set(0);
      afterNextRender(() => this.firstRow.set(this.firstRowIndexOnPage), { injector: this.injector });
    });
  }

  private readDataAndProposeDataChangedEvent(processedActionData: ProcessedActionData): void {
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }
}
