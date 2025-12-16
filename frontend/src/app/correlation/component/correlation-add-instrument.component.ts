import {SecuritycurrencySearchBase} from '../../securitycurrency/component/securitycurrency.search.base';
import {Component, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {TenantLimit} from '../../shared/types/tenant.limit';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MultipleRequestToOneService} from '../../shared/service/multiple.request.to.one.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ProcessedAction} from '../../lib/types/processed.action';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {CorrelationSetAddInstrumentTableComponent} from './correlation-set-add-instrument-table.component';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';

/**
 * Search dialog component for adding existing securities or currency pairs to a correlation set.
 * Provides a search interface with form controls and displays results in a selectable table format.
 * Handles the complete workflow from search criteria input to instrument selection and addition.
 */
@Component({
    selector: 'correlation-add-instrument',
    template: `
    <p-dialog header="{{'ADD_EXISTING_SECURITY' | translate}}" [(visible)]="visibleAddInstrumentDialog"
              [style]="{width: '720px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <p class="big-size">{{'SEARCH_DIALOG_HELP' | translate}}</p>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #dynamicFormComponent="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
      <br/>
      <correlation-set-add-instrument-table [tenantLimits]="tenantLimits">
      </correlation-set-add-instrument-table>
    </p-dialog>
  `,
    imports: [
      TranslateModule,
      DialogModule,
      DynamicFormComponent,
      CorrelationSetAddInstrumentTableComponent
    ],
    standalone: true
})
export class CorrelationAddInstrumentComponent extends SecuritycurrencySearchBase {
  /** Controls the visibility state of the add instrument dialog */
  @Input() visibleAddInstrumentDialog: boolean;

  /** The unique identifier of the correlation set to add instruments to */
  @Input() idCorrelationSet: number;

  /** Array of tenant limits that constrain the number of instruments that can be added */
  @Input() tenantLimits: TenantLimit[];

  /** Reference to the child table component that displays and manages instrument selection */
  @ViewChild(CorrelationSetAddInstrumentTableComponent) csaitc: CorrelationSetAddInstrumentTableComponent;

  /** Event emitter that notifies parent component when dialog is closed with action data */
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  /**
   * Creates a new correlation add instrument component with required services.
   *
   * @param gps Global parameter service for application-wide settings and user preferences
   * @param multipleRequestToOneService Service for handling multiple concurrent requests efficiently
   * @param translateService Angular translation service for internationalization support
   */
  constructor(gps: GlobalparameterService,
              multipleRequestToOneService: MultipleRequestToOneService,
              translateService: TranslateService) {
    super(true, gps, multipleRequestToOneService, translateService);
  }

  /**
   * Handles dialog hide events and emits close event with no change action.
   * Called when user closes dialog without making changes.
   *
   * @param event The dialog hide event object
   */
  onHide(event) {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  /**
   * Handles search dialog close events and emits close event with no change action.
   * Called when the search functionality is cancelled or closed without selection.
   *
   * @param event The search dialog close event object
   */
  override closeSearchDialog(event): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  /** Clears the instrument list in the child table component, removing all displayed search results. */
  childClearList(): void {
    this.csaitc.clearList();
  }

  /**
   * Loads search data into the child table component based on provided search criteria.
   * Triggers the search operation and populates the table with matching instruments.
   *
   * @param securitycurrencySearch The search criteria object containing filters and parameters
   */
  childLoadData(securitycurrencySearch: SecuritycurrencySearch): void {
    this.csaitc.loadData(this.idCorrelationSet, securitycurrencySearch);
  }

}
