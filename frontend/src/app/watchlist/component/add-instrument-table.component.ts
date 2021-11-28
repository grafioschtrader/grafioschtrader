import {Directive, Input} from '@angular/core';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {SecuritycurrencyLists} from '../../entities/view/securitycurrency.lists';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {ProcessedAction} from '../../shared/types/processed.action';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {SecuritycurrencySearchTableBase} from '../../securitycurrency/component/securitycurrency.search.table.base';
import {FilterService} from 'primeng/api';
import {Observable} from 'rxjs';

@Directive()
export abstract class AddInstrumentTable<T> extends SecuritycurrencySearchTableBase {

  @Input() tenantLimits: TenantLimit[];

  selectedSecuritycurrencies: (Security | Currencypair)[] = [];
  id: number;
  securitycurrencySearch: SecuritycurrencySearch;

  constructor(private instance: T,
              private dataChangedService: DataChangedService,
              private addSearchToListService: AddSearchToListService<T>,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);
    this.multiSortMeta.push({field: 'name', order: 1});
  }

  clearList(): void {
    this.securitycurrencyList = [];
    this.selectedSecuritycurrencies = [];
  }

  loadData(id: number, securitycurrencySearch: SecuritycurrencySearch): void {
    this.id = id;
    this.securitycurrencySearch = securitycurrencySearch;

    this.addSearchToListService.searchByCriteria(id, securitycurrencySearch)
      .subscribe((securitycurrencyLists: SecuritycurrencyLists) => {
        this.createTranslatedValueStoreAndFilterField(securitycurrencyLists.securityList);
        this.securitycurrencyList = securitycurrencyLists.securityList;
        this.transformCurrencypairToCurrencypairWatchlist(securitycurrencyLists.currencypairList);
      });
  }

  onClickAdd(): void {
    const securitycurrencyLists = new SecuritycurrencyLists();
    this.selectedSecuritycurrencies.forEach(securitycurrency => {
      if (securitycurrency instanceof CurrencypairWatchlist) {
        securitycurrencyLists.currencypairList.push(Object.assign(new Currencypair(null, null), securitycurrency));
      } else {
        securitycurrencyLists.securityList.push(Object.assign(new Security(), securitycurrency));
      }
    });
    this.addSearchToListService.addSecuritycurrenciesToList(this.id, securitycurrencyLists).subscribe((data: T) => {
      this.clearList();
      this.loadData(this.id, this.securitycurrencySearch);
      this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.UPDATED, this.instance ? this.instance : data));
    });
  }

  reachedListLimits(): boolean {
    for (const tenantLimit of this.tenantLimits) {
      if (tenantLimit.actual + this.selectedSecuritycurrencies.length > tenantLimit.limit) {
        return true;
      }
    }
    return false;
  }

}

export interface AddSearchToListService<T> {
  searchByCriteria(id: number, securitycurrencySearch: SecuritycurrencySearch): Observable<SecuritycurrencyLists>;

  addSecuritycurrenciesToList(id: number, securitycurrencyLists: SecuritycurrencyLists): Observable<T>;
}
