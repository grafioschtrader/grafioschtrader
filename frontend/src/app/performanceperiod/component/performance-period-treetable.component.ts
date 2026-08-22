import { Component, Input, OnChanges, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { TreeTableConfigBase } from '../../lib/datashowbase/tree.table.config.base';
import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import {
  HolidayMissing,
  PerformancePeriod,
  PeriodStep,
  PeriodStepMissingHoliday,
  PeriodWindowWithField
} from '../model/performance.period';
import { TreeNode } from '@openng/optimus-ui/api';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ColumnConfig, ColumnGroupConfig } from '../../lib/datashowbase/column.config';
import { WeekYear } from '../service/holding.service';
import { Helper } from '../../lib/helper/helper';
import moment from 'moment';
import { AppHelper } from '../../lib/helper/app.helper';
import { AppSettings } from '../../shared/app.settings';
import { CommonModule } from '@angular/common';
import { TreeTableModule } from '@openng/optimus-ui/treetable';
import { TooltipModule } from '@openng/optimus-ui/tooltip';

/**
 * Shows a tree table with periodic windows on the first column, which can be week or year.
 * The header shows gain for each day or each month.
 */
@Component({
  selector: 'performance-period-treetable',
  template: `
    <div class="fcontainer">
      <p-treeTable [value]="periodWindowsNodes" [columns]="fields" selectionMode="single" [(selection)]="selectedNodes">
        <ng-template #header let-fields>
          <tr>
            @for (field of fields; track field) {
              <th [ngClass]="getCellClass(field)" [style.width.px]="field.width" [style.min-width.px]="field.width">
                {{ field.headerTranslated }}
              </th>
            }
          </tr>
        </ng-template>
        <ng-template #body let-rowNode let-rowData="rowData" let-columns="fields">
          <tr>
            @for (field of fields; track field; let i = $index) {
              <td
                [ngClass]="[
                  getCellClass(field),
                  getHolidayMissing(rowData, field) === HolidayMissing[HolidayMissing.HM_HOLIDAY] ? 'cell-holiday' : '',
                  getHolidayMissing(rowData, field) === HolidayMissing[HolidayMissing.HM_HISTORY_DATA_MISSING]
                    ? 'cell-data-missing'
                    : ''
                ]"
                [style.width.px]="field.width"
                [style.min-width.px]="field.width">
                @if (i === 0) {
                  <p-treeTableToggler [rowNode]="rowNode"></p-treeTableToggler>
                }
                @switch (field.templateName) {
                  @case ('greenRed') {
                    <span
                      [pTooltip]="getValueByPath(rowData, field)"
                      [style.color]="isValueByPathMinusWithEmptyColor(rowData, field) ? 'red' : 'green'">
                      {{ getValueByPath(rowData, field) }}
                    </span>
                  }
                  @default {
                    <span [pTooltip]="getValueByPath(rowData, field)">{{ getValueByPath(rowData, field) }}</span>
                  }
                }
              </td>
            }
          </tr>
        </ng-template>
        <ng-template pTemplate="footer">
          <tr>
            @for (field of fields; track field) {
              @if (field.visible) {
                <td
                  class="row-total"
                  [ngClass]="getCellClass(field)"
                  [style.width.px]="field.width"
                  [style.min-width.px]="field.width">
                  @switch (field.templateName) {
                    @case ('greenRed') {
                      <span
                        [pTooltip]="
                          getValueColumnTotal(
                            field,
                            0,
                            $safeNavigationMigration(performancePeriod?.sumPeriodColSteps),
                            null
                          )
                        "
                        [style.color]="isFooterValueMinus(field) ? 'red' : 'green'">
                        {{
                          getValueColumnTotal(
                            field,
                            0,
                            $safeNavigationMigration(performancePeriod?.sumPeriodColSteps),
                            null
                          )
                        }}
                      </span>
                    }
                    @default {
                      <span>{{
                        getValueColumnTotal(
                          field,
                          0,
                          $safeNavigationMigration(performancePeriod?.sumPeriodColSteps),
                          null
                        )
                      }}</span>
                    }
                  }
                </td>
              }
            }
          </tr>
        </ng-template>
      </p-treeTable>
    </div>
  `,
  styles: [
    `
      .cell-holiday {
        background-color: greenyellow !important;
      }

      .cell-data-missing {
        background-color: orange !important;
      }

      /*
     * Optimus renders the tree table with "table-layout: fixed; width: 100%", which hands every column
     * without an explicit width the same share of the panel. The column totals of the footer are an order
     * of magnitude larger than the values of a single period, so they no longer fit and the global
     * "td {text-overflow: ellipsis}" rule silently clipped them. Auto layout lets the widest cell of a
     * column - usually its total - determine the column width, and the wrapper scrolls horizontally when
     * the whole table no longer fits instead of truncating single values.
     *
     * Auto layout has one consequence for the column widths: a "width" on a cell is only a preference.
     * The natural content of this table is far narrower than the panel, so the browser spreads the
     * surplus of "min-width: 100%" over all columns and overrides that preference. The template
     * therefore applies ColumnConfig.width as "min-width" as well, which auto layout does honour
     * exactly. A width below the min-content of the cell - about 113px for the toggler plus the nowrap
     * date range of a week - cannot be reached at all and is silently ignored.
     */
      :host ::ng-deep .p-treetable-wrapper {
        overflow-x: auto;
      }

      :host ::ng-deep .p-treetable-wrapper > table {
        table-layout: auto;
        width: auto;
        min-width: 100%;
      }

      /* .fcontainer is a flex container; without min-width the table cannot shrink and overflows the panel */
      .fcontainer > p-treetable {
        flex: 1 1 auto;
        min-width: 0;
      }

      /*
     * tabular-nums gives all digits the same advance width so the figures of a column line up.
     *
     * The cell padding of this table is deliberately not set here: Optimus applies it from the treetable
     * design tokens of MyPreset in app.component.ts, and a rule of this component would tie with the
     * theme rule on specificity and lose, because the theme sheet is injected after the component style.
     * Such a rule only appears to work after a hot reload, when the injection order happens to flip.
     */
      :host ::ng-deep .p-treetable-thead > tr > th,
      :host ::ng-deep .p-treetable-tbody > tr > td,
      :host ::ng-deep .p-treetable-tfoot > tr > td {
        font-variant-numeric: tabular-nums;
      }
    `
  ],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [CommonModule, TreeTableModule, TooltipModule]
})
export class TenantPerformanceTreetableComponent extends TreeTableConfigBase implements OnInit, OnChanges {
  @Input() performancePeriod: PerformancePeriod;

  readonly GAIN_MC = 'totalGainMC';
  readonly CASH_BALANCE_MC = 'cashBalanceMC';
  readonly TOTAL_SECURITIES_MC = 'securitiesMC';
  readonly MARGIN_CLOSE_GAIN_MC = 'marginCloseGainMC';

  HolidayMissing: typeof HolidayMissing = HolidayMissing;

  periodWindowsNodes: TreeNode[] = [];
  selectedNodes: TreeNode[] = [];
  translatedTexts: { [s: string]: string };
  lastPeriodSplit: WeekYear | string;

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.translateService
      .get(['CASH_BALANCE', AppSettings.SECURITY.toUpperCase(), 'MARGIN_CLOSE_GAIN'])
      .subscribe((translatedTexts) => (this.translatedTexts = translatedTexts));
    this.addColumnFeqH(DataType.String, 'period', true, false, {
      width: 120,
      fieldValueFN: this.getFirstColumnLabel.bind(this),
      columnGroupConfigs: [new ColumnGroupConfig(null, 'GRAND_TOTAL')]
    });
    this.addColumn(DataType.Numeric, 'periodWindow.gainPeriodMC', 'TOTAL', true, false, {
      fieldValueFN: this.getLastColumn.bind(this),
      templateName: 'greenRed',
      columnGroupConfigs: [new ColumnGroupConfig(null, null, this.getGrandTotal.bind(this))]
    });
    this.translateHeadersAndColumns();
  }

  ngOnChanges(): void {
    this.createPeriodTreeTableDefinition();
    this.createPeriodNodes();
  }

  getFirstColumnLabel(dataobject: PeriodWindowWithField, field: ColumnConfig, valueField: any): string | number {
    let colVal: string | number;

    switch (dataobject.showField) {
      case this.CASH_BALANCE_MC:
        colVal = this.translatedTexts['CASH_BALANCE'];
        break;

      case this.TOTAL_SECURITIES_MC:
        colVal = this.translatedTexts[AppSettings.SECURITY.toUpperCase()];
        break;
      case this.MARGIN_CLOSE_GAIN_MC:
        colVal = this.translatedTexts['MARGIN_CLOSE_GAIN'];
        break;

      default:
        if (this.performancePeriod.periodSplit === WeekYear[WeekYear.WM_WEEK]) {
          // Date range for week
          colVal =
            moment(dataobject.periodWindow.startDate).format(this.gps.getDateFormatWithoutYear()) +
            ' - ' +
            moment(dataobject.periodWindow.endDate).format(this.gps.getDateFormatWithoutYear());
        } else {
          // Only year
          colVal = moment(dataobject.periodWindow.startDate).year();
        }
    }
    return colVal;
  }

  getLastColumn(dataobject: PeriodWindowWithField, field: ColumnConfig, valueField: any): string | number {
    return dataobject.showField === this.GAIN_MC ? valueField : null;
  }

  getDataValue(dataobject: PeriodWindowWithField, field: ColumnConfig, valueField: any): string | number {
    const psmh: PeriodStepMissingHoliday = dataobject.periodWindow.periodStepList[+field.field];
    if (psmh.hasOwnProperty(dataobject.showField)) {
      return AppHelper.numberFormat(
        this.gps,
        (<PeriodStep>psmh)[dataobject.showField],
        field.maxFractionDigits,
        field.minFractionDigits
      );
    }
    return null;
  }

  /**
   * Returns the alignment class of a cell. Numeric and date-time values are right aligned, which applies to the
   * header, the data cells and the footer alike so that a column header sits directly above its figures.
   *
   * @param field Column configuration of the rendered cell
   * @returns 'text-end' for right aligned data types, otherwise an empty string
   */
  getCellClass(field: ColumnConfig): string {
    return field.dataType === DataType.Numeric ||
      field.dataType === DataType.NumericShowZero ||
      field.dataType === DataType.NumericInteger ||
      field.dataType === DataType.DateTimeNumeric
      ? 'text-end'
      : '';
  }

  /**
   * Delivers the grand total of the last column. The column totals of the footer come from sumPeriodColSteps, which
   * holds one entry per period step (5 for weeks, 12 for months) and therefore no total over all steps. That value is
   * the gain of the whole evaluated period and is taken from the difference of the first and the last day.
   *
   * @param columnConfig Column configuration of the total column, provides the numeric formatting
   * @param arrIndex Index of the column group configuration, not used here
   * @param data Total data object handed over by the footer, not used here
   * @param rowIndex Row index of the total row, not used here
   * @returns The formatted gain of the whole period or undefined when no data is loaded
   */
  getGrandTotal(columnConfig: ColumnConfig, arrIndex: number, data: any, rowIndex: number): string {
    return AppHelper.getValueByPathWithField(
      this.gps,
      this.translateService,
      this.performancePeriod,
      columnConfig,
      'difference.gainMC'
    );
  }

  /**
   * Determines the sign of a footer value for its red or green colouring. The last column shows the gain of the whole
   * period, all other columns a column total of sumPeriodColSteps.
   *
   * @param field Column configuration of the footer cell
   * @returns True when the displayed total is negative
   */
  isFooterValueMinus(field: ColumnConfig): boolean {
    return isNaN(<any>field.field)
      ? this.performancePeriod?.difference.gainMC < 0
      : this.isValueByPathMinus(this.performancePeriod?.sumPeriodColSteps, field);
  }

  isValueByPathMinusWithEmptyColor(dataobject: PeriodWindowWithField, field: ColumnConfig): boolean {
    if (isNaN(<any>field.field) && field.dataType === DataType.Numeric) {
      return super.isValueByPathMinus(dataobject, field);
    } else {
      const psmh: PeriodStepMissingHoliday = dataobject.periodWindow.periodStepList[+field.field];
      if (psmh.hasOwnProperty(dataobject.showField)) {
        return (<PeriodStep>psmh)[dataobject.showField] < 0;
      }
    }
    return false;
  }

  getHolidayMissing(dataobject: PeriodWindowWithField, field: ColumnConfig): HolidayMissing | string {
    if (isNaN(<any>field.field)) {
      return HolidayMissing.HM_OTHER_CELL;
    }
    return dataobject.periodWindow.periodStepList[+field.field].holidayMissing;
  }

  private createPeriodTreeTableDefinition(): void {
    if (this.performancePeriod) {
      if (this.performancePeriod.periodSplit !== this.lastPeriodSplit) {
        this.fields.length > 2 && this.spliceColumns(1, this.fields.length - 2);
        const calendarLang = Helper.CALENDAR_LANG[this.gps.getUserLang()];
        if (this.performancePeriod.periodSplit === WeekYear[WeekYear.WM_WEEK]) {
          // for week-day
          for (let i = 1; i < calendarLang.dayNamesShort.length - 1; i++) {
            const fieldName = '' + (i - 1);
            this.insertColumn(i, DataType.Numeric, fieldName, calendarLang.dayNamesShort[i], true, false, {
              fieldValueFN: this.getDataValue.bind(this),
              templateName: 'greenRed',
              headerTranslated: calendarLang.dayNamesShort[i]
            });
            this.fields[i].columnGroupConfigs = [new ColumnGroupConfig(fieldName)];
          }
        } else {
          // for year-month
          for (let i = 0; i < calendarLang.monthNamesShort.length; i++) {
            const fieldName = '' + i;
            this.insertColumn(i + 1, DataType.Numeric, fieldName, calendarLang.monthNamesShort[i], true, false, {
              fieldValueFN: this.getDataValue.bind(this),
              templateName: 'greenRed',
              headerTranslated: calendarLang.monthNamesShort[i]
            });
            this.fields[i + 1].columnGroupConfigs = [new ColumnGroupConfig(fieldName)];
          }
        }
        this.lastPeriodSplit = this.performancePeriod.periodSplit;
      }
    }
  }

  private createPeriodNodes(): void {
    const tn: TreeNode[] = [];
    if (this.performancePeriod) {
      this.performancePeriod.periodWindows.forEach((periodWindow) => {
        // The same data row is shown two times but different values
        const pwTreeNode: TreeNode = {
          data: new PeriodWindowWithField(this.GAIN_MC, periodWindow),
          children: [],
          expanded: false,
          leaf: false
        };
        pwTreeNode.children = [
          {
            data: new PeriodWindowWithField(this.CASH_BALANCE_MC, periodWindow),
            children: [],
            expanded: false,
            leaf: true
          },
          {
            data: new PeriodWindowWithField(this.TOTAL_SECURITIES_MC, periodWindow),
            children: [],
            expanded: false,
            leaf: true
          },
          {
            data: new PeriodWindowWithField(this.MARGIN_CLOSE_GAIN_MC, periodWindow),
            children: [],
            expanded: false,
            leaf: true
          }
        ];
        tn.push(pwTreeNode);
      });
    }
    this.periodWindowsNodes = tn;
  }
}
