import { Component, inject } from '@angular/core';
import { AddInstrumentTable } from './add-instrument-table.component';
import { WatchlistService } from '../service/watchlist.service';
import { Watchlist } from '../../entities/watchlist';

import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from '@openng/optimus-ui/table';
import { ButtonModule } from '@openng/optimus-ui/button';

/**
 * Component that displays found instruments in a table for adding to a watchlist. Extends the base AddInstrumentTable
 * functionality specifically for watchlist operations, providing a user interface for searching and selecting
 * securities and currency pairs to add to an existing watchlist.
 */
@Component({
  selector: 'add-instrument-table',
  templateUrl: '../view/add.instrument.table.html',
  imports: [TranslateModule, FormsModule, TableModule, ButtonModule]
})
export class WatchlistAddInstrumentTableComponent extends AddInstrumentTable<Watchlist> {
  /**
   * Creates a new watchlist add instrument table component with required services for data management and user interaction.
   *
   */
  constructor() {
    super(new Watchlist(), inject(WatchlistService));
  }
}
