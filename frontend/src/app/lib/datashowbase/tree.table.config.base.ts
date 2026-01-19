import {TranslateService} from '@ngx-translate/core';
import {FilterService, TreeNode} from 'primeng/api';
import {GlobalparameterService} from '../services/globalparameter.service';
import {TableTreetableTotalBase} from './table.treetable.total.base';
import {TranslateHelper} from '../helper/translate.helper';
import {BaseSettings} from '../base.settings';

/**
 * Abstract base class for configuring PrimeNG TreeTable components with hierarchical data display.
 * Extends TableTreetableTotalBase to provide tree-specific functionality including breadth-first
 * tree traversal for translation processing and field configuration setup.
 *
 * This class is designed for displaying hierarchical data structures where parent-child
 * relationships need to be maintained and all levels of the tree require proper translation
 * support for sorting and filtering operations.
 */
export abstract class TreeTableConfigBase extends TableTreetableTotalBase {

  /**
   * Creates a new tree table configuration base.
   *
   * @param translateService - Angular translation service for internationalization
   * @param gps - Global parameter service for locale and formatting settings
   * @param filterService - Optional PrimeNG service for registering custom filters
   */
  protected constructor(translateService: TranslateService, gps: GlobalparameterService, filterService?: FilterService) {
    super(translateService, gps, null, filterService);
  }

  /**
   * Initializes tree table configuration including filtering and translations.
   * Must be called after column definitions are complete to set up proper filtering
   * and header translation functionality.
   */
  prepareTreeTableAndTranslate(): void {
    this.initializeFilters();
    this.translateHeadersAndColumns();
  }

  /**
   * Creates translated value stores for all nodes in a tree structure using breadth-first traversal.
   *
   * This method processes the entire tree hierarchy to ensure that all translatable field values
   * are properly cached for display and sorting operations. It uses a breadth-first algorithm
   * to visit every node in the tree and applies translation processing to each node's data.
   *
   * **Algorithm Details:**
   * - Uses a queue-based breadth-first traversal to process all tree levels
   * - Processes all nodes at each level before moving to child levels
   * - Safely handles trees with varying depths and branch structures
   * - Automatically sets up fieldTranslated properties for PrimeNG sorting
   *
   * **Translation Processing:**
   * - Only processes columns marked with translateValues property
   * - Creates translated value maps for consistent display across the tree
   * - Adds '$' suffix fields for PrimeNG TreeTable sorting support
   * - Handles null/undefined tree structures gracefully
   *
   * @param root - Array of root TreeNode objects representing the tree structure.
   *               Can be null/undefined for empty trees.
   *
   * @example
   * ```typescript
   * // Tree structure:
   * // Root
   * // ├── Category A (status: "ACTIVE")
   * // │   ├── Subcategory A1 (status: "INACTIVE")
   * // │   └── Subcategory A2 (status: "PENDING")
   * // └── Category B (status: "ACTIVE")
   *
   * this.createTranslateValuesStoreForTranslation(treeData);
   *
   * // Result: All nodes get translated values:
   * // status: "ACTIVE" → status$: "Active"
   * // status: "INACTIVE" → status$: "Inactive"
   * // status: "PENDING" → status$: "Pending"
   * ```
   */
  createTranslateValuesStoreForTranslation(root: TreeNode[]): void {
    const columnConfigs = this.fields.filter(columnConfig => !!columnConfig.translateValues);
    if (root === null) {
      return;
    }
    const q: TreeNode[] = [].concat(root);
    while (q.length !== 0) {
      let n = q.length;
      while (n > 0) {
        const p: TreeNode = q[0];
        q.shift();
        TranslateHelper.createTranslatedValueStoreForTranslation(this.translateService, columnConfigs, p.data);
        if (p.children) {
          q.push(...p.children);
        }
        n--;
      }
    }
    columnConfigs.forEach(columnConfig => columnConfig.fieldTranslated = columnConfig.field + BaseSettings.FIELD_SUFFIX);
  }
}
