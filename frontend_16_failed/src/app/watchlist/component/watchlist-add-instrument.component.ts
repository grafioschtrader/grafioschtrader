import {Component, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';

import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {SecuritycurrencySearchBase} from '../../securitycurrency/component/securitycurrency.search.base';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {MultipleRequestToOneService} from '../../shared/service/multiple.request.to.one.service';
import {WatchlistAddInstrumentTableComponent} from './watchlist-add-instrument-table.component';

/**
 * Search dialog for adding an existing security or currency pair to a certain watchlist.
 */
@Component({
  selector: 'watchlist-add-instrument',
  template: `
    <p-dialog styleClass="big-dialog" header="{{'ADD_EXISTING_SECURITY' | translate}}" [(visible)]="visibleAddInstrumentDialog"
              [responsive]="true" [style]="{width: '720px'}" [resizable]="false"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <p class="big-size">{{'SEARCH_DIALOG_HELP' | translate}}</p>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #dynamicFormComponent="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
      <br/>
      <add-instrument-table [tenantLimits]="tenantLimits">
      </add-instrument-table>
    </p-dialog>
  `
})
export class WatchlistAddInstrumentComponent extends SecuritycurrencySearchBase {
  // From parent view
  @Input() visibleAddInstrumentDialog: boolean;
  @Input() idWatchlist: number;
  @Input() tenantLimits: TenantLimit[];

  // Access child components
  @ViewChild(WatchlistAddInstrumentTableComponent) waitc: WatchlistAddInstrumentTableComponent;

  // Output for parent view
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  constructor(gps: GlobalparameterService,
              multipleRequestToOneService: MultipleRequestToOneService,
              translateService: TranslateService) {
    super(true, gps, multipleRequestToOneService, translateService);
  }

  onHide(event) {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  closeSearchDialog(event): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  childClearList(): void {
    this.waitc.clearList();
  }

  childLoadData(securitycurrencySearch: SecuritycurrencySearch): void {
    this.waitc.loadData(this.idWatchlist, securitycurrencySearch);
  }

}

