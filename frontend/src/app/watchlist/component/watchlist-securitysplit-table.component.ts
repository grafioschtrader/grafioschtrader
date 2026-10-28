import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, OnInit, input, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { SecuritysplitService } from '../../securitycurrency/service/securitysplit.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { DividendSplitTableBase } from './dividend.split.table.base';
import { Securitysplit } from '../../entities/dividend.split';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';

/**
 * Component that displays security splits in a table format for a specific security.
 * Shows split dates, creation types, and split ratios (from/to factors) with icons indicating data source.
 */
@Component({
  selector: 'watchlist-securitysplit-table',
  templateUrl: '../view/dividend.split.table.html',
  imports: [TranslateModule, ConfigurableTableComponent, AngularSvgIconModule]
})
export class WatchlistSecuritysplitTableComponent extends DividendSplitTableBase<Securitysplit> implements OnInit {
  private securitysplitService: SecuritysplitService = inject(SecuritysplitService);

  /** Field name constant for the split date column */
  private static SPLIT_DATE = 'splitDate';

  /** The ID of the security/currency for which to display split data */
  readonly idSecuritycurrency = input<number>(undefined);

  /**
   * Creates a new watchlist security split table component with required services and column configuration.
   */
  constructor() {
    super('idSecuritysplit', WatchlistSecuritysplitTableComponent.SPLIT_DATE, 'SPLIT');
    this.addColumnFeqH(DataType.DateNumeric, 'splitDate', true, false);
    this.addColumn(DataType.NumericInteger, 'createType', 'C', true, false, {
      fieldValueFN: this.getCreateTypeIcon.bind(this),
      templateName: 'icon',
      width: 20
    });
    this.addColumnFeqH(DataType.NumericInteger, 'fromFactor', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'toFactor', true, false);
    this.prepareTableAndTranslate();
  }

  /** Loads security split data for the specified security/currency ID and populates the table */
  ngOnInit(): void {
    this.securitysplitService
      .getSecuritysplitsByIdSecuritycurrency(this.idSecuritycurrency())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((securitysplits) => {
        this.data.set(securitysplits);
      });
  }
}
