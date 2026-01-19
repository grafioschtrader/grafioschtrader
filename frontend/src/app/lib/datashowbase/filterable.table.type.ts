import {Table} from 'primeng/table';
import {TreeTable} from 'primeng/treetable';

/**
 * Union type for PrimeNG Table and TreeTable components.
 * Both components share the same `filter(value, field, matchMode)` API,
 * allowing filter functionality to be shared between table and tree table components.
 */
export type FilterableTable = Table | TreeTable;
