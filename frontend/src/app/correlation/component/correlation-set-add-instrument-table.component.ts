import { AddInstrumentTable } from '../../watchlist/component/add-instrument-table.component';
import { Component, inject } from '@angular/core';
import { CorrelationSetService } from '../service/correlation.set.service';
import { CorrelationSet } from '../../entities/correlation.set';
import { DataType } from '../../lib/dynamic-form/models/data.type';

import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from '@openng/optimus-ui/table';
import { ButtonModule } from '@openng/optimus-ui/button';

/**
 * Table component for selecting and displaying instruments in the correlation set search dialog.
 * Extends the base AddInstrumentTable with correlation set specific functionality and date columns.
 */
@Component({
  selector: 'correlation-set-add-instrument-table',
  templateUrl: '../../watchlist/view/add.instrument.table.html',
  imports: [TranslateModule, FormsModule, TableModule, ButtonModule]
})
export class CorrelationSetAddInstrumentTableComponent extends AddInstrumentTable<CorrelationSet> {
  /**
   * Initializes the correlation set instrument table with required services.
   */
  constructor() {
    super(null, inject(CorrelationSetService));
  }

  /**
   * Adds correlation set specific field definitions to the table.
   * Inserts activeFromDate and activeToDate columns after the base columns.
   */
  protected override addFieldDefinition(): void {
    super.addFieldDefinition();
    this.insertColumnFeqH(1, DataType.DateString, 'activeFromDate', true, false);
    this.insertColumnFeqH(2, DataType.DateString, 'activeToDate', true, false);
  }
}
