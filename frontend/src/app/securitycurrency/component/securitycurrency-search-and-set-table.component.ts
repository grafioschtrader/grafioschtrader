import { Component, computed, DestroyRef, inject, signal, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Security } from '../../entities/security';
import { TranslateModule } from '@ngx-translate/core';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { SecurityService } from '../service/security.service';
import { CallBackSetSecurity } from './securitycurrency-search-and-set-dialog.component';
import { CurrencypairService } from '../service/currencypair.service';
import { combineLatest, Observable } from 'rxjs';
import { Currencypair } from '../../entities/currencypair';
import { SecuritycurrencySearchTableBase } from './securitycurrency.search.table.base';
import { SupplementCriteria } from '../model/supplement.criteria';
import { TableModule } from '@openng/optimus-ui/table';
import { ButtonModule } from '@openng/optimus-ui/button';

/**
 * After a search over securities or currency the search result ist shown in a table to select a certain security.
 */
@Component({
  selector: 'securitycurrency-search-and-set-table',
  template: `
    <div class="col-md-12">
      <p-table
        [columns]="tableFields()"
        [value]="securitycurrencyList"
        selectionMode="single"
        [selection]="selectedSecurity()"
        (selectionChange)="selectedSecurity.set($event)"
        dataKey="idSecuritycurrency"
        [paginator]="true"
        [rows]="10"
        [rowsPerPageOptions]="[10, 20, 30]"
        sortField="name"
        sortMode="multiple"
        stripedRows
        showGridlines>
        <ng-template #header>
          <tr>
            @for (field of tableFields(); track field) {
              <th
                [pSortableColumn]="field.field"
                [style.max-width.px]="field.width"
                [style]="field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {}">
                {{ field.headerTranslated }}
                <p-sortIcon [field]="field.field" />
              </th>
            }
          </tr>
        </ng-template>

        <ng-template #body let-el>
          <tr [pSelectableRow]="el">
            @for (field of tableFields(); track field) {
              @if (field.visible) {
                <td
                  [style.max-width.px]="field.width"
                  [style]="field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {}"
                  [class]="
                    field.dataType === DataType.Numeric ||
                    field.dataType === DataType.DateTimeNumeric ||
                    field.dataType === DataType.NumericInteger
                      ? 'text-end'
                      : ''
                  ">
                  {{ getValueByPath(el, field) }}
                </td>
              }
            }
          </tr>
        </ng-template>
      </p-table>
    </div>

    <div class="ui-dialog-buttonpane ui-widget-content ui-helper-clearfix">
      <button pButton [disabled]="!selectedSecurity()" class="btn float-end" (click)="chooseSecurity()" type="button">
        {{ 'ASSIGN_SELECTED' | translate }}
      </button>
    </div>
  `,
  imports: [TableModule, ButtonModule, TranslateModule]
})
export class SecuritycurrencySearchAndSetTableComponent extends SecuritycurrencySearchTableBase {
  private securityService: SecurityService = inject(SecurityService);
  private currencypairService: CurrencypairService = inject(CurrencypairService);

  readonly callBackSetSecurity = input<CallBackSetSecurity>(undefined);
  readonly supplementCriteria = input<SupplementCriteria>(undefined);

  readonly selectedSecurity = signal<Security | null>(null);
  /** A new columns reference re-renders the header and body templates of the OnPush p-table after translation. */
  readonly tableFields = computed(() => {
    this.fieldsRevision();
    return [...this.fields];
  });
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    super();
  }

  loadData(securitycurrencySearch: SecuritycurrencySearch): void {
    securitycurrencySearch.excludeDerivedSecurity = this.supplementCriteria().excludeDerivedSecurity;
    const obs: Observable<(Currencypair | Security)[]>[] = [
      this.securityService.searchByCriteria(securitycurrencySearch)
    ];
    !this.supplementCriteria().onlySecurity &&
      obs.push(this.currencypairService.searchByCriteria(securitycurrencySearch));
    combineLatest(obs)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data: (Currencypair | Security)[][]) => {
        this.securitycurrencyList = data[0];
        this.createTranslatedValueStoreAndFilterField(data[0]);
        if (!this.supplementCriteria().onlySecurity) {
          this.transformCurrencypairToCurrencypairWatchlist(<Currencypair[]>data[1]);
        }
      });
  }

  clearList(): void {
    this.securitycurrencyList = [];
    this.selectedSecurity.set(null);
  }

  chooseSecurity(): void {
    const selected = this.selectedSecurity();
    if (selected) this.callBackSetSecurity().setSecurity(selected);
  }
}
