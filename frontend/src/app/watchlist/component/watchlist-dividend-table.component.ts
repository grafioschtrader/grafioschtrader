import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { Component, OnInit, input, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { DividendService } from '../service/dividend.service';
import { DividendSplitTableBase } from './dividend.split.table.base';
import { Dividend } from '../../entities/dividend.split';
import { AppSettings } from '../../shared/app.settings';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';

/**
 * Component that displays imported dividends as a table for a specific security.
 * Provides a tabular view of dividend data including ex-date, pay date, amounts, and creation source information.
 */
@Component({
  selector: 'watchlist-dividend-table',
  templateUrl: '../view/dividend.split.table.html',
  imports: [TranslateModule, ConfigurableTableComponent, AngularSvgIconModule]
})
export class WatchlistDividendTableComponent extends DividendSplitTableBase<Dividend> implements OnInit {
  private dividendService: DividendService = inject(DividendService);

  /** Field name constant for the ex-date column */
  private static EX_DATE = 'exDate';

  /** The unique identifier of the security/currency to display dividends for */
  readonly idSecuritycurrency = input<number>(undefined);

  /**
   * Creates a new watchlist dividend table component and configures the table columns.
   *
   */
  constructor() {
    super('idDividend', WatchlistDividendTableComponent.EX_DATE, AppSettings.DIVIDEND.toUpperCase());
    this.addColumnFeqH(DataType.DateNumeric, WatchlistDividendTableComponent.EX_DATE, true, false);
    this.addColumn(DataType.NumericInteger, 'createType', 'C', true, false, {
      fieldValueFN: this.getCreateTypeIcon.bind(this),
      templateName: 'icon',
      width: 20
    });
    this.addColumnFeqH(DataType.DateNumeric, 'payDate', true, false);
    this.addColumnFeqH(DataType.NumericRaw, 'amount', true, false);
    this.addColumnFeqH(DataType.NumericRaw, 'amountAdjusted', true, false);
    this.addColumnFeqH(DataType.String, 'currency', true, false);
    this.prepareTableAndTranslate();
  }

  /** Initializes the component by loading dividend data for the specified security */
  ngOnInit(): void {
    this.dividendService
      .getDividendsByIdSecuritycurrency(this.idSecuritycurrency())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((dividend) => {
        this.data.set(dividend);
      });
  }
}
