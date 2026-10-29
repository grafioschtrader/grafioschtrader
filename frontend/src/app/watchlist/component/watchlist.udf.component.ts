import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { WatchlistTable, WatchListType } from './watchlist.table';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { CurrencypairService } from '../../securitycurrency/service/currencypair.service';
import { TranslateModule } from '@ngx-translate/core';
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
import { DatePipe } from '@angular/common';
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
  imports: [
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
    UDFGeneralEditComponent,
    DatePipe
  ]
})
export class WatchlistUdfComponent extends WatchlistTable implements OnInit, OnDestroy {
  private iconReg: SvgIconRegistryService = inject(SvgIconRegistryService);
  private securityService: SecurityService = inject(SecurityService);
  private currencypairService: CurrencypairService = inject(CurrencypairService);
  private uDFMetadataSecurityService: UDFMetadataSecurityService = inject(UDFMetadataSecurityService);

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

  /**
   * Creates a new WatchlistUdfComponent instance with all required services and dependencies.
   *
   */
  constructor() {
    super(WatchListType.UDF, AppSettings.WATCHLIST_UDF_TABLE_SETTINGS_STORE, WatchlistTable.SINGLE);
    WatchlistUdfComponent.registerIcons(this.iconReg);
    this.addBaseColumns();
    this.fdSecurityList = JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY)).concat(
      JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL)).filter(
        (fd: { entity: string }) => fd.entity === AppSettings.CURRENCYPAIR
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
    this.replaceWatchlistRequest('data').add(
      combineLatest([watchListObservable, tenantLimitObservable, this.uDFMetadataSecurityService.getAllByIdUser()])
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((result: [SecuritycurrencyUDFGroup, TenantLimit[], UDFMetadataSecurity[]]) => {
          this.udfEntityValues = (<SecuritycurrencyUDFGroup>result[0]).udfEntityValues;
          this.udfFillInfo = (<SecuritycurrencyUDFGroup>result[0]).udfFillInfo ?? {};
          this.tenantLimits.set(result[1]);
          this.fillFields = result[2].filter(
            (f) => f.idUser === this.gps.getIdUser() && f.fillSource && f.fillSource !== UDFFillSource.MANUAL
          );
          this.createSecurityPositionList(this.extendSecurityWithUDF(result[0]));
          this.prepareCellMarks();
          this.loading.set(false);
        })
    );
  }

  /**
   * Extends security position list items with their corresponding UDF values by parsing JSON strings
   * and merging UDF data directly into the security currency objects for display purposes.
   */
  private extendSecurityWithUDF(group: SecuritycurrencyUDFGroup): SecuritycurrencyUDFGroup {
    this.udfValuesMap.clear();
    const extend = <T extends { securitycurrency: { idSecuritycurrency?: number } }>(position: T): T => {
      const json = this.udfEntityValues[position.securitycurrency.idSecuritycurrency];
      const values = json ? JSON.parse(json) : {};
      this.udfValuesMap.set(position.securitycurrency.idSecuritycurrency, values);
      return {
        ...position,
        securitycurrency: Object.assign(
          Object.create(Object.getPrototypeOf(position.securitycurrency)),
          position.securitycurrency,
          values
        )
      };
    };
    return {
      ...group,
      securityPositionList: group.securityPositionList.map(extend),
      currencypairPositionList: group.currencypairPositionList.map(extend)
    };
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
    const assetClass = security.assetClass;
    if (id == null || !assetClass) return [];
    return this.fillFields
      .filter(
        (f) =>
          f.categoryTypeEnums.includes(<any>assetClass.categoryType) &&
          f.specialInvestmentInstrumentEnums.includes(<any>assetClass.specialInvestmentInstrument)
      )
      .flatMap((f) => {
        const fieldId = f.idUDFMetadata;
        if (fieldId == null) return [];
        const fieldKey = UDFMetadataHelper.UDF_FIELD_PREFIX + f.idUDFMetadata;
        const info = this.udfFillInfo[id]?.[fieldKey];
        const locked = info?.fillState === UDFFillState.LOCKED;
        const proposalItems: MenuItem[] =
          info?.fillState === UDFFillState.PROPOSED ? this.getProposalMenuItems(f, id, info) : [];
        const action = locked
          ? () => this.uDFMetadataSecurityService.unlockCell(fieldId, id)
          : () => this.uDFMetadataSecurityService.lockCell(fieldId, id);
        return [
          ...proposalItems,
          {
            label: this.translateService.instant(locked ? 'UDF_FILL_UNLOCK' : 'UDF_FILL_LOCK', {
              field: f.description
            }),
            command: () =>
              action()
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe(() => this.getWatchlistWithoutUpdate())
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
    const fieldId = f.idUDFMetadata;
    if (fieldId == null) return [];
    const decisions = [{ idSecuritycurrency, ruleVersion: info.ruleVersion, value: info.proposedValue }];
    return [
      {
        label: this.translateService.instant('UDF_FILL_ACCEPT_PROPOSAL', { field: f.description }),
        command: () =>
          this.uDFMetadataSecurityService
            .acceptProposals(fieldId, decisions)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.getWatchlistWithoutUpdate())
      },
      {
        label: this.translateService.instant('UDF_FILL_REJECT_PROPOSAL', { field: f.description }),
        command: () =>
          this.uDFMetadataSecurityService
            .rejectProposals(fieldId, decisions)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.getWatchlistWithoutUpdate())
      }
    ];
  }

  /**
   * Refreshes all price data by setting loading state and triggering a complete data reload.
   */
  protected override updateAllPrice(): void {
    this.loading.set(true);
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
      switch (DataType[fd.dataType as keyof typeof DataType]) {
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
        DataType[fd.dataType as keyof typeof DataType] === DataType.DateTimeNumeric
          ? DataType.DateTimeString
          : DataType[fd.dataType as keyof typeof DataType],
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
