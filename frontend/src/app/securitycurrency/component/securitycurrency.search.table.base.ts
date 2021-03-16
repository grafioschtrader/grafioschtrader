import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {ChangeDetectorRef} from '@angular/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {Currencypair} from '../../entities/currencypair';
import {Security} from '../../entities/security';
import {FilterService} from 'primeng/api';

export abstract class SecuritycurrencySearchTableBase extends TableConfigBase {

  securitycurrencyList: (Security | Currencypair)[] = [];

  constructor(changeDetectionStrategy: ChangeDetectorRef,
              filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              globalparameterService: GlobalparameterService) {
    super(changeDetectionStrategy, filterService, usersettingsService, translateService, globalparameterService);

    this.addColumn(DataType.String, 'name', 'NAME', true, false, {width: 250});
    this.addColumn(DataType.String, 'isin', 'ISIN', true, false);
    this.addColumn(DataType.String, 'tickerSymbol', 'TICKER_SYMBOL', true, false);
    this.addColumn(DataType.String, 'assetClass.categoryType', 'ASSETCLASS', true, true, {translateValues: true, width: 60});
    this.addColumn(DataType.String, 'currency', 'CURRENCY', true, false);

    this.prepareTableAndTranslate();
  }

  transformCurrencypairToCurrencypairWatchlist(currencypairs: Currencypair[]): void {
    currencypairs.forEach(currencypair => {
      const currencypairWatchlist: CurrencypairWatchlist = new CurrencypairWatchlist(currencypair.fromCurrency,
        currencypair.toCurrency);
      Object.assign(currencypairWatchlist, currencypair);
      this.securitycurrencyList.push(currencypairWatchlist);
    });
  }
}
