import {AddInstrumentTable} from './add-instrument-table.component';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {ChangeDetectorRef, Component} from '@angular/core';
import {FilterService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {CorrelationSetService} from '../service/correlation.set.service';
import {CorrelationSet} from '../../entities/correlation.set';
import {CorrelationComponent} from './correlation.component';

@Component({
  selector: 'correlation-set-add-instrument-table',
  templateUrl: '../view/add.instrument.table.html'
})
export class CorrelationSetAddInstrumentTableComponent extends AddInstrumentTable<CorrelationSet> {

  constructor(dataChangedService: DataChangedService,
              correlationSetService: CorrelationSetService,
              changeDetectionStrategy: ChangeDetectorRef,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(null, dataChangedService, correlationSetService, changeDetectionStrategy,
      filterService, translateService, gps, usersettingsService);
  }

}
