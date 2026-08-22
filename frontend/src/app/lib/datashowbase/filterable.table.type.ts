import { Table } from '@openng/optimus-ui/table';
import { TreeTable } from '@openng/optimus-ui/treetable';

/**
 * Union type for Optimus Table and TreeTable components.
 * Both components share the same `filter(value, field, matchMode)` API,
 * allowing filter functionality to be shared between table and tree table components.
 */
export type FilterableTable = Table | TreeTable;
