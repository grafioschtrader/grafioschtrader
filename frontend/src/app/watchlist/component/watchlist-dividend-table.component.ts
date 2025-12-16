import {SvgIconRegistryService, AngularSvgIconModule} from 'angular-svg-icon';
import {Component, Input, OnInit} from '@angular/core';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {DividendService} from '../service/dividend.service';
import {DividendSplitTableBase} from './dividend.split.table.base';
import {Dividend} from '../../entities/dividend.split';
import {FilterService} from 'primeng/api';
import {AppSettings} from '../../shared/app.settings';
import {CommonModule} from '@angular/common';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';

/**
 * Component that displays imported dividends as a table for a specific security.
 * Provides a tabular view of dividend data including ex-date, pay date, amounts, and creation source information.
 */
@Component({
    selector: 'watchlist-dividend-table',
    templateUrl: '../view/dividend.split.table.html',
    standalone: true,
    imports: [
      CommonModule,
      TranslateModule,
      ConfigurableTableComponent,
      AngularSvgIconModule
    ]
})
export class WatchlistDividendTableComponent extends DividendSplitTableBase<Dividend> implements OnInit {
  /** Field name constant for the ex-date column */
  private static EX_DATE = 'exDate';

  /** The unique identifier of the security/currency to display dividends for */
  @Input() idSecuritycurrency: number;


  /**
   * Creates a new watchlist dividend table component and configures the table columns.
   *
   * @param dividendService Service for retrieving dividend data
   * @param filterService PrimeNG service for table filtering functionality
   * @param usersettingsService Service for persisting user table preferences
   * @param translateService Angular translation service for internationalization
   * @param gps Global parameter service for locale and formatting settings
   * @param iconReg Service for registering SVG icons used in the table
   */
  constructor(private dividendService: DividendService,
              filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              iconReg: SvgIconRegistryService) {
    super(filterService, usersettingsService, translateService, gps, iconReg, 'idDividend',
      WatchlistDividendTableComponent.EX_DATE, AppSettings.DIVIDEND.toUpperCase());
    this.addColumnFeqH(DataType.DateNumeric, WatchlistDividendTableComponent.EX_DATE, true, false);
    this.addColumn(DataType.NumericInteger, 'createType', 'C', true, false,
      {fieldValueFN: this.getCreateTypeIcon.bind(this), templateName: 'icon', width: 20});
    this.addColumnFeqH(DataType.DateNumeric, 'payDate', true, false);
    this.addColumnFeqH(DataType.NumericRaw, 'amount', true, false);
    this.addColumnFeqH(DataType.NumericRaw, 'amountAdjusted', true, false);
    this.addColumnFeqH(DataType.String, 'currency', true, false);
    this.prepareTableAndTranslate();
  }

  /** Initializes the component by loading dividend data for the specified security */
  ngOnInit(): void {
    this.dividendService.getDividendsByIdSecuritycurrency(this.idSecuritycurrency).subscribe((dividend) => {
      this.data = dividend;
    });
  }
}
