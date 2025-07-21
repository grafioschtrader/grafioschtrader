import {Component, Input, OnInit} from '@angular/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {SecuritysplitService} from '../../securitycurrency/service/securitysplit.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {DividendSplitTableBase} from './dividend.split.table.base';
import {Securitysplit} from '../../entities/dividend.split';
import {FilterService} from 'primeng/api';

/**
 * Component that displays security splits in a table format for a specific security.
 * Shows split dates, creation types, and split ratios (from/to factors) with icons indicating data source.
 */
@Component({
    selector: 'watchlist-securitysplit-table',
    templateUrl: '../view/dividend.split.table.html',
    standalone: false
})
export class WatchlistSecuritysplitTableComponent extends DividendSplitTableBase<Securitysplit> implements OnInit {
  /** Field name constant for the split date column */
  private static SPLIT_DATE = 'splitDate';

  /** The ID of the security/currency for which to display split data */
  @Input() idSecuritycurrency: number;

  /**
   * Creates a new watchlist security split table component with required services and column configuration.
   * @param securitysplitService Service for loading security split data from the backend
   * @param filterService PrimeNG filter service for table filtering functionality
   * @param usersettingsService Service for managing user table preferences and settings
   * @param translateService Angular translation service for internationalization
   * @param gps Global parameter service for locale and formatting settings
   * @param iconReg SVG icon registry service for displaying creation type icons
   */
  constructor(private securitysplitService: SecuritysplitService,
              filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              iconReg: SvgIconRegistryService) {
    super(filterService, usersettingsService, translateService, gps, iconReg,
      'idSecuritysplit', WatchlistSecuritysplitTableComponent.SPLIT_DATE, 'SPLIT');
    this.addColumnFeqH(DataType.DateNumeric, 'splitDate', true, false);
    this.addColumn(DataType.NumericInteger, 'createType', 'C', true, false,
      {fieldValueFN: this.getCreateTypeIcon.bind(this), templateName: 'icon', width: 20});
    this.addColumnFeqH(DataType.NumericInteger, 'fromFactor', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'toFactor', true, false);
    this.prepareTableAndTranslate();
  }

  /** Loads security split data for the specified security/currency ID and populates the table */
  ngOnInit(): void {
    this.securitysplitService.getSecuritysplitsByIdSecuritycurrency(this.idSecuritycurrency).subscribe((securitysplits) => {
      this.data = securitysplits;
    });
  }
}
