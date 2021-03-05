import {SvgIconRegistryService} from 'angular-svg-icon';
import {ChangeDetectorRef, Component, Input, OnInit} from '@angular/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {DividendService} from '../service/dividend.service';
import {DividendSplitTableBase} from './dividend.split.table.base';
import {Dividend} from '../../entities/dividend.split';


@Component({
  selector: 'watchlist-dividend-table',
  templateUrl: '../view/dividend.split.table.html'
})
export class WatchlistDividendTableComponent extends DividendSplitTableBase<Dividend> implements OnInit {
  private static EX_DATE = 'exDate';
  @Input() idSecuritycurrency: number;


  constructor(private dividendService: DividendService,
              changeDetectionStrategy: ChangeDetectorRef,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              globalparameterService: GlobalparameterService,
              iconReg: SvgIconRegistryService) {
    super(changeDetectionStrategy, usersettingsService, translateService, globalparameterService, iconReg, 'idDividend',
      WatchlistDividendTableComponent.EX_DATE, 'DIVIDEND');
    this.addColumnFeqH(DataType.DateNumeric, WatchlistDividendTableComponent.EX_DATE, true, false);
    this.addColumn(DataType.NumericInteger, 'createType', 'C', true, false,
      {fieldValueFN: this.getCreateTypeIcon.bind(this), templateName: 'icon', width: 20});
    this.addColumnFeqH(DataType.DateNumeric, 'payDate', true, false);
    this.addColumnFeqH(DataType.NumericRaw, 'amount', true, false);
    this.addColumnFeqH(DataType.String, 'currency', true, false);
    this.prepareTableAndTranslate();
  }

  ngOnInit(): void {
    this.dividendService.getDividendsByIdSecuritycurrency(this.idSecuritycurrency).subscribe((dividend) => {
      this.data = dividend;
    });
  }
}
