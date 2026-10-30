import { DestroyRef, inject, signal } from '@angular/core';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { SvgIconRegistryService } from 'angular-svg-icon';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { DividendSplit } from '../../entities/dividend.split';
import { DividendSplitSvgCreator } from '../../shared/dividendsplit/dividend.split.svg.creator';

/**
 * Abstract base class for displaying dividend and split data in table format with SVG icon support.
 * Provides common functionality for dividend and security split table components including
 * icon registration, sorting configuration, and create type visualization.
 */
export abstract class DividendSplitTableBase<S extends DividendSplit> extends TableConfigBase {
  private iconReg: SvgIconRegistryService = inject(SvgIconRegistryService);

  protected readonly destroyRef = inject(DestroyRef);

  /** Array of dividend or split entities to display in the table */
  readonly data = signal<S[] | undefined>(undefined);

  /**
   * Creates a new dividend/split table base with sorting and icon configuration.
   *
   * @param keyfield Primary key field name for table row identification
   * @param sortField Default field name for initial table sorting
   * @param groupTitle Translation key for the table group title
   */
  protected constructor(
    public keyfield: string,
    sortField: string,
    public groupTitle: string
  ) {
    super();
    this.multiSortMeta.push({ field: sortField, order: -1 });
    DividendSplitSvgCreator.registerIcons(this.iconReg);
  }

  /**
   * Retrieves the appropriate SVG icon name for a dividend or split entity's create type.
   *
   * @param entity The dividend or split entity containing create type information
   * @param field Column configuration object (unused but required for interface compatibility)
   * @returns SVG icon name corresponding to the entity's create type
   */
  getCreateTypeIcon(entity: S, field: ColumnConfig): string | undefined {
    return entity.createType == null ? undefined : DividendSplitSvgCreator.createTypeIconMap[entity.createType];
  }
}
