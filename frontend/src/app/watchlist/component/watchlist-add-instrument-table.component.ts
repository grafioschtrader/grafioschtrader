import {Component} from '@angular/core';
import {AddInstrumentTable} from './add-instrument-table.component';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {FilterService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {WatchlistService} from '../service/watchlist.service';
import {Watchlist} from '../../entities/watchlist';

/**
 * Shows found instrument in a table for adding to Watchlist
 */
@Component({
  selector: 'add-instrument-table',
  templateUrl: '../view/add.instrument.table.html'
})
export class WatchlistAddInstrumentTableComponent extends AddInstrumentTable<Watchlist> {

  constructor(dataChangedService: DataChangedService,
              watchlistService: WatchlistService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(new Watchlist(), dataChangedService, watchlistService, filterService, translateService, gps, usersettingsService);
  }

}
