import { signal } from '@angular/core';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { CurrencypairWatchlist } from '../../entities/view/currencypair.watchlist';
import { Currencypair } from '../../entities/currencypair';
import { Security } from '../../entities/security';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { AppSettings } from '../../shared/app.settings';
import {
  AdvancedSearchCriterion,
  AdvancedSearchField,
  AdvancedSearchValue,
  AdvancedSearchValueType
} from '../../entities/search/advanced.search';

/**
 * Base class to show the search result as result in a table
 */
/** Property of a result row that carries the values that satisfied the advanced criteria, by field key. */
const ADVANCED_ROW_PROPERTY = 'advanced';

/** A result row to which the matched values of the advanced criteria are attached for the columns to read. */
export type AdvancedResultRow = (Security | Currencypair) & {
  advanced?: Record<string, AdvancedSearchValue>;
};

/** How the value of each type of field is formatted in a column. */
const DATA_TYPE_OF_VALUE: Record<AdvancedSearchValueType, DataType> = {
  NUMBER: DataType.Numeric,
  DATE: DataType.DateString,
  TEXT: DataType.String,
  BOOLEAN: DataType.Boolean
};

export abstract class SecuritycurrencySearchTableBase extends TableConfigBase {
  private readonly securitycurrencyListSignal = signal<(Security | Currencypair)[]>([]);
  /** Paths of the columns of the last advanced search, to replace them by those of the next one. */
  private advancedColumnFields: string[] = [];

  constructor() {
    super();

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

  /**
   * Replaces the columns of the former advanced search by one column for each criterion of the new one. A column shows
   * the value that satisfied its criterion, formatted by its data type, and sorts like every other column. A search
   * without criteria leaves only the fixed columns.
   *
   * @param criteria - The criteria of the search that produced the list
   * @param fields - The fields the user can choose, which give each column its label and data type
   */
  protected setAdvancedColumns(criteria: AdvancedSearchCriterion[], fields: AdvancedSearchField[]): void {
    this.fields = this.fields.filter((column) => !this.advancedColumnFields.includes(column.field));
    this.advancedColumnFields = [];
    for (const criterion of criteria) {
      const field = fields.find((candidate) => candidate.key === criterion.field);
      if (field) {
        const path = `${ADVANCED_ROW_PROPERTY}.${field.key}`;
        this.addColumn(DATA_TYPE_OF_VALUE[field.valueType], path, null, true, false, { headerTranslated: field.label });
        this.advancedColumnFields.push(path);
      }
    }
    this.notifyViewChanged();
  }

  /**
   * Attaches to each row the values that satisfied the criteria, which the advanced columns read.
   *
   * @param securities - The securities of the response
   * @param values - The matched values by security id and field key; absent without advanced criteria
   */
  protected attachAdvancedValues(
    securities: Security[],
    values: Record<number, Record<string, AdvancedSearchValue>> | undefined
  ): void {
    for (const security of securities as AdvancedResultRow[]) {
      const idSecuritycurrency = security.idSecuritycurrency;
      security.advanced = idSecuritycurrency == null ? undefined : values?.[idSecuritycurrency];
    }
  }

  /** Appends the currency pairs, converted to watchlist rows, to the search result as a new list. */
  transformCurrencypairToCurrencypairWatchlist(currencypairs: Currencypair[]): void {
    const currencypairWatchlists = currencypairs.map((currencypair) =>
      Object.assign(new CurrencypairWatchlist(currencypair.fromCurrency, currencypair.toCurrency), currencypair)
    );
    this.securitycurrencyList = [...this.securitycurrencyList, ...currencypairWatchlists];
  }
}
