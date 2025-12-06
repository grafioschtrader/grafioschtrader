import {Component, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {TranslateService, TranslateModule} from '@ngx-translate/core';

import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {SecuritycurrencySearchBase} from '../../securitycurrency/component/securitycurrency.search.base';
import {TenantLimit} from '../../shared/types/tenant.limit';
import {MultipleRequestToOneService} from '../../shared/service/multiple.request.to.one.service';
import {WatchlistAddInstrumentTableComponent} from './watchlist-add-instrument-table.component';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

/**
 * Search dialog for adding an existing security or currency pair to a certain watchlist.
 *
 * Provides a modal dialog with search functionality and results table that allows users
 * to find and select securities or currency pairs to add to a watchlist. Extends
 * SecuritycurrencySearchBase for common search functionality.
 */
@Component({
    selector: 'watchlist-add-instrument',
    template: `
    <p-dialog styleClass="big-dialog" header="{{'ADD_EXISTING_SECURITY' | translate}}" [(visible)]="visibleAddInstrumentDialog"
              [style]="{width: '720px'}" [resizable]="false"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <p class="big-size">{{'SEARCH_DIALOG_HELP' | translate}}</p>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #dynamicFormComponent="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
      <br/>
      <add-instrument-table [tenantLimits]="tenantLimits">
      </add-instrument-table>
    </p-dialog>
  `,
    standalone: true,
    imports: [
      TranslateModule,
      DialogModule,
      DynamicFormModule,
      WatchlistAddInstrumentTableComponent
    ]
})
export class WatchlistAddInstrumentComponent extends SecuritycurrencySearchBase {
  /** Controls the visibility of the add instrument dialog. */
  @Input() visibleAddInstrumentDialog: boolean;
  /** The ID of the watchlist to add instruments to. */
  @Input() idWatchlist: number;
  /** Tenant limits for securities and currencies in the watchlist. */
  @Input() tenantLimits: TenantLimit[];

  /** Reference to the child table component that displays search results. */
  @ViewChild(WatchlistAddInstrumentTableComponent) waitc: WatchlistAddInstrumentTableComponent;

  /** Event emitted when the dialog is closed, providing the result of the operation. */
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();


  /**
   * Creates an instance of WatchlistAddInstrumentComponent.
   *
   * @param {GlobalparameterService} gps - Global parameters service
   * @param {MultipleRequestToOneService} multipleRequestToOneService - Service for handling multiple requests
   * @param {TranslateService} translateService - Angular translation service
   */
  constructor(gps: GlobalparameterService,
              multipleRequestToOneService: MultipleRequestToOneService,
              translateService: TranslateService) {
    super(true, gps, multipleRequestToOneService, translateService);
  }

  /** Handles dialog hide event and emits close dialog event with no change action. */
  onHide(event) {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  /** Closes the search dialog and emits close dialog event with no change action. */
  override closeSearchDialog(event): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  /** Clears the search results list in the child table component. */
  childClearList(): void {
    this.waitc.clearList();
  }

  /**
   * Loads search data into the child table component.
   *
   * @param {SecuritycurrencySearch} securitycurrencySearch - Search criteria for securities and currencies
   */
  childLoadData(securitycurrencySearch: SecuritycurrencySearch): void {
    this.waitc.loadData(this.idWatchlist, securitycurrencySearch);
  }

}

