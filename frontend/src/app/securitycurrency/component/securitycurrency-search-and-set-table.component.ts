import {Component, Input} from '@angular/core';
import {Security} from '../../entities/security';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {SecurityService} from '../service/security.service';
import {CallBackSetSecurity} from './securitycurrency-search-and-set.component';
import {CurrencypairService} from '../service/currencypair.service';
import {combineLatest, Observable} from 'rxjs';
import {Currencypair} from '../../entities/currencypair';
import {SecuritycurrencySearchTableBase} from './securitycurrency.search.table.base';
import {SupplementCriteria} from '../model/supplement.criteria';
import {FilterService} from 'primeng/api';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';
import {CommonModule} from '@angular/common';

/**
 * After a search over securities or currency the search result ist shown in a table to select a certain security.
 */
@Component({
    selector: 'securitycurrency-search-and-set-table',
  template: `
    <div class="col-md-12">
      <p-table [columns]="fields" [value]="securitycurrencyList" selectionMode="single" [(selection)]="selectedSecurity"
               dataKey="idSecuritycurrency" [paginator]="true" [rows]="10" [rowsPerPageOptions]="[10,20,30]"
               sortField="name" sortMode="multiple"  stripedRows showGridlines>
        <ng-template #header let-fields>
          <tr>
            @for (field of fields; track field) {
              <th [pSortableColumn]="field.field" [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                {{field.headerTranslated}}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            }
          </tr>
        </ng-template>

        <ng-template #body let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            @for (field of fields; track field) {
              @if (field.visible) {
                <td [style.max-width.px]="field.width"
                    [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                    [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-end': ''">
                  {{getValueByPath(el, field)}}
                </td>
              }
            }
          </tr>
        </ng-template>
      </p-table>
    </div>

    <div class="ui-dialog-buttonpane ui-widget-content ui-helper-clearfix">
      <button pButton [disabled]="!selectedSecurity"
              class="btn float-end"
              (click)="chooseSecurity()" type="button">
        {{'ASSIGN_SELECTED' | translate}}
      </button>
    </div>
  `,
    imports: [
        TableModule,
        ButtonModule,
        CommonModule,
        TranslateModule
    ],
    standalone: true
})
export class SecuritycurrencySearchAndSetTableComponent extends SecuritycurrencySearchTableBase {
  @Input() callBackSetSecurity: CallBackSetSecurity;
  @Input() supplementCriteria: SupplementCriteria;

  selectedSecurity: Security;

  constructor(private securityService: SecurityService,
              private currencypairService: CurrencypairService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);
  }

  loadData(securitycurrencySearch: SecuritycurrencySearch): void {
    securitycurrencySearch.excludeDerivedSecurity = this.supplementCriteria.excludeDerivedSecurity;
    const obs: Observable<(Currencypair | Security)[]>[] = [this.securityService.searchByCriteria(securitycurrencySearch)];
    !this.supplementCriteria.onlySecurity && obs.push(this.currencypairService.searchByCriteria(securitycurrencySearch));
    combineLatest(obs).subscribe((data: ((Currencypair | Security)[])[]) => {
      this.securitycurrencyList = data[0];
      this.createTranslatedValueStoreAndFilterField(data[0]);
      if (!this.supplementCriteria.onlySecurity) {
        this.transformCurrencypairToCurrencypairWatchlist(<Currencypair[]>data[1]);
      }
    });
  }

  clearList(): void {
    this.securitycurrencyList = [];
  }

  chooseSecurity(): void {
    this.callBackSetSecurity.setSecurity(this.selectedSecurity);
  }
}


