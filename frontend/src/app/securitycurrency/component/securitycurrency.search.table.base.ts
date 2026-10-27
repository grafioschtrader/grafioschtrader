import { Injector, signal } from '@angular/core';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { CurrencypairWatchlist } from '../../entities/view/currencypair.watchlist';
import { Currencypair } from '../../entities/currencypair';
import { Security } from '../../entities/security';
import { FilterService } from '@openng/optimus-ui/api';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { AppSettings } from '../../shared/app.settings';

/**
 * Base class to show the search result as result in a table
 */
export abstract class SecuritycurrencySearchTableBase extends TableConfigBase {
  private readonly securitycurrencyListSignal = signal<(Security | Currencypair)[]>([]);

  protected constructor(
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);

    this.addFieldDefinition();
    this.prepareTableAndTranslate();
  }

  /**
   * Search result shown in the table. Backed by a signal, so assigning a new list re-renders OnPush subclasses as well;
   * always assign a new array instead of changing the current one.
   */
  get securitycurrencyList(): (Security | Currencypair)[] {
    return this.securitycurrencyListSignal();
  }

  set securitycurrencyList(securitycurrencyList: (Security | Currencypair)[]) {
    this.securitycurrencyListSignal.set(securitycurrencyList);
  }

  protected addFieldDefinition(): void {
    this.addColumnFeqH(DataType.String, 'name', true, false, { width: 250 });
    this.addColumnFeqH(DataType.String, 'isin', true, false);
    this.addColumnFeqH(DataType.String, 'tickerSymbol', true, false);
    this.addColumn(DataType.String, 'assetClass.categoryType', AppSettings.ASSETCLASS.toUpperCase(), true, true, {
      translateValues: TranslateValue.NORMAL,
      width: 60
    });
    this.addColumnFeqH(DataType.String, 'currency', true, false);
  }

  /** Appends the currency pairs, converted to watchlist rows, to the search result as a new list. */
  transformCurrencypairToCurrencypairWatchlist(currencypairs: Currencypair[]): void {
    const currencypairWatchlists = currencypairs.map((currencypair) =>
      Object.assign(new CurrencypairWatchlist(currencypair.fromCurrency, currencypair.toCurrency), currencypair)
    );
    this.securitycurrencyList = [...this.securitycurrencyList, ...currencypairWatchlists];
  }
}
