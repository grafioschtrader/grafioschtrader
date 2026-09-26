import { AddInstrumentTable } from '../../watchlist/component/add-instrument-table.component';
import { DataChangedService } from '../../lib/maintree/service/data.changed.service';
import { Component, Injector, ChangeDetectionStrategy } from '@angular/core';
import { FilterService } from '@openng/optimus-ui/api';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { AlgoAssetclassService } from '../service/algo.assetclass.service';
import { AlgoAssetclass } from '../model/algo.assetclass';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { CommonModule } from '@angular/common';
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
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [CommonModule, TranslateModule, FormsModule, TableModule, ButtonModule]
})
export class AlgoAssetclassAddInstrumentTableComponent extends AddInstrumentTable<AlgoAssetclass> {
  /**
   * Creates the result table of the custom category search.
   *
   * @param dataChangedService - Notifies the hierarchy view after instruments were added
   * @param algoAssetclassService - Searches the instruments and adds them to the custom category
   * @param filterService - Optimus filter service for table filtering
   * @param translateService - Service for internationalization
   * @param gps - Global parameter service for application settings
   * @param usersettingsService - Service for user preference management
   * @param injector - Angular injector passed to the table base
   */
  constructor(
    dataChangedService: DataChangedService,
    algoAssetclassService: AlgoAssetclassService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(
      null,
      dataChangedService,
      algoAssetclassService,
      filterService,
      translateService,
      gps,
      usersettingsService,
      injector
    );
  }

  /** Adds the activity period of each instrument after the base columns. */
  protected override addFieldDefinition(): void {
    super.addFieldDefinition();
    this.insertColumnFeqH(1, DataType.DateString, 'activeFromDate', true, false);
    this.insertColumnFeqH(2, DataType.DateString, 'activeToDate', true, false);
  }
}
