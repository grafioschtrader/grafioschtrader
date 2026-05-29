import {Directive} from '@angular/core';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {BaseID} from '../../lib/entities/base.id';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {FilterType} from '../../lib/datashowbase/filter.type';
import {BaseSettings} from '../../lib/base.settings';
import {HistoryquoteCreateType} from '../../entities/historyquote';

/**
 * Shared base for the live {@code HistoryquoteTableComponent} and the archive
 * {@code HistoryquoteLegacyComponent}. Exposes the column definitions both tables show, plus
 * the default date-descending sort and the create_type icon plumbing. Subclasses still own
 * constructor and dispatch flow, but they call into these helpers instead of re-declaring the
 * same columns.
 *
 * Typical usage in a subclass constructor:
 * <pre>
 *   super(...);
 *   HistoryquoteTableBase.registerCreateTypeIcons(iconReg);
 *   this.addDateColumn();
 *   this.addCreateTypeColumn();              // optional, both views show it
 *   this.addColumn(... extra column ...);    // e.g. transferDate on the legacy view
 *   this.addOhlcvColumns();
 *   this.applyDefaultDateSort();
 *   this.prepareTableAndTranslate();
 * </pre>
 */
@Directive()
export abstract class HistoryquoteTableBase<T extends BaseID> extends TableCrudSupportMenu<T> {

  /**
   * Maps each HistoryquoteCreateType byte value to an SVG icon name. Both the live view and
   * the legacy view render this column the same way — same icons, same lookup.
   */
  protected static readonly createTypeIconMap: { [key: number]: string } = {
    [HistoryquoteCreateType.CONNECTOR_CREATED]: 'connector',
    [HistoryquoteCreateType.MANUAL_IMPORTED]: 'import',
    [HistoryquoteCreateType.FILLED_CLOSED_LINEAR_TRADING_DAY]: 'fill_linear',
    [HistoryquoteCreateType.CALCULATED]: 'calculation',
    [HistoryquoteCreateType.ADD_MODIFIED_USER]: 'edit',
    [HistoryquoteCreateType.FILL_GAP_BY_CONNECTOR]: 'gap_fill'
  };
  private static iconLoadDone = false;

  /**
   * Registers the create_type SVG icons exactly once per browser session. Safe to call from
   * any number of subclasses' constructors — the static guard ensures the underlying
   * iconReg.loadSvg calls run only on the first invocation.
   */
  protected static registerCreateTypeIcons(iconReg: SvgIconRegistryService): void {
    if (HistoryquoteTableBase.iconLoadDone) {
      return;
    }
    for (const iconName of Object.values(HistoryquoteTableBase.createTypeIconMap)) {
      iconReg.loadSvg(BaseSettings.PATH_ASSET_ICONS + iconName + BaseSettings.SVG, iconName);
    }
    HistoryquoteTableBase.iconLoadDone = true;
  }

  /** Adds the date column with the standard like-filter and export configuration. */
  protected addDateColumn(): void {
    this.addColumnFeqH(DataType.DateString, 'date', true, false,
      {filterType: FilterType.likeDataType, export: true});
  }

  /**
   * Adds the icon-rendered create_type column. The row's createType byte is mapped through
   * {@link createTypeIconMap} to an SVG icon name. Width matches the live view's column.
   */
  protected addCreateTypeColumn(): void {
    this.addColumn(DataType.NumericInteger, 'createType', 'T', true, true,
      {fieldValueFN: this.getCreateTypeIcon.bind(this), templateName: 'icon', width: 20});
  }

  /**
   * fieldValueFN callback for the create_type column. The live Historyquote entity carries
   * createType as the enum NAME (Jackson default), while the IShadowRow projection delivers
   * it as a numeric Byte — both shapes are normalized to a numeric key for the map lookup.
   * Returns undefined for null createType (e.g., legacy rows archived before the schema
   * gained the column).
   */
  protected getCreateTypeIcon(row: { createType?: number | string }, _column: ColumnConfig): string | undefined {
    if (row.createType == null) {
      return undefined;
    }
    const numericKey = typeof row.createType === 'string'
      ? HistoryquoteCreateType[row.createType as keyof typeof HistoryquoteCreateType]
      : row.createType;
    return HistoryquoteTableBase.createTypeIconMap[numericKey];
  }

  /**
   * Adds the volume + open + high + low + close columns with the locale-aware fraction-digit
   * settings used across the application's historyquote views.
   */
  protected addOhlcvColumns(): void {
    this.addColumnFeqH(DataType.Numeric, 'volume', true, false, {export: true});
    this.addColumnFeqH(DataType.Numeric, 'open', true, false, {
      minFractionDigits: 5,
      maxFractionDigits: this.gps.getMaxFractionDigits(),
      export: true
    });
    this.addColumnFeqH(DataType.Numeric, 'high', true, false,
      {maxFractionDigits: this.gps.getMaxFractionDigits(), export: true});
    this.addColumnFeqH(DataType.Numeric, 'low', true, false,
      {maxFractionDigits: this.gps.getMaxFractionDigits(), export: true});
    this.addColumnFeqH(DataType.Numeric, 'close', true, false, {
      minFractionDigits: 5,
      maxFractionDigits: this.gps.getMaxFractionDigits(),
      filterType: FilterType.likeDataType, export: true
    });
  }

  /** Newest row first — matches what the live view shipped with before this refactor. */
  protected applyDefaultDateSort(): void {
    this.multiSortMeta.push({field: 'date', order: -1});
  }
}
