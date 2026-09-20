import { AppSettings } from '../../shared/app.settings';
import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { TreeNode } from '@openng/optimus-ui/api';
import { TreeTableConfigBase } from '../../lib/datashowbase/tree.table.config.base';
import { ConfigurableTreeTableComponent } from '../../lib/datashowbase/configurable-tree-table.component';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { HoldingMoversBranch, HoldingMoversRanking, HoldingMoversRow } from '../model/holding.movers';

/**
 * One of the two orderings of a movers card: a branch per period, its instruments beneath it.
 *
 * <p>
 * The component is instantiated twice per card with the same branches and a different {@link ranking}, which is why the
 * ordering is chosen by input rather than computed here - both tables show the same figures and can only differ in
 * their sequence.
 * </p>
 */
@Component({
  selector: 'holding-movers-treetable',
  standalone: true,
  imports: [ConfigurableTreeTableComponent],
  template: `
    <configurable-tree-table
      [data]="treeNodes"
      [fields]="fields"
      dataKey="nodeKey"
      [enableSort]="false"
      [scrollable]="true"
      [scrollHeight]="scrollHeight"
      [valueGetterFn]="getValueByPath.bind(this)"
      (nodeExpand)="onBranchToggled($event, true)"
      (nodeCollapse)="onBranchToggled($event, false)" />
  `
})
export class HoldingMoversTreetableComponent extends TreeTableConfigBase implements OnChanges {
  /** Branches in display order, as delivered by the server. */
  @Input() branches: HoldingMoversBranch[] = [];

  /** Which of the two per-branch orderings this table renders. */
  @Input() ranking: HoldingMoversRanking = 'byPercentage';

  /** Currency of the amount column; a movers card reports one currency for the whole client. */
  @Input() currency: string;

  /** Branch codes the reader has open, owned by the card so both tables of a card open together. */
  @Input() expanded: string[] = [];

  /** Height at which the table starts to scroll, so a card cannot grow past its neighbours. */
  @Input() scrollHeight = '220px';

  /** Emits the branch code and its new state when the reader opens or closes a branch. */
  @Output() expandedChange = new EventEmitter<{ branch: string; open: boolean }>();

  treeNodes: TreeNode[] = [];

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
    this.addColumn(DataType.String, 'name', 'DASHBOARD_MOVERS_INSTRUMENT', true, false, {
      width: 160,
      fieldValueFN: this.instrumentLabel.bind(this),
      cellTooltipFN: this.instrumentTooltip.bind(this)
    });
    this.addColumn(DataType.Numeric, 'changePercentage', 'DASHBOARD_MOVERS_CHANGE_PERCENTAGE', true, false, {
      width: 90,
      templateName: 'greenRed',
      headerSuffix: '%',
      maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION
    });
    this.addColumn(DataType.Numeric, 'amountMC', 'DASHBOARD_MOVERS_AMOUNT', true, false, {
      width: 110,
      templateName: 'greenRed'
    });
    this.prepareTreeTableAndTranslate();
  }

  ngOnChanges(): void {
    this.fields.find((field) => field.field === 'amountMC').fixedCurrency = this.currency;
    this.treeNodes = this.branches.map((branch) => this.branchNode(branch));
    this.createTranslateValuesStoreForTranslation(this.treeNodes);
  }

  /**
   * Builds one branch and its instruments. The branch node carries the counts and dates rather than a figure of its
   * own: a period has no single move, and summing the rows would invent one.
   */
  private branchNode(branch: HoldingMoversBranch): TreeNode {
    const rows = branch[this.ranking] ?? [];
    return {
      data: {
        nodeKey: branch.branch,
        name: this.branchLabel(branch),
        changePercentage: null,
        amountMC: null,
        branchData: branch
      },
      expanded: this.expanded.includes(branch.branch),
      leaf: false,
      children: rows.length ? rows.map((row) => this.rowNode(branch, row)) : this.reasonNode(branch)
    };
  }

  /**
   * Stands in for the instruments of an empty branch. A branch is normally empty because no market traded that day,
   * which is worth saying; giving it a child also keeps the tree the same shape every day, which is what the
   * remembered expansion state assumes.
   */
  private reasonNode(branch: HoldingMoversBranch): TreeNode[] {
    if (!branch.reasonKey) {
      return [];
    }
    return [
      {
        data: {
          nodeKey: `${branch.branch}:reason`,
          name: this.translateService.instant(branch.reasonKey, { count: branch.omittedCount }),
          changePercentage: null,
          amountMC: null
        },
        leaf: true
      }
    ];
  }

  /** Builds one instrument. A substituted row keeps the dates it was actually valued over. */
  private rowNode(branch: HoldingMoversBranch, row: HoldingMoversRow): TreeNode {
    return {
      data: { ...row, nodeKey: `${branch.branch}:${row.idSecuritycurrency}` },
      leaf: true
    };
  }

  /**
   * Names a branch by its period and the session it measures. The date belongs in the label because the third branch is
   * chosen by the reader and the other two move with the calendar.
   */
  private branchLabel(branch: HoldingMoversBranch): string {
    const period = this.translateService.instant(`DASHBOARD_MOVERS_${branch.branch}`);
    if (branch.branch === 'INTRADAY' || !branch.date) {
      return period;
    }
    return `${period} ${this.getValueByPath({ date: branch.date }, this.dateField)}`;
  }

  /** Marks a row valued over other dates than its branch names, so a longer span is visible in the list itself. */
  private instrumentLabel(dataobject: any): string {
    return dataobject.substitute ? `${dataobject.name} *` : dataobject.name;
  }

  /** Explains the marker, and for the intraday branch says how old the price behind the row is. */
  private instrumentTooltip(dataobject: any): string {
    if (!dataobject.substitute) {
      return null;
    }
    return dataobject.usedPreviousDate
      ? this.translateService.instant('DASHBOARD_MOVERS_SUBSTITUTE', {
          from: dataobject.usedPreviousDate,
          to: dataobject.usedDate
        })
      : this.translateService.instant('DASHBOARD_MOVERS_STALE', { at: dataobject.usedDate });
  }

  onBranchToggled(event: any, open: boolean): void {
    const node = event?.node ?? event;
    if (node?.data?.nodeKey) {
      this.expandedChange.emit({ branch: node.data.nodeKey, open });
    }
  }

  /** Formats a branch date through the shared date handling rather than a raw pipe. */
  private readonly dateField = TreeTableConfigBase.createColumnConfig(DataType.DateString, 'date', '');
}
