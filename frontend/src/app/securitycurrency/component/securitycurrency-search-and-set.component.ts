import {Component, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {SecuritycurrencySearchBase} from './securitycurrency.search.base';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {SecuritycurrencySearchAndSetTableComponent} from './securitycurrency-search-and-set-table.component';
import {Security} from '../../entities/security';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {MultipleRequestToOneService} from '../../shared/service/multiple.request.to.one.service';

/**
 * Dialog for selecting a security or currency by search criterias.
 */
@Component({
  selector: 'securitycurrency-search-and-set',
  template: `
      <p-dialog header="{{'SET_SECURITY' | translate}}" [(visible)]="visibleDialog" appendTo="body"
                [responsive]="true" [style]="{width: '720px'}" [resizable]="false"
                (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
          <p class="big-size">{{'SEARCH_DIALOG_HELP' | translate}}</p>
          <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                        (submitBt)="submit($event)">
          </dynamic-form>
          <br/>
          <securitycurrency-search-and-set-table [callBackSetSecurity]="this" [supplementCriteria]="supplementCriteria">
          </securitycurrency-search-and-set-table>
      </p-dialog>
  `
})
export class SecuritycurrencySearchAndSetComponent extends SecuritycurrencySearchBase implements CallBackSetSecurity, AfterSetSecurity {

  // From parent view
  @Input() visibleDialog: boolean;
  @Input() callBackSetSecurityWithAfter: CallBackSetSecurityWithAfter;

  // Access child components
  @ViewChild(SecuritycurrencySearchAndSetTableComponent) sissdc: SecuritycurrencySearchAndSetTableComponent;

  // Output for parent view
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  constructor(gps: GlobalparameterService,
              multipleRequestToOneService: MultipleRequestToOneService,
              translateService: TranslateService) {
    super(false, gps, multipleRequestToOneService, translateService);
  }

  onHide(event) {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  setSecurity(security: Security | CurrencypairWatchlist): void {
    this.callBackSetSecurityWithAfter.setSecurity(security, this);
  }

  afterSetSecurity(): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
  }

  childClearList(): void {
    this.sissdc.clearList();
  }

  childLoadData(securitycurrencySearch: SecuritycurrencySearch): void {
    this.sissdc.loadData(securitycurrencySearch);
  }

  protected initialize(): void {
    super.initialize();
  }

}


export interface CallBackSetSecurity {
  setSecurity(security: Security | CurrencypairWatchlist): void;
}

export interface CallBackSetSecurityWithAfter {
  setSecurity(security: Security | CurrencypairWatchlist, afterSetSecurity: AfterSetSecurity): void;
}

export interface AfterSetSecurity {
  afterSetSecurity(): void;
}
