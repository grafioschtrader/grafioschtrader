import { AddInstrumentTable } from '../../watchlist/component/add-instrument-table.component';
import { Component, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { AlgoAssetclassService } from '../service/algo.assetclass.service';
import { AlgoAssetclass } from '../model/algo.assetclass';
import { DataType } from '../../lib/dynamic-form/models/data.type';

import { FormsModule } from '@angular/forms';
import { TableModule } from '@openng/optimus-ui/table';
import { ButtonModule } from '@openng/optimus-ui/button';

/**
 * Multi-select result table of the instrument search for a custom category of an algo hierarchy. The backend already
 * removes instruments a simulation cannot trade, so every row can be added. The activity period is shown because an
 * instrument that starts after the reference date of the hierarchy only takes part in a simulation from its start.
 */
@Component({
  selector: 'algo-assetclass-add-instrument-table',
  templateUrl: '../../watchlist/view/add.instrument.table.html',
  imports: [TranslateModule, FormsModule, TableModule, ButtonModule]
})
export class AlgoAssetclassAddInstrumentTableComponent extends AddInstrumentTable<AlgoAssetclass> {
  /**
   * Creates the result table of the custom category search.
   *
   */
  constructor() {
    super(null, inject(AlgoAssetclassService));
  }

  /** Adds the activity period of each instrument after the base columns. */
  protected override addFieldDefinition(): void {
    super.addFieldDefinition();
    this.insertColumnFeqH(1, DataType.DateString, 'activeFromDate', true, false);
    this.insertColumnFeqH(2, DataType.DateString, 'activeToDate', true, false);
  }
}
