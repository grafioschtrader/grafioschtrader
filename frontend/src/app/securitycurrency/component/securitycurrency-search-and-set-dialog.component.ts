import { afterNextRender, Component, viewChild, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { SecuritycurrencySearchBase } from './securitycurrency.search.base';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { SecuritycurrencySearchAndSetTableComponent } from './securitycurrency-search-and-set-table.component';
import { Security } from '../../entities/security';
import { CurrencypairWatchlist } from '../../entities/view/currencypair.watchlist';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';

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
    <p class="big-size">{{ 'SEARCH_DIALOG_HELP' | translate }}</p>
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
    <br />
    <securitycurrency-search-and-set-table [callBackSetSecurity]="this" [supplementCriteria]="supplementCriteria()" />
  `,
  imports: [TranslateModule, DynamicFormComponent, SecuritycurrencySearchAndSetTableComponent]
})
export class SecuritycurrencySearchAndSetDialogComponent
  extends SecuritycurrencySearchBase
  implements CallBackSetSecurity
{
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  private dynamicDialogRef = inject(DynamicDialogRef);

  // Access child components
  readonly sissdc = viewChild(SecuritycurrencySearchAndSetTableComponent);

  constructor() {
    super(false);
    this.supplementCriteria.set(this.dynamicDialogConfig.data.supplementCriteria);
    // The form and the result table exist once the dialog has rendered for the first time.
    afterNextRender(() => this.initialize());
  }

  setSecurity(security: Security | CurrencypairWatchlist): void {
    this.dynamicDialogRef.close(security);
  }

  childClearList(): void {
    this.sissdc().clearList();
  }

  childLoadData(securitycurrencySearch: SecuritycurrencySearch): void {
    this.sissdc().loadData(securitycurrencySearch);
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
