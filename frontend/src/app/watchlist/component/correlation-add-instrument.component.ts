import {SecuritycurrencySearchBase} from '../../securitycurrency/component/securitycurrency.search.base';
import {Component, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MultipleRequestToOneService} from '../../shared/service/multiple.request.to.one.service';
import {TranslateService} from '@ngx-translate/core';
import {ProcessedAction} from '../../shared/types/processed.action';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {CorrelationSetAddInstrumentTableComponent} from './correlation-set-add-instrument-table.component';

/**
 * Search dialog for adding an existing security or currency pair to a certain correlation set.
 */
@Component({
  selector: 'correlation-add-instrument',
  template: `
    <p-dialog header="{{'ADD_EXISTING_SECURITY' | translate}}" [(visible)]="visibleAddInstrumentDialog"
              [responsive]="true" [style]="{width: '720px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <p class="big-size">{{'SEARCH_DIALOG_HELP' | translate}}</p>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #dynamicFormComponent="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
      <br/>
      <correlation-set-add-instrument-table [tenantLimits]="tenantLimits">
      </correlation-set-add-instrument-table>
    </p-dialog>
  `
})
export class CorrelationAddInstrumentComponent extends SecuritycurrencySearchBase {
  // From parent view
  @Input() visibleAddInstrumentDialog: boolean;
  @Input() idCorrelationSet: number;
  @Input() tenantLimits: TenantLimit[];

  // Access child components
  @ViewChild(CorrelationSetAddInstrumentTableComponent) csaitc: CorrelationSetAddInstrumentTableComponent;

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
    this.csaitc.clearList();
  }

  childLoadData(securitycurrencySearch: SecuritycurrencySearch): void {
    this.csaitc.loadData(this.idCorrelationSet, securitycurrencySearch);
  }

}
