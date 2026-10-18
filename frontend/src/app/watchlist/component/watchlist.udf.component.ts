import { ChangeDetectorRef, Component, Injector, OnDestroy, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { WatchlistTable, WatchListType } from './watchlist.table';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { CurrencypairService } from '../../securitycurrency/service/currencypair.service';
import { TimeSeriesQuotesService } from '../../historyquote/service/time.series.quotes.service';
import { DataChangedService } from '../../lib/maintree/service/data.changed.service';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { WatchlistService } from '../service/watchlist.service';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService, FilterService } from '@openng/optimus-ui/api';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { ProductIconService } from '../../securitycurrency/service/product.icon.service';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { AppSettings } from '../../shared/app.settings';
import { combineLatest, Observable } from 'rxjs';
import { SecuritycurrencyUDFGroup } from '../../entities/view/securitycurrency.group';
import { TenantLimit } from '../../shared/types/tenant.limit';
import { UDFMetadataSecurityService } from '../../udfmetasecurity/service/udf.metadata.security.service';
import { GlobalSessionNames } from '../../lib/global.session.names';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { SvgIconRegistryService } from 'angular-svg-icon';
import { ColumnConfig, OptionalParams } from '../../lib/datashowbase/column.config';
import { FilterType } from '../../lib/datashowbase/filter.type';
import { WatchlistHelper } from './watchlist.helper';
import { AlarmSetupService } from '../../algo/service/alarm.setup.service';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { HelpIds } from '../../lib/help/help.ids';
import { BaseSettings } from '../../lib/base.settings';
import {
  FieldDescriptorInputAndShowExtendedSecurity,
  UDFFillInfo,
  UDFFillSource,
  UDFFillState,
  UDFMetadataSecurity
} from '../../udfmetasecurity/model/udf.metadata.security';
import { CellMark } from '../../lib/datashowbase/configurable-table.component';
import { MenuItem } from '@openng/optimus-ui/api';
import { SecuritycurrencyPosition } from '../../entities/view/securitycurrency.position';
import { Security } from '../../entities/security';
import { Currencypair } from '../../entities/currencypair';
import { CurrencypairWatchlist } from '../../entities/view/currencypair.watchlist';
import { UDFMetadataHelper } from '../../lib/udfmeta/components/udf.metadata.helper';
import { CommonModule } from '@angular/common';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { TransactionSecurityTableComponent } from '../../transaction/component/transaction-security-table.component';
import { TransactionSecurityMarginTreetableComponent } from '../../transaction/component/transaction-security-margin-treetable.component';
import { SecuritycurrencyUdfComponent } from './securitycurrency-udf.component';
import { WatchlistPriceFeedExpandedComponent } from './watchlist-price-feed-expanded.component';
import { WatchlistDividendTableComponent } from './watchlist-dividend-table.component';
import { WatchlistSecuritysplitTableComponent } from './watchlist-securitysplit-table.component';
import { WatchlistAddInstrumentDialogComponent } from './watchlist-add-instrument-dialog.component';
import { CurrencypairEditComponent } from '../../shared/securitycurrency/currencypair-edit.component';
import { SecurityEditComponent } from '../../shared/securitycurrency/security-edit.component';
import { SecurityDerivedEditComponent } from '../../securitycurrency/component/security-derived-edit.component';
import { SecurityUDFEditComponent } from '../../securitycurrency/component/security-udf-edit.component';
import { AlgoStrategyEditComponent } from '../../algo/component/algo-strategy-edit.component';
import { WatchlistAddEditPriceProblemInstrumentComponent } from './watchlist-add-edit-price-problem-instrument.component';
import { TransactionSecurityEditComponent } from '../../transaction/component/transaction-security-edit.component';
import { UDFGeneralEditComponent } from '../../lib/udfmeta/components/udf-general-edit.component';

/**
 * Angular component that displays a watchlist with user-defined additional fields (UDF).
 * Extends WatchlistTable to provide UDF-specific functionality including dynamic column generation
 * from UDF metadata and integration of custom field values into the security data display.
 */
@Component({
  templateUrl: '../view/watchlist.data.html',
  styles: [
    `
      .cell-move {
        cursor: move !important;
      }
    `
  ],
  providers: [DialogService],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    CommonModule,
    TranslateModule,
    ConfigurableTableComponent,
    AngularSvgIconModule,
    TooltipModule,
    TransactionSecurityTableComponent,
    TransactionSecurityMarginTreetableComponent,
    SecuritycurrencyUdfComponent,
    WatchlistPriceFeedExpandedComponent,
    WatchlistDividendTableComponent,
    WatchlistSecuritysplitTableComponent,
    WatchlistAddInstrumentDialogComponent,
    CurrencypairEditComponent,
    SecurityEditComponent,
    SecurityDerivedEditComponent,
    SecurityUDFEditComponent,
    AlgoStrategyEditComponent,
    WatchlistAddEditPriceProblemInstrumentComponent,
    TransactionSecurityEditComponent,
    UDFGeneralEditComponent
  ]
})
export class WatchlistUdfComponent extends WatchlistTable implements OnInit, OnDestroy {
  /** Base name for link icon assets */
  private static readonly LINK_ICON = 'link';

  /** Map of security currency IDs to their UDF values as JSON strings */
  private udfEntityValues: { [idSecuritycurrency: number]: string };

  /** Fill marks of the automatically filled and the locked cells, by security and field key */
  private udfFillInfo: { [idSecuritycurrency: number]: { [fieldKey: string]: UDFFillInfo } } = {};

  /** Prepared marks of the cells, keyed by security ID and field key, so no cell translates on each check */
  private cellMarks = new Map<string, CellMark>();

  /** The own fields of the user whose fill source is not manual; they get the cell actions of the context menu */
  private fillFields: UDFMetadataSecurity[] = [];

  /** List of field descriptors for security and general UDF metadata */
  private readonly fdSecurityList: FieldDescriptorInputAndShowExtendedSecurity[];

  /** Regular expression to extract trailing numbers from field names */
  fieldNumberRegex = /\d+$/;

  /**
   * Creates a new WatchlistUdfComponent instance with all required services and dependencies.
   *
   * @param iconReg Service for registering SVG icons
   * @param securityService Service for security-related operations
   * @param currencypairService Service for currency pair operations
   * @param uDFMetadataSecurityService Service for UDF metadata operations
   * @param dialogService Optimus service for dynamic dialog management
   * @param alarmSetupService Service for alarm configuration
   * @param timeSeriesQuotesService Service for time series quote operations
   * @param dataChangedService Service for data change notifications
   * @param activePanelService Service for panel activation management
   * @param watchlistService Service for watchlist operations
   * @param router Angular router for navigation
   * @param activatedRoute Current activated route
   * @param confirmationService Optimus service for confirmation dialogs
   * @param messageToastService Service for displaying toast messages
   * @param productIconService Service for product icon management
   * @param changeDetectionStrategy Angular change detection reference
   * @param filterService Optimus service for table filtering
   * @param translateService Angular translation service
   * @param gpsGT Global parameter service for GT-specific settings
   * @param gps Global parameter service for application settings
   * @param usersettingsService Service for user preference management
   */
  constructor(
    private iconReg: SvgIconRegistryService,
    private securityService: SecurityService,
    private currencypairService: CurrencypairService,
    private uDFMetadataSecurityService: UDFMetadataSecurityService,
    dialogService: DialogService,
    alarmSetupService: AlarmSetupService,
    timeSeriesQuotesService: TimeSeriesQuotesService,
    dataChangedService: DataChangedService,
    activePanelService: ActivePanelService,
    watchlistService: WatchlistService,
    router: Router,
    activatedRoute: ActivatedRoute,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    productIconService: ProductIconService,
    changeDetectionStrategy: ChangeDetectorRef,
    filterService: FilterService,
    translateService: TranslateService,
    gpsGT: GlobalparameterGTService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(
      WatchListType.UDF,
      AppSettings.WATCHLIST_UDF_TABLE_SETTINGS_STORE,
      dialogService,
      alarmSetupService,
      timeSeriesQuotesService,
      dataChangedService,
      activePanelService,
      watchlistService,
      router,
      activatedRoute,
      confirmationService,
      messageToastService,
      productIconService,
      changeDetectionStrategy,
      filterService,
      translateService,
      gpsGT,
      gps,
      usersettingsService,
      WatchlistTable.SINGLE,
      injector
    );
    WatchlistUdfComponent.registerIcons(iconReg);
    this.addBaseColumns();
    this.fdSecurityList = JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY)).concat(
      JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL)).filter(
        (fd) => fd.entity === AppSettings.CURRENCYPAIR
      )
    );
    this.createColumnsFromUDFMetaData();
    this.prepareTableAndTranslate();
    this.watchlistHasModifiedFromOutside();
  }

  /**
   * Registers SVG icons for link functionality by loading numbered link icons (0-3) into the icon registry.
   *
   * @param iconReg The SVG icon registry service to register icons with
   */
  private static registerIcons(iconReg: SvgIconRegistryService): void {
    for (let i = 0; i < 4; i++) {
      iconReg.loadSvg(
        BaseSettings.PATH_ASSET_ICONS + WatchlistUdfComponent.LINK_ICON + i + BaseSettings.SVG,
        WatchlistUdfComponent.LINK_ICON + i
      );
    }
  }

  /**
   * Angular lifecycle hook that initializes the component by setting up the watchlist and loading data.
   */
  ngOnInit(): void {
    this.init();
    this.getWatchlistWithoutUpdate();
  }

  /**
   * Loads watchlist data with UDF information and tenant limits without triggering price updates.
   * Combines watchlist UDF data and tenant limit observables to populate the component state.
   */
  protected override getWatchlistWithoutUpdate(): void {
    const watchListObservable: Observable<SecuritycurrencyUDFGroup> = this.watchlistService.getWatchlistWithUDFData(
      this.idWatchlist
    );
    const tenantLimitObservable: Observable<TenantLimit[]> =
      this.watchlistService.getSecuritiesCurrenciesWatchlistLimits(this.idWatchlist);
    combineLatest([
      watchListObservable,
      tenantLimitObservable,
      this.uDFMetadataSecurityService.getAllByIdUser()
    ]).subscribe((result: [SecuritycurrencyUDFGroup, TenantLimit[], UDFMetadataSecurity[]]) => {
      this.createSecurityPositionList(result[0]);
      this.udfEntityValues = (<SecuritycurrencyUDFGroup>result[0]).udfEntityValues;
      this.udfFillInfo = (<SecuritycurrencyUDFGroup>result[0]).udfFillInfo ?? {};
      this.tenantLimits = result[1];
      this.fillFields = result[2].filter(
        (f) => f.idUser === this.gps.getIdUser() && f.fillSource && f.fillSource !== UDFFillSource.MANUAL
      );
      this.extendSecurityWithUDF();
      this.prepareCellMarks();
      this.loading = false;
    });
  }

  /**
   * Extends security position list items with their corresponding UDF values by parsing JSON strings
   * and merging UDF data directly into the security currency objects for display purposes.
   */
  private extendSecurityWithUDF(): void {
    this.udfValuesMap.clear();
    this.securityPositionList.forEach((spl) => {
      const jsonString = this.udfEntityValues[spl.securitycurrency.idSecuritycurrency];
      if (jsonString) {
        const udfValues: any = JSON.parse(jsonString);
        this.udfValuesMap.set(spl.securitycurrency.idSecuritycurrency, udfValues);
        Object.assign(spl.securitycurrency, udfValues);
      }
    });
  }

  /**
   * Translates the fill marks once per load: a wand for a value the fill source wrote or adopted and that the cell
   * still holds, a lock for a cell the user took over.
   */
  private prepareCellMarks(): void {
    this.cellMarks.clear();
    for (const [idSecuritycurrency, marks] of Object.entries(this.udfFillInfo)) {
      for (const [fieldKey, info] of Object.entries(marks)) {
        this.cellMarks.set(idSecuritycurrency + '|' + fieldKey, this.createCellMark(info));
      }
    }
  }

  /**
   * Creates the mark of one cell with its translated tooltip.
   *
   * @param info - The fill mark sent by the server
   * @returns The mark shown before the cell content
   */
  private createCellMark(info: UDFFillInfo): CellMark {
    if (info.fillState === UDFFillState.PROPOSED) {
      return {
        iconClass: 'fa fa-lightbulb-o',
        tooltip: this.translateService.instant('UDF_FILL_PROPOSAL_TOOLTIP', { evidence: info.evidence ?? '' }),
        text: info.proposedValue
      };
    }
    if (info.fillState === UDFFillState.LOCKED) {
      return { iconClass: 'fa fa-lock', tooltip: this.translateService.instant('UDF_FILL_LOCKED_TOOLTIP') };
    }
    const tooltip: string = this.translateService.instant('UDF_FILL_AUTOMATIC_TOOLTIP', {
      source: this.translateService.instant(info.fillSource),
      checkedTime: info.checkedTime ? new Date(info.checkedTime).toLocaleString(this.gps.getUserLang()) : ''
    });
    return { iconClass: 'fa fa-magic', tooltip: info.sourceUrl ? tooltip + ' ' + info.sourceUrl : tooltip };
  }

  /**
   * Returns the fill mark of a user-defined field cell.
   *
   * @param position - Row of the table
   * @param field - Column configuration of the cell
   * @returns The mark, or null when the cell is neither automatic nor locked
   */
  override cellMark(position: SecuritycurrencyPosition<Security | Currencypair>, field: ColumnConfig): CellMark | null {
    const prefix = WatchlistHelper.SECURITYCURRENCY + '.';
    if (this.cellMarks.size === 0 || !field.field.startsWith(prefix)) {
      return null;
    }
    const key = position.securitycurrency.idSecuritycurrency + '|' + field.field.substring(prefix.length);
    return this.cellMarks.get(key) ?? null;
  }

  /**
   * Adds the cell actions of the automatically filled fields to the edit menu: take a cell over manually, or let a
   * taken over cell be filled automatically again.
   *
   * @param securitycurrencyPosition - Selected position or null
   * @returns The edit menu items
   */
  protected override getEditMenuItems(
    securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>
  ): MenuItem[] {
    const menuItems = super.getEditMenuItems(securitycurrencyPosition);
    if (
      securitycurrencyPosition &&
      !(securitycurrencyPosition.securitycurrency instanceof CurrencypairWatchlist) &&
      this.fillFields.length > 0
    ) {
      const fillItems = this.getFillCellMenuItems(<Security>securitycurrencyPosition.securitycurrency);
      if (fillItems.length > 0) {
        menuItems.push({ separator: true }, { label: 'UDF_FILL_CELLS', items: fillItems });
      }
    }
    return menuItems;
  }

  /**
   * Creates one cell action per automatically filled field that applies to the asset class of the security. The labels
   * are translated here because they carry the field description.
   *
   * @param security - The selected security
   * @returns The menu items of the cell actions
   */
  private getFillCellMenuItems(security: Security): MenuItem[] {
    const id = security.idSecuritycurrency;
    return this.fillFields
      .filter(
        (f) =>
          f.categoryTypeEnums.includes(<any>security.assetClass.categoryType) &&
          f.specialInvestmentInstrumentEnums.includes(<any>security.assetClass.specialInvestmentInstrument)
      )
      .flatMap((f) => {
        const fieldKey = UDFMetadataHelper.UDF_FIELD_PREFIX + f.idUDFMetadata;
        const info = this.udfFillInfo[id]?.[fieldKey];
        const locked = info?.fillState === UDFFillState.LOCKED;
        const proposalItems: MenuItem[] =
          info?.fillState === UDFFillState.PROPOSED ? this.getProposalMenuItems(f, id, info) : [];
        const action = locked
          ? () => this.uDFMetadataSecurityService.unlockCell(f.idUDFMetadata, id)
          : () => this.uDFMetadataSecurityService.lockCell(f.idUDFMetadata, id);
        return [
          ...proposalItems,
          {
            label: this.translateService.instant(locked ? 'UDF_FILL_UNLOCK' : 'UDF_FILL_LOCK', {
              field: f.description
            }),
            command: () => action().subscribe(() => this.getWatchlistWithoutUpdate())
          }
        ];
      });
  }

  /**
   * Accept and reject of a proposal of a language model in a cell. Both name the version and value the user sees.
   *
   * @param f - The field with the fill source LLM
   * @param idSecuritycurrency - The security
   * @param info - The fill mark of the cell with the proposal
   * @returns The two menu items
   */
  private getProposalMenuItems(f: UDFMetadataSecurity, idSecuritycurrency: number, info: UDFFillInfo): MenuItem[] {
    const decisions = [{ idSecuritycurrency, ruleVersion: info.ruleVersion, value: info.proposedValue }];
    return [
      {
        label: this.translateService.instant('UDF_FILL_ACCEPT_PROPOSAL', { field: f.description }),
        command: () =>
          this.uDFMetadataSecurityService
            .acceptProposals(f.idUDFMetadata, decisions)
            .subscribe(() => this.getWatchlistWithoutUpdate())
      },
      {
        label: this.translateService.instant('UDF_FILL_REJECT_PROPOSAL', { field: f.description }),
        command: () =>
          this.uDFMetadataSecurityService
            .rejectProposals(f.idUDFMetadata, decisions)
            .subscribe(() => this.getWatchlistWithoutUpdate())
      }
    ];
  }

  /**
   * Refreshes all price data by setting loading state and triggering a complete data reload.
   */
  protected override updateAllPrice(): void {
    this.loading = true;
    this.getWatchlistWithoutUpdate();
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST_UDF;
  }

  /**
   * Creates table columns dynamically from UDF metadata by processing field descriptors and configuring
   * column display properties based on data types. Sets up appropriate templates and widths for different field types.
   */
  private createColumnsFromUDFMetaData(): void {
    for (const fd of this.fdSecurityList) {
      const optionalParam: OptionalParams = {};
      switch (DataType[fd.dataType]) {
        case DataType.Boolean:
          optionalParam.templateName = 'check';
          optionalParam.width = 54;
          break;
        case DataType.URLString:
          optionalParam.templateName = 'linkIcon';
          optionalParam.width = 54;
          break;
        case DataType.String:
          if (fd.max > 25) {
            optionalParam.width = 200;
          }
          optionalParam.filterType = FilterType.likeDataType;
          break;
        case DataType.Numeric:
        case DataType.NumericInteger:
        case DataType.NumericShowZero:
          optionalParam.filterType = FilterType.likeDataType;
      }
      const cc: ColumnConfig = this.addColumn(
        DataType[fd.dataType] === DataType.DateTimeNumeric ? DataType.DateTimeString : DataType[fd.dataType],
        WatchlistHelper.SECURITYCURRENCY + '.' + fd.fieldName,
        fd.description,
        true,
        true,
        optionalParam
      );
      cc.headerTooltipTranslated = fd.descriptionHelp;
    }
  }
}
