import {Component, Input, OnChanges, OnInit} from '@angular/core';
import {TreeTableConfigBase} from '../../datashowbase/tree.table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {
  HolidayMissing,
  PerformancePeriod,
  PeriodStep,
  PeriodStepMissingHoliday,
  PeriodWindowWithField
} from '../model/performance.period';
import {TreeNode} from 'primeng/api';
import {DataType} from '../../../dynamic-form/models/data.type';
import {ColumnConfig, ColumnGroupConfig} from '../../datashowbase/column.config';
import {WeekYear} from '../service/holding.service';
import {Helper} from '../../../helper/helper';
import * as moment from 'moment';
import {AppHelper} from '../../helper/app.helper';
import {AppSettings} from '../../app.settings';

/**
 * Shows a tree table with periodic windows on the first column, which can be week or year.
 * The header shows gain for each day or each month.
 */
@Component({
  selector: 'performance-period-treetable',
  template: `
      <p-treeTable [value]="periodWindowsNodes" [columns]="fields"
                   selectionMode="single" [(selection)]="selectedNodes">
          <ng-template pTemplate="header" let-fields>
              <tr>
                  <th *ngFor="let field of fields" [style.width.px]="field.width">
                      {{field.headerTranslated}}
                  </th>
              </tr>
          </ng-template>
          <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="fields">
              <tr>
                  <td *ngFor="let field of fields; let i = index"
                      [ngClass]="{'text-right': (field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric),
              'cell-holiday': getHolidayMissing(rowData, field) === HolidayMissing[HolidayMissing.HM_HOLIDAY],
              'cell-data-missing': getHolidayMissing(rowData, field) === HolidayMissing[HolidayMissing.HM_HISTORY_DATA_MISSING]}"
                      [style.width.px]="field.width">
                      <p-treeTableToggler [rowNode]="rowNode" *ngIf="i === 0"></p-treeTableToggler>
                      <ng-container [ngSwitch]="field.templateName">
                          <ng-container *ngSwitchCase="'greenRed'">
                <span [style.color]='isValueByPathMinusWithEmptyColor(rowData, field)? "red": "green"'>
                  {{getValueByPath(rowData, field)}}
                </span>
                          </ng-container>
                          <ng-container *ngSwitchDefault>
                              <span [pTooltip]="getValueByPath(rowData, field)">{{getValueByPath(rowData, field)}}</span>
                          </ng-container>
                      </ng-container>
                  </td>
              </tr>
          </ng-template>
          <ng-template pTemplate="footer">
              <tr>
                  <ng-container *ngFor="let field of fields">
                      <td *ngIf="field.visible" class="row-total" [style.width.px]="field.width"
                          [ngClass]="{'text-right': (field.dataType===DataType.NumericInteger  || field.dataType===DataType.Numeric
              || field.dataType===DataType.DateTimeNumeric)}">
                          <ng-container [ngSwitch]="field.templateName">
                              <ng-container *ngSwitchCase="'greenRed'">
                <span [style.color]='isValueByPathMinus(performancePeriod?.sumPeriodColSteps, field)? "red": "green"'>
                  {{getValueColumnTotal(field, 0, performancePeriod?.sumPeriodColSteps, null)}}
                </span>
                              </ng-container>
                              <ng-container *ngSwitchDefault>
                                  <span>{{getValueColumnTotal(field, 0, performancePeriod?.sumPeriodColSteps, null)}}</span>
                              </ng-container>
                          </ng-container>
                      </td>
                  </ng-container>
              </tr>
          </ng-template>
      </p-treeTable>
  `,
  styles: [`
      .cell-holiday {
          background-color: greenyellow !important;
      }
      .cell-data-missing {
          background-color: orange !important;
      }
  `]
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

  constructor(translateService: TranslateService,
              gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.translateService.get(['CASH_BALANCE', AppSettings.SECURITY.toUpperCase(), 'MARGIN_CLOSE_GAIN']).subscribe(translatedTexts =>
      this.translatedTexts = translatedTexts);
    this.addColumnFeqH(DataType.String, 'period', true, false,
      {
        width: 200,
        fieldValueFN: this.getFirstColumnLabel.bind(this),
        columnGroupConfigs: [new ColumnGroupConfig(null, 'GRAND_TOTAL')]
      });
    this.addColumn(DataType.Numeric, 'periodWindow.gainPeriodMC', 'TOTAL', true, false,
      {fieldValueFN: this.getLastColumn.bind(this), templateName: 'greenRed'});
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
          colVal = moment(dataobject.periodWindow.startDate).format(this.gps.getDateFormatWithoutYear()) + ' - '
            + moment(dataobject.periodWindow.endDate).format(this.gps.getDateFormatWithoutYear());
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
      return AppHelper.numberFormat(this.gps, (<PeriodStep>psmh)[dataobject.showField], field.maxFractionDigits,
        field.minFractionDigits);
    }
    return null;
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
            this.insertColumn(i, DataType.Numeric, fieldName, calendarLang.dayNamesShort[i], true, false,
              {
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
            this.insertColumn(i + 1, DataType.Numeric, fieldName, calendarLang.monthNamesShort[i], true, false,
              {
                fieldValueFN: this.getDataValue.bind(this), templateName: 'greenRed',
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
      this.performancePeriod.periodWindows.forEach(periodWindow => {
        // The same data row is shown two times but different values
        const pwTreeNode: TreeNode = {
          data: new PeriodWindowWithField(this.GAIN_MC, periodWindow),
          children: [],
          expanded: false,
          leaf: false
        };
        pwTreeNode.children = [{
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


