import {ChangeDetectorRef, Component, Input, OnInit} from '@angular/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {SecuritysplitService} from '../../securitycurrency/service/securitysplit.service';
import {DataType} from '../../dynamic-form/models/data.type';

import {SvgIconRegistryService} from 'angular-svg-icon';
import {DividendSplitTableBase} from './dividend.split.table.base';
import {Securitysplit} from '../../entities/dividend.split';


@Component({
  selector: 'watchlist-securitysplit-table',
  templateUrl: '../view/dividend.split.table.html'
})
export class WatchlistSecuritysplitTableComponent extends DividendSplitTableBase<Securitysplit> implements OnInit {
  private static SPLIT_DATE = 'splitDate';
  @Input() idSecuritycurrency: number;

  constructor(private securitysplitService: SecuritysplitService,
              changeDetectionStrategy: ChangeDetectorRef,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              globalparameterService: GlobalparameterService,
              iconReg: SvgIconRegistryService) {
    super(changeDetectionStrategy, usersettingsService, translateService, globalparameterService, iconReg,
      'idSecuritysplit', WatchlistSecuritysplitTableComponent.SPLIT_DATE, 'SPLIT');

    this.addColumnFeqH(DataType.DateNumeric, 'splitDate', true, false);
    this.addColumn(DataType.NumericInteger, 'createType', 'C', true, false,
      {fieldValueFN: this.getCreateTypeIcon.bind(this), templateName: 'icon', width: 20});
    this.addColumnFeqH(DataType.NumericInteger, 'fromFactor', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'toFactor', true, false);
    this.prepareTableAndTranslate();
  }

  ngOnInit(): void {
    this.securitysplitService.getSecuritysplitsByIdSecuritycurrency(this.idSecuritycurrency).subscribe((securitysplits) => {
      this.data = securitysplits;
    });
  }
}
