import {Component, Injector} from '@angular/core';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {SplitPeriodTableBase} from './split.period.table.base';
import {HistoryquotePeriod} from '../../entities/historyquote.period';
import {HistoryquotePeriodService} from '../../securitycurrency/service/historyquote.period.service';
import {FilterService} from 'primeng/api';
import {CommonModule} from '@angular/common';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';
import {AngularSvgIconModule} from 'angular-svg-icon';

/**
 * The split and history quote period table component.
 */
@Component({
    selector: 'security-historyquote-period-edit-table',
    templateUrl: './view/split.period.table.html',
    standalone: true,
    imports: [CommonModule, TranslateModule, TableModule, ButtonModule, AngularSvgIconModule]
})
export class SecurityHistoryquotePeriodEditTableComponent extends SplitPeriodTableBase<HistoryquotePeriod> {
 // readonly dataSortKey = 'fromDate';

  constructor(historyquotePeriodService: HistoryquotePeriodService,
              messageToastService: MessageToastService,
              filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              injector: Injector) {
    super('fromDate', 'SECURITY_PERIODS_FROM_MAX', HistoryquotePeriod, messageToastService, historyquotePeriodService,
      filterService, usersettingsService, translateService, gps, injector);
    this.addColumn(DataType.DateString, 'fromDate', 'FROM_DATE_NEW_PRICE', true, false);
    this.addColumn(DataType.Numeric, 'price', 'CLOSE', true, false);
    this.prepareTableAndTranslate();
  }
}
