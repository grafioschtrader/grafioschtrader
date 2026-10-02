import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TreeNode } from '@openng/optimus-ui/api';
import { TreeTableConfigBase } from '../../lib/datashowbase/tree.table.config.base';
import { ConfigurableTreeTableComponent } from '../../lib/datashowbase/configurable-tree-table.component';
import { ColumnConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import {
  AlgoAlertService,
  AlgoTopAlertGroup,
  HierarchyNodeAlerts,
  HierarchyStrategyAlert
} from '../service/algo-alert.service';

/** Row of the read-only tree: a hierarchy, one of its nodes, or an alert strategy on that node. */
interface HierarchyAlertRow {
  nodeKey: string;
  name?: string;
  algoStrategyImplementations?: string;
  alertEnabled?: boolean;
  /** NLS key of the live state, translated through the status column. */
  status?: string;
  /** True for a hierarchy row, which is shown in bold. */
  top?: boolean;
  /** True when the live evaluation does not consider this row; its status cell is highlighted. */
  dormant?: boolean;
}

/**
 * Read-only tree of the alerts configured in the tenant's AlgoTop hierarchies, shown on the rule-based trading
 * landing page below the standalone alerts. An alert may be configured on any hierarchy, but only the one assigned
 * to monitoring is evaluated live; every other hierarchy and its alerts are marked as dormant, so that the user sees at
 * a glance which alerts can actually report something. The alerts are edited in the view of their hierarchy.
 */
@Component({
  selector: 'algo-hierarchy-alert-overview',
  template: `
    @if (treeNodes.length > 0) {
      <h5>{{ 'ALGO_HIERARCHY_ALERTS' | translate }}</h5>
      <configurable-tree-table
        [data]="treeNodes"
        [fields]="fields"
        dataKey="nodeKey"
        [selectionMode]="null"
        [valueGetterFn]="getValueByPath.bind(this)"
        [rowClassFn]="getRowClass.bind(this)"
        [cellClassFn]="getCellClass.bind(this)"
        [enableSort]="false">
      </configurable-tree-table>
    }
  `,
  styles: [
    `
      .kb-row {
        font-weight: 700 !important;
      }
    `
  ],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [CommonModule, TranslateModule, ConfigurableTreeTableComponent]
})
export class AlgoHierarchyAlertOverviewComponent extends TreeTableConfigBase implements OnInit {
  private static readonly MONITORED = 'ALGO_ALERT_MONITORED';
  private static readonly DORMANT = 'ALGO_ALERT_DORMANT_NOT_ASSIGNED';
  private static readonly SWITCHED_OFF = 'ALGO_ALERT_SWITCHED_OFF';

  treeNodes: TreeNode[] = [];

  /**
   * @param algoAlertService - Reads the alerts of the tenant's hierarchies
   * @param translateService - Angular translation service for internationalization support
   * @param gps - Global parameter service providing user locale and formatting preferences
   */
  constructor(
    private algoAlertService: AlgoAlertService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(translateService, gps);
    this.addColumn(DataType.String, 'name', 'NAME', true, false);
    this.addColumn(DataType.String, 'algoStrategyImplementations', 'ALGO_STRATEGY_NAME', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnFeqH(DataType.Boolean, 'alertEnabled', true, false, { templateName: 'check' });
    this.addColumn(DataType.String, 'status', 'ALGO_ALERT_STATUS', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.prepareTreeTableAndTranslate();
  }

  ngOnInit(): void {
    this.algoAlertService.hierarchyAlerts().subscribe((groups: AlgoTopAlertGroup[]) => {
      this.treeNodes = groups.map((group) => this.createTopNode(group));
      this.createTranslateValuesStoreForTranslation(this.treeNodes);
    });
  }

  /** Hierarchy rows are bold. */
  getRowClass(rowNode: any, rowData: HierarchyAlertRow): string | null {
    return rowData.top ? 'kb-row' : null;
  }

  /** The status of a dormant hierarchy or alert is highlighted, so that it stands out from the monitored ones. */
  getCellClass(rowData: HierarchyAlertRow, field: ColumnConfig): string | null {
    return field.field === 'status' && rowData.dormant ? 'algo-value-warning' : null;
  }

  private createTopNode(group: AlgoTopAlertGroup): TreeNode {
    const row: HierarchyAlertRow = {
      nodeKey: 't' + group.idAlgoTop,
      name: group.name,
      status: group.assigned
        ? AlgoHierarchyAlertOverviewComponent.MONITORED
        : AlgoHierarchyAlertOverviewComponent.DORMANT,
      top: true,
      dormant: !group.assigned
    };
    return {
      data: row,
      children: group.nodes.map((node) => this.createNodeNode(node, group.assigned)),
      expanded: true
    };
  }

  private createNodeNode(node: HierarchyNodeAlerts, assigned: boolean): TreeNode {
    const row: HierarchyAlertRow = { nodeKey: 'n' + node.idNode, name: node.nodeName };
    return {
      data: row,
      children: node.alerts.map((alert) => this.createStrategyNode(alert, assigned)),
      expanded: true
    };
  }

  private createStrategyNode(alert: HierarchyStrategyAlert, assigned: boolean): TreeNode {
    const row: HierarchyAlertRow = {
      nodeKey: 's' + alert.idAlgoRuleStrategy,
      algoStrategyImplementations: alert.algoStrategyImplementations,
      alertEnabled: alert.alertEnabled,
      status: !assigned
        ? AlgoHierarchyAlertOverviewComponent.DORMANT
        : alert.effectiveActive
          ? AlgoHierarchyAlertOverviewComponent.MONITORED
          : AlgoHierarchyAlertOverviewComponent.SWITCHED_OFF,
      dormant: !alert.effectiveActive
    };
    return { data: row, leaf: true };
  }
}
