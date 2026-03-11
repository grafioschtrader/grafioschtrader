import {AfterViewInit, Component, ViewChild} from '@angular/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {SecuritycurrencySearchBase} from './securitycurrency.search.base';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {SecuritycurrencySearchAndSetTableComponent} from './securitycurrency-search-and-set-table.component';
import {Security} from '../../entities/security';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {MultipleRequestToOneService} from '../../shared/service/multiple.request.to.one.service';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';

/**
 * Dialog for selecting a security or currency by search criteria.
 * Opened via DialogService.open() as a top-level DynamicDialog to avoid nested dialog z-index issues.
 *
 * Required DynamicDialogConfig.data:
 * - supplementCriteria: SupplementCriteria
 * - callBackSetSecurityWithAfter: CallBackSetSecurityWithAfter
 */
@Component({
    selector: 'securitycurrency-search-and-set',
    template: `
        <p class="big-size">{{'SEARCH_DIALOG_HELP' | translate}}</p>
        <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                      (submitBt)="submit($event)">
        </dynamic-form>
        <br/>
        <securitycurrency-search-and-set-table [callBackSetSecurity]="this" [supplementCriteria]="supplementCriteria">
        </securitycurrency-search-and-set-table>
  `,
    imports: [
        TranslateModule,
        DynamicFormComponent,
        SecuritycurrencySearchAndSetTableComponent
    ],
    standalone: true
})
export class SecuritycurrencySearchAndSetComponent extends SecuritycurrencySearchBase implements CallBackSetSecurity, AfterViewInit {

  // Access child components
  @ViewChild(SecuritycurrencySearchAndSetTableComponent) sissdc: SecuritycurrencySearchAndSetTableComponent;

  constructor(gps: GlobalparameterService,
              multipleRequestToOneService: MultipleRequestToOneService,
              translateService: TranslateService,
              private dynamicDialogConfig: DynamicDialogConfig,
              private dynamicDialogRef: DynamicDialogRef) {
    super(false, gps, multipleRequestToOneService, translateService);
    this.supplementCriteria = this.dynamicDialogConfig.data.supplementCriteria;
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.initialize());
  }

  setSecurity(security: Security | CurrencypairWatchlist): void {
    this.dynamicDialogRef.close(security);
  }

  childClearList(): void {
    this.sissdc.clearList();
  }

  childLoadData(securitycurrencySearch: SecuritycurrencySearch): void {
    this.sissdc.loadData(securitycurrencySearch);
  }

  protected override initialize(): void {
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
