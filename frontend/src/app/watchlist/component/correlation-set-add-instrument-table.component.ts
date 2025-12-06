import {AddInstrumentTable} from './add-instrument-table.component';
import {DataChangedService} from '../../lib/maintree/service/data.changed.service';
import {Component} from '@angular/core';
import {FilterService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {CorrelationSetService} from '../service/correlation.set.service';
import {CorrelationSet} from '../../entities/correlation.set';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {FormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';

/**
 * Table component for selecting and displaying instruments in the correlation set search dialog.
 * Extends the base AddInstrumentTable with correlation set specific functionality and date columns.
 */
@Component({
    selector: 'correlation-set-add-instrument-table',
    templateUrl: '../view/add.instrument.table.html',
    standalone: true,
    imports: [CommonModule, TranslateModule, FormsModule, TableModule, ButtonModule]
})
export class CorrelationSetAddInstrumentTableComponent extends AddInstrumentTable<CorrelationSet> {

  /**
   * Initializes the correlation set instrument table with required services.
   * @param dataChangedService Service for handling data change notifications
   * @param correlationSetService Service for correlation set operations
   * @param filterService PrimeNG filter service for table filtering
   * @param translateService Service for internationalization
   * @param gps Global parameter service for application settings
   * @param usersettingsService Service for user preference management
   */
  constructor(dataChangedService: DataChangedService,
              correlationSetService: CorrelationSetService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(null, dataChangedService, correlationSetService, filterService, translateService, gps, usersettingsService);
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
