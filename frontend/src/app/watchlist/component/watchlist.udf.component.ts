import {ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {DialogService} from 'primeng/dynamicdialog';
import {WatchlistTable, WatchListType} from './watchlist.table';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {WatchlistService} from '../service/watchlist.service';
import {ActivatedRoute, Router} from '@angular/router';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {AppSettings} from '../../shared/app.settings';
import {combineLatest, Observable} from 'rxjs';
import {SecuritycurrencyUDFGroup} from '../../entities/view/securitycurrency.group';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {UDFMetadataSecurityService} from '../../shared/udfmeta/service/udf.metadata.security.service';
import {GlobalSessionNames} from '../../shared/global.session.names';
import {FieldDescriptorInputAndShowExtendedSecurity} from '../../shared/udfmeta/model/udf.metadata';
import {DataType} from '../../dynamic-form/models/data.type';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {ColumnConfig, OptionalParams} from '../../lib/datashowbase/column.config';
import {HelpIds} from '../../shared/help/help.ids';
import {WatchlistHelper} from './watchlist.helper';
import {AlarmSetupService} from '../../algo/service/alarm.setup.service';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';

/**
 * Angular component that displays a watchlist with user-defined additional fields (UDF).
 * Extends WatchlistTable to provide UDF-specific functionality including dynamic column generation
 * from UDF metadata and integration of custom field values into the security data display.
 */
@Component({
    templateUrl: '../view/watchlist.data.html',
    styles: [`
    .cell-move {
      cursor: move !important;
    }
  `],
    providers: [DialogService],
    standalone: false
})
export class WatchlistUdfComponent extends WatchlistTable implements OnInit, OnDestroy {

  /** Base name for link icon assets */
  private static readonly LINK_ICON = 'link';

  /** Map of security currency IDs to their UDF values as JSON strings */
  private udfEntityValues: { [idSecuritycurrency: number]: string };

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
   * @param dialogService PrimeNG service for dynamic dialog management
   * @param alarmSetupService Service for alarm configuration
   * @param timeSeriesQuotesService Service for time series quote operations
   * @param dataChangedService Service for data change notifications
   * @param activePanelService Service for panel activation management
   * @param watchlistService Service for watchlist operations
   * @param router Angular router for navigation
   * @param activatedRoute Current activated route
   * @param confirmationService PrimeNG service for confirmation dialogs
   * @param messageToastService Service for displaying toast messages
   * @param productIconService Service for product icon management
   * @param changeDetectionStrategy Angular change detection reference
   * @param filterService PrimeNG service for table filtering
   * @param translateService Angular translation service
   * @param gpsGT Global parameter service for GT-specific settings
   * @param gps Global parameter service for application settings
   * @param usersettingsService Service for user preference management
   */
  constructor(private iconReg: SvgIconRegistryService,
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
    usersettingsService: UserSettingsService) {
    super(WatchListType.UDF, AppSettings.WATCHLIST_UDF_TABLE_SETTINGS_STORE, dialogService, alarmSetupService,
      timeSeriesQuotesService, dataChangedService, activePanelService, watchlistService, router, activatedRoute, confirmationService,
      messageToastService, productIconService, changeDetectionStrategy, filterService, translateService,
      gpsGT, gps, usersettingsService, WatchlistTable.SINGLE);
    WatchlistUdfComponent.registerIcons(iconReg);
    this.addBaseColumns();
    this.fdSecurityList = JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY))
      .concat(JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL)).filter(fd =>
        fd.entity === AppSettings.CURRENCYPAIR));
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
      iconReg.loadSvg(AppSettings.PATH_ASSET_ICONS + WatchlistUdfComponent.LINK_ICON + i + AppSettings.SVG, WatchlistUdfComponent.LINK_ICON + i);
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
    const watchListObservable: Observable<SecuritycurrencyUDFGroup> = this.watchlistService.getWatchlistWithUDFData(this.idWatchlist);
    const tenantLimitObservable: Observable<TenantLimit[]> = this.watchlistService.getSecuritiesCurrenciesWatchlistLimits(this.idWatchlist);
    combineLatest([watchListObservable, tenantLimitObservable]).subscribe((result:[SecuritycurrencyUDFGroup, TenantLimit[]] ) => {
      this.createSecurityPositionList(result[0]);
      this.udfEntityValues = (<SecuritycurrencyUDFGroup>result[0]).udfEntityValues;
      this.tenantLimits = result[1];
      this.extendSecurityWithUDF();
      this.loading = false;
    });
  }

  /**
   * Extends security position list items with their corresponding UDF values by parsing JSON strings
   * and merging UDF data directly into the security currency objects for display purposes.
   */
  private extendSecurityWithUDF(): void {
    this.udfValuesMap.clear();
    this.securityPositionList.forEach(spl => {
      const jsonString = this.udfEntityValues[spl.securitycurrency.idSecuritycurrency];
      if (jsonString) {
        const udfValues: any = JSON.parse(jsonString);
        this.udfValuesMap.set(spl.securitycurrency.idSecuritycurrency, udfValues);
        Object.assign(spl.securitycurrency, udfValues);
      }
    });
  }

  /**
   * Refreshes all price data by setting loading state and triggering a complete data reload.
   */
  protected override updateAllPrice(): void {
    this.loading = true;
    this.getWatchlistWithoutUpdate();
  }

  public override getHelpContextId(): HelpIds {
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
      }
      const cc: ColumnConfig = this.addColumn(DataType[fd.dataType] === DataType.DateTimeNumeric ? DataType.DateTimeString
          : DataType[fd.dataType], WatchlistHelper.SECURITYCURRENCY + '.' + fd.fieldName, fd.description, true,
        true, optionalParam);
      cc.headerTooltipTranslated = fd.descriptionHelp;
    }
  }
}
