/**
 * The common filter API of Optimus Table and TreeTable. Both implementations clear a filter for a null value,
 * although the TreeTable declaration currently excludes that sentinel.
 */
export interface FilterableTable {
  filter(value: string | string[] | null, field: string, matchMode: string | null): void;
}
