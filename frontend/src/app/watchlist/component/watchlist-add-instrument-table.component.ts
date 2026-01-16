import {Component, Injector} from '@angular/core';
import {AddInstrumentTable} from './add-instrument-table.component';
import {DataChangedService} from '../../lib/maintree/service/data.changed.service';
import {FilterService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {WatchlistService} from '../service/watchlist.service';
import {Watchlist} from '../../entities/watchlist';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {FormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';

/**
 * Component that displays found instruments in a table for adding to a watchlist. Extends the base AddInstrumentTable
 * functionality specifically for watchlist operations, providing a user interface for searching and selecting
 * securities and currency pairs to add to an existing watchlist.
 */
@Component({
    selector: 'add-instrument-table',
    templateUrl: '../view/add.instrument.table.html',
    standalone: true,
    imports: [CommonModule, TranslateModule, FormsModule, TableModule, ButtonModule]
})
export class WatchlistAddInstrumentTableComponent extends AddInstrumentTable<Watchlist> {

  /**
   * Creates a new watchlist add instrument table component with required services for data management and user interaction.
   *
   * @param dataChangedService Service for handling and broadcasting data change events across components
   * @param watchlistService Service for watchlist-specific operations including adding instruments to watchlists
   * @param filterService PrimeNG service for table filtering capabilities
   * @param translateService Angular service for internationalization and text translation
   * @param gps Global parameter service providing application-wide settings and user preferences
   * @param usersettingsService Service for persisting and retrieving user-specific table configuration settings
   */
  constructor(dataChangedService: DataChangedService,
              watchlistService: WatchlistService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService,
              injector: Injector) {
    super(new Watchlist(), dataChangedService, watchlistService, filterService, translateService, gps, usersettingsService, injector);
  }

}
