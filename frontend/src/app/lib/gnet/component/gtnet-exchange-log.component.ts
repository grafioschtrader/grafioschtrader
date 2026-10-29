import { httpResource } from '@angular/common/http';
import {
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  OnInit,
  TemplateRef,
  untracked,
  viewChild
} from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { TreeNode } from '@openng/optimus-ui/api';
import { ColumnConfig } from '../../datashowbase/column.config';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { ConfigurableTreeTableComponent } from '../../datashowbase/configurable-tree-table.component';
import { ShowRecordConfigBase } from '../../datashowbase/show.record.config.base';
import { TreeTableConfigBase } from '../../datashowbase/tree.table.config.base';
import { DataType } from '../../dynamic-form/models/data.type';
import { HelpIds } from '../../help/help.ids';
import { reportResourceErrors } from '../../login/service/resource.error.report';
import { IGlobalMenuAttach } from '../../mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../mainmenubar/service/active.panel.service';
import { GTNetExchangeLogNode, GTNetExchangeLogTree } from '../model/gtnet-exchange-log';
import { GTNetExchangeLogService } from '../service/gtnet-exchange-log.service';

/**
 * Component for displaying GTNet exchange log statistics.
 * Shows a table of GTNets with expandable rows containing supplier and consumer TreeTables.
 */
@Component({
  selector: 'gtnet-exchange-log',
  imports: [TranslateModule, ConfigurableTableComponent, ConfigurableTreeTableComponent],
  template: `
    <configurable-table
      [data]="exchangeLogTrees()"
      [loading]="logsResource.isLoading()"
      [fields]="mainTableFields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="'idGtNet'"
      [expandable]="true"
      [expandedRowTemplate]="expandedContent"
      [containerClass]="{
        'data-container-full': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [selectionMode]="null"
      [valueGetterFn]="mainTableValueGetter"
      [baseLocale]="baseLocale"
      (componentClick)="onComponentClick($event)">
      <h4 caption>{{ titleKey() | translate }}</h4>
    </configurable-table>

    <ng-template #expandedContent let-tree>
      <div class="p-3">
        <!-- Supplier Statistics -->
        <h5>{{ 'SUPPLIER_STATISTICS' | translate }}</h5>
        @if (hasData(tree.supplierTotal)) {
          <configurable-tree-table
            [data]="getSupplierNodes(tree)"
            [fields]="fields"
            [fieldsRevision]="fieldsRevision()"
            [selectionMode]="null"
            [enableSort]="false"
            [scrollable]="true"
            [valueGetterFn]="valueGetter" />
        } @else {
          <p class="no-data">{{ 'NO_DATA_AVAILABLE' | translate }}</p>
        }

        <!-- Consumer Statistics -->
        <h5 class="mt-3">{{ 'CONSUMER_STATISTICS' | translate }}</h5>
        @if (hasData(tree.consumerTotal)) {
          <configurable-tree-table
            [data]="getConsumerNodes(tree)"
            [fields]="fields"
            [fieldsRevision]="fieldsRevision()"
            [selectionMode]="null"
            [enableSort]="false"
            [scrollable]="true"
            [valueGetterFn]="valueGetter" />
        } @else {
          <p class="no-data">{{ 'NO_DATA_AVAILABLE' | translate }}</p>
        }
      </div>
    </ng-template>
  `,
  styles: [
    `
      h5 {
        margin-top: 0.5rem;
        margin-bottom: 0.5rem;
        font-weight: 500;
      }
      .no-data {
        padding: 1rem;
        text-align: center;
        color: #666;
        font-style: italic;
      }
      .mt-3 {
        margin-top: 1rem;
      }
      .p-3 {
        padding: 1rem;
      }
    `
  ]
})
export class GTNetExchangeLogComponent extends TreeTableConfigBase implements OnInit, IGlobalMenuAttach {
  private gtNetExchangeLogService: GTNetExchangeLogService = inject(GTNetExchangeLogService);
  private activePanelService: ActivePanelService = inject(ActivePanelService);

  readonly entityKindInput = input('LAST_PRICE', {
    alias: 'entityKind',
    transform: (value: string | undefined) => (value ? decodeURIComponent(value).toUpperCase() : 'LAST_PRICE')
  });
  readonly entityKind = this.entityKindInput;
  readonly expandedContent = viewChild<TemplateRef<any>>('expandedContent');

  readonly logsResource = httpResource<GTNetExchangeLogTree[]>(() =>
    this.gtNetExchangeLogService.getAllExchangeLogTreesRequest(this.entityKind())
  );
  readonly exchangeLogTrees = computed(() => (this.logsResource.hasValue() ? this.logsResource.value() : []));
  mainTableFields: ColumnConfig[] = [];
  readonly titleKey = computed(() => this.entityKind());

  /** Stable value getter of the outer table, which shows plain properties of the tree */
  readonly mainTableValueGetter = (row: GTNetExchangeLogTree, field: ColumnConfig) =>
    this.getMainTableValue(row, field);

  private readonly destroyRef = inject(DestroyRef);
  private supplierNodesCache: Map<number, TreeNode[]> = new Map();
  private consumerNodesCache: Map<number, TreeNode[]> = new Map();

  constructor() {
    super();
    effect(() => {
      this.exchangeLogTrees();
      untracked(() => {
        this.supplierNodesCache.clear();
        this.consumerNodesCache.clear();
      });
    });

    reportResourceErrors(this.logsResource, this.gtNetExchangeLogService);
  }

  ngOnInit(): void {
    // Configure main table column (outer table)
    this.mainTableFields = [
      ShowRecordConfigBase.createColumnConfig(DataType.String, 'domainRemoteName', 'DOMAIN_REMOTE_NAME', true, false)
    ];
    this.translateHeaders(
      this.mainTableFields.map((f) => f.headerKey),
      this.mainTableFields
    );

    // Configure tree table columns (inner tables)
    this.addColumnFeqH(DataType.String, 'label', true, false, { width: 200 });
    this.addColumnFeqH(DataType.NumericInteger, 'entitiesSent', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'entitiesUpdated', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'entitiesInResponse', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'requestCount', true, false);
    this.translateHeadersAndColumns();
  }

  getMainTableValue(row: GTNetExchangeLogTree, field: ColumnConfig): any {
    return row[field.field as keyof GTNetExchangeLogTree];
  }

  hasData(node: GTNetExchangeLogNode): boolean {
    return node && node.requestCount > 0;
  }

  getSupplierNodes(tree: GTNetExchangeLogTree): TreeNode[] {
    if (!this.supplierNodesCache.has(tree.idGtNet)) {
      this.supplierNodesCache.set(tree.idGtNet, this.buildTreeNodes(tree.supplierTotal));
    }
    return this.supplierNodesCache.get(tree.idGtNet)!;
  }

  getConsumerNodes(tree: GTNetExchangeLogTree): TreeNode[] {
    if (!this.consumerNodesCache.has(tree.idGtNet)) {
      this.consumerNodesCache.set(tree.idGtNet, this.buildTreeNodes(tree.consumerTotal));
    }
    return this.consumerNodesCache.get(tree.idGtNet)!;
  }

  private buildTreeNodes(node: GTNetExchangeLogNode): TreeNode[] {
    if (!node || !node.children || node.children.length === 0) {
      // If no children, show the total node itself
      if (node && node.requestCount > 0) {
        return [this.convertToTreeNode(node)];
      }
      return [];
    }

    return node.children.map((child) => this.convertToTreeNode(child));
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu() {}

  callMeDeactivate(): void {}

  onComponentClick(event): void {
    this.activePanelService.activatePanel(this);
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_GT_NET_EXCHANGE_LOG;
  }

  private convertToTreeNode(node: GTNetExchangeLogNode): TreeNode {
    const treeNode: TreeNode = {
      data: {
        label: node.label,
        entitiesSent: node.entitiesSent,
        entitiesUpdated: node.entitiesUpdated,
        entitiesInResponse: node.entitiesInResponse,
        requestCount: node.requestCount
      },
      expanded: false
    };

    if (node.children && node.children.length > 0) {
      treeNode.children = node.children.map((child) => this.convertToTreeNode(child));
    }

    return treeNode;
  }
}
