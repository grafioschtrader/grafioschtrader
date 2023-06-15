import {Component, Input} from '@angular/core';
import {Security} from '../../entities/security';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {TranslateService} from '@ngx-translate/core';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {SecurityService} from '../service/security.service';
import {CallBackSetSecurity} from './securitycurrency-search-and-set.component';
import {CurrencypairService} from '../service/currencypair.service';
import {combineLatest, Observable} from 'rxjs';
import {Currencypair} from '../../entities/currencypair';
import {SecuritycurrencySearchTableBase} from './securitycurrency.search.table.base';
import {SupplementCriteria} from '../model/supplement.criteria';
import {FilterService} from 'primeng/api';

/**
 * After a search over securities or currency the search result ist shown in a table to select a certain security.
 */
@Component({
  selector: 'securitycurrency-search-and-set-table',
  template: `
    <div class="col-md-12">

      <p-table [columns]="fields" [value]="securitycurrencyList" selectionMode="single" [(selection)]="selectedSecurity"
               dataKey="idSecuritycurrency" [paginator]="true" [rows]="10" [rowsPerPageOptions]="[10,20,30]"
               sortField="name" sortMode="multiple" responsiveLayout="scroll"
               styleClass="sticky-table p-datatable-striped p-datatable-gridlines">
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th *ngFor="let field of fields" [pSortableColumn]="field.field" [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
              {{field.headerTranslated}}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <ng-container *ngFor="let field of fields">
              <td *ngIf="field.visible" [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                  [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-right': ''">
                {{getValueByPath(el, field)}}
              </td>
            </ng-container>
          </tr>
        </ng-template>
      </p-table>
    </div>

    <div class="ui-dialog-buttonpane ui-widget-content ui-helper-clearfix">
      <button pButton [disabled]="!selectedSecurity"
              class="btn pull-right" [label]="'ASSIGN_SELECTED' | translate"
              (click)="chooseSecurity()" type="button">
      </button>
    </div>
  `
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

    combineLatest(obs).subscribe(data => {
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


