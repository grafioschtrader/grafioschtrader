import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { computed, DestroyRef, Directive, inject, Injector, Input, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { Security } from '../../entities/security';
import { Currencypair } from '../../entities/currencypair';
import { TranslateService } from '@ngx-translate/core';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { SecuritycurrencyLists } from '../../entities/view/securitycurrency.lists';
import { CurrencypairWatchlist } from '../../entities/view/currencypair.watchlist';
import { DataChangedService } from '../../lib/maintree/service/data.changed.service';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { TenantLimit } from '../../shared/types/tenant.limit';
import { SecuritycurrencySearchTableBase } from '../../securitycurrency/component/securitycurrency.search.table.base';
import { FilterService } from '@openng/optimus-ui/api';
import { Observable } from 'rxjs';

/**
 * Abstract directive providing base functionality for adding instruments to various entity lists.
 * Handles search, selection, and addition of securities and currency pairs with tenant limit validation.
 */
@Directive()
export abstract class AddInstrumentTable<T> extends SecuritycurrencySearchTableBase {
  /** Tenant limits for controlling maximum number of instruments that can be added */
  @Input() tenantLimits: TenantLimit[];

  /** Columns as a new reference after every change, so the header and body templates of the OnPush p-table follow. */
  readonly tableFields = computed(() => {
    this.fieldsRevision();
    return [...this.fields];
  });

  private readonly selectedSecuritycurrenciesSignal = signal<(Security | Currencypair)[]>([]);
  /** Search of the current criteria; a newer search replaces one that is still pending. */
  private searchSubscription: Subscription;
  protected readonly destroyRef = inject(DestroyRef);

  /** ID of the target entity to add instruments to */
  id: number;

  /** Current search criteria for finding instruments */
  securitycurrencySearch: SecuritycurrencySearch;

  /**
   * Creates a new AddInstrumentTable instance.
   * @param instance - The target entity instance to add instruments to
   * @param dataChangedService - Service for notifying about data changes
   * @param addSearchToListService - Service for searching and adding instruments to lists
   * @param filterService - Optimus filter service for table filtering
   * @param translateService - Angular translation service for internationalization
   * @param gps - Global parameter service for application settings
   * @param usersettingsService - Service for user-specific settings and preferences
   */
  protected constructor(
    private instance: T,
    private dataChangedService: DataChangedService,
    private addSearchToListService: AddSearchToListService<T>,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.multiSortMeta.push({ field: 'name', order: 1 });
    inject(DestroyRef).onDestroy(() => this.searchSubscription?.unsubscribe());
  }

  /** Currently selected securities and currency pairs for addition; replaced, never changed in place. */
  get selectedSecuritycurrencies(): (Security | Currencypair)[] {
    return this.selectedSecuritycurrenciesSignal();
  }

  set selectedSecuritycurrencies(selected: (Security | Currencypair)[]) {
    this.selectedSecuritycurrenciesSignal.set(selected ?? []);
  }

  /** Clears the search results and selected instruments lists */
  clearList(): void {
    this.searchSubscription?.unsubscribe();
    this.securitycurrencyList = [];
    this.selectedSecuritycurrencies = [];
  }

  /**
   * Loads instrument data based on search criteria and populates the display table.
   * @param id - ID of the target entity to search instruments for
   * @param securitycurrencySearch - Search criteria for finding instruments
   */
  loadData(id: number, securitycurrencySearch: SecuritycurrencySearch): void {
    this.id = id;
    this.securitycurrencySearch = securitycurrencySearch;
    this.searchSubscription?.unsubscribe();
    this.searchSubscription = this.addSearchToListService
      .searchByCriteria(id, securitycurrencySearch)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((securitycurrencyLists: SecuritycurrencyLists) => {
        this.createTranslatedValueStoreAndFilterField(securitycurrencyLists.securityList);
        this.securitycurrencyList = securitycurrencyLists.securityList;
        this.transformCurrencypairToCurrencypairWatchlist(securitycurrencyLists.currencypairList);
      });
  }

  /** Adds the selected instruments to the target entity and refreshes the data display */
  onClickAdd(): void {
    const securitycurrencyLists = new SecuritycurrencyLists();
    this.selectedSecuritycurrencies.forEach((securitycurrency) => {
      if (securitycurrency instanceof CurrencypairWatchlist) {
        securitycurrencyLists.currencypairList.push(Object.assign(new Currencypair(null, null), securitycurrency));
      } else {
        securitycurrencyLists.securityList.push(Object.assign(new Security(), securitycurrency));
      }
    });
    this.addSearchToListService
      .addSecuritycurrenciesToList(this.id, securitycurrencyLists)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data: T) => {
        this.clearList();
        this.loadData(this.id, this.securitycurrencySearch);
        this.dataChangedService.dataHasChanged(
          new ProcessedActionData(ProcessedAction.UPDATED, this.instance ? this.instance : data)
        );
      });
  }

  /**
   * Checks if adding the currently selected instruments would exceed any tenant limits.
   * @returns True if tenant limits would be exceeded, false otherwise
   */
  reachedListLimits(): boolean {
    for (const tenantLimit of this.tenantLimits) {
      if (tenantLimit.actual + this.selectedSecuritycurrencies.length > tenantLimit.limit) {
        return true;
      }
    }
    return false;
  }
}

/**
 * Service interface for searching and adding instruments to entity lists.
 * Provides methods for finding available instruments and adding them to target entities.
 */
export interface AddSearchToListService<T> {
  /**
   * Searches for instruments based on the provided criteria.
   * @param id - ID of the target entity to search for
   * @param securitycurrencySearch - Search criteria for finding instruments
   * @returns Observable containing lists of matching securities and currency pairs
   */
  searchByCriteria(id: number, securitycurrencySearch: SecuritycurrencySearch): Observable<SecuritycurrencyLists>;

  /**
   * Adds the specified instruments to the target entity's list.
   * @param id - ID of the target entity to add instruments to
   * @param securitycurrencyLists - Lists of securities and currency pairs to add
   * @returns Observable containing the updated target entity
   */
  addSecuritycurrenciesToList(id: number, securitycurrencyLists: SecuritycurrencyLists): Observable<T>;
}
