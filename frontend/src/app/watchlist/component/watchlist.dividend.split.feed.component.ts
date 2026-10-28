import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { CurrencypairService } from '../../securitycurrency/service/currencypair.service';
import { TranslateModule } from '@ngx-translate/core';
import { AppSettings } from '../../shared/app.settings';
import { WatchlistTable, WatchListType } from './watchlist.table';
import { combineLatest, Observable } from 'rxjs';
import { SecuritycurrencyGroup } from '../../entities/view/securitycurrency.group';
import { TenantLimit } from '../../shared/types/tenant.limit';
import { ColumnConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import { FilterType } from '../../lib/datashowbase/filter.type';
import { HelpIds } from '../../lib/help/help.ids';
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
 * Angular component for displaying and managing watchlist dividend and split feed data.
 * Provides functionality to check the reliability of dividend and split feeds with multi-selection support for removal operations.
 * Extends the base WatchlistTable to add specific dividend and split feed columns and functionality.
 */
@Component({
  templateUrl: '../view/watchlist.data.html',
  styles: [
    `
      .cell-move {
        cursor: move !important;
      }

      table {
        border: 1px solid #555;
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
export class WatchlistDividendSplitFeedComponent extends WatchlistTable implements OnInit, OnDestroy {
  private securityService: SecurityService = inject(SecurityService);
  private currencypairService: CurrencypairService = inject(CurrencypairService);

  /** Map of feed connector IDs to their human-readable names for display purposes */
  // feedConnectorsKV: { [id: string]: string } = {};

  /**
   * Creates a new WatchlistDividendSplitFeedComponent with all required services and dependencies.
   * Initializes the watchlist table with dividend and split feed specific columns and configurations.
   */
  constructor() {
    super(
      WatchListType.DIVIDEND_SPLIT_FEED,
      AppSettings.WATCHLIST_DIVIDEND_SPLIT_FEED_TABLE_SETTINGS_STORE,
      WatchlistTable.MULTIPLE
    );
    this.addBaseColumns();
    this.addColumnFeqH(DataType.String, 'securitycurrency.distributionFrequency', true, true, {
      translateValues: TranslateValue.NORMAL,
      filterType: FilterType.withOptions
    });
    this.addColumnFeqH(DataType.String, 'securitycurrency.idConnectorDividend', true, true, {
      fieldValueFN: this.getFeedConnectorReadableName.bind(this),
      filterType: FilterType.withOptions
    });
    this.addColumnFeqH(DataType.NumericInteger, 'securitycurrency.retryDividendLoad', true, true, {
      filterType: FilterType.likeDataType
    });
    this.addColumnFeqH(DataType.DateNumeric, 'securitycurrency.dividendEarliestNextCheck', true, true, {
      filterType: FilterType.likeDataType
    });
    this.addColumnFeqH(DataType.String, 'securitycurrency.idConnectorSplit', true, true, {
      fieldValueFN: this.getFeedConnectorReadableName.bind(this),
      filterType: FilterType.withOptions
    });
    this.addColumnFeqH(DataType.NumericInteger, 'securitycurrency.retrySplitLoad', true, true, {
      filterType: FilterType.likeDataType
    });
    this.prepareTableAndTranslate();
    this.watchlistHasModifiedFromOutside();
  }

  /**
   * Transforms feed connector IDs into human-readable names for display in table columns.
   * Used as a field value function to convert technical connector identifiers into user-friendly text.
   * @param dataobject The data object containing the row data (unused in current implementation)
   * @param field The column configuration object (unused in current implementation)
   * @param valueField The connector ID value to be transformed into readable name
   */
  getFeedConnectorReadableName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.feedConnectorsKV()[valueField];
  }

  /** Initializes the component by calling base initialization and loading connector data */
  ngOnInit(): void {
    this.init();
    this.loadData();
  }

  /**
   * Retrieves watchlist data specific to dividend and split feeds without triggering price updates.
   * Combines watchlist data and tenant limits in a single operation for efficient loading.
   */
  protected override getWatchlistWithoutUpdate(): void {
    const watchListObservable: Observable<SecuritycurrencyGroup> =
      this.watchlistService.getWatchlistForSplitAndDividend(this.idWatchlist);
    const tenantLimitObservable: Observable<TenantLimit[]> =
      this.watchlistService.getSecuritiesCurrenciesWatchlistLimits(this.idWatchlist);
    this.replaceWatchlistRequest('data').add(
      combineLatest([watchListObservable, tenantLimitObservable])
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((result: [SecuritycurrencyGroup, TenantLimit[]]) => {
          this.createSecurityPositionList(result[0]);
          this.tenantLimits.set(result[1]);
          this.loading.set(false);
        })
    );
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST_DIVIDEND_SPLIT_FEED;
  }

  /** Refreshes all watchlist data by setting loading state and retrieving updated information */
  protected override updateAllPrice(): void {
    this.loading.set(true);
    this.getWatchlistWithoutUpdate();
  }

  /**
   * Loads feed connector mappings and initializes watchlist data.
   * Uses SecurityCurrencyHelper to populate the connector name mappings before loading watchlist content.
   */
  private loadData(): void {
    combineLatest([this.securityService.getFeedConnectors(), this.currencypairService.getFeedConnectors()])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([security, currency]) => {
        this.feedConnectorsKV.set(Object.fromEntries([...security, ...currency].map((c) => [c.id, c.readableName])));
        this.getWatchlistWithoutUpdate();
      });
  }
}
