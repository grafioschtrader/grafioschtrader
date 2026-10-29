import { signal, Component, OnInit, input } from '@angular/core';
import { Portfolio } from '../../entities/portfolio';
import { TreeTableConfigBase } from '../../lib/datashowbase/tree.table.config.base';
import { TranslateModule } from '@ngx-translate/core';
import { TreeNode } from '@openng/optimus-ui/api';
import { ColumnConfig } from '../../lib/datashowbase/column.config';

import { TreeTableModule } from '@openng/optimus-ui/treetable';

/**
 * Display all portfolios and corresponding accounts in a tree structure. This enables a selection of these.
 */
@Component({
  selector: 'tenant-dividend-account-selection',
  template: `
    <p-treeTable
      [value]="portfolioAccounts()"
      [columns]="fields"
      selectionMode="checkbox"
      [selection]="selectedNodes()"
      (selectionChange)="onSelectionChange($event)"
      [scrollable]="true"
      scrollHeight="600px">
      <ng-template #caption>
        <div style="text-align:left">
          <h4>{{ title() | translate }}</h4>
          <p-treeTableHeaderCheckbox />
          <span style="margin-left: .25em; vertical-align: middle">{{ 'TOGGLE_ALL' | translate }}</span>
        </div>
      </ng-template>
      <ng-template #header let-fields>
        <tr>
          @for (field of fields; track field) {
            <th>
              {{ field.headerTranslated }}
            </th>
          }
        </tr>
      </ng-template>
      <ng-template #body let-rowNode let-rowData="rowData" let-columns="fields">
        <tr>
          @for (field of fields; track field; let i = $index) {
            <td>
              @if (i === 0) {
                <p-treeTableToggler [rowNode]="rowNode" />
              }
              @if (i === 0) {
                <p-treeTableCheckbox [value]="rowNode" />
              }
              {{ getValueByPath(rowData, field) }}
            </td>
          }
        </tr>
      </ng-template>
    </p-treeTable>
    @if (selectionRequired() && selectedNodes().length === 0) {
      <div>
        <div class="alert alert-danger">
          {{ 'selectionrequried' | translate }}
        </div>
      </div>
    }
  `,
  imports: [TreeTableModule, TranslateModule]
})
export class TenantDividendAccountSelectionComponent extends TreeTableConfigBase implements OnInit {
  readonly columnConfig = input<ColumnConfig[]>(undefined);
  readonly portfolios = input<Portfolio[]>(undefined);
  readonly idsAccount = input<number[]>(undefined);
  readonly listAttributeName = input<'securityaccountList' | 'cashaccountList'>(undefined);
  readonly title = input<string>();
  readonly selectionRequired = input<boolean>();

  readonly portfolioAccounts = signal<TreeNode[]>([]);
  readonly selectedNodes = signal<TreeNode[]>([]);

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.setColumnConfig(this.columnConfig());
    this.translateHeadersAndColumns();
  }

  prepareData(): void {
    const idsAccount = this.idsAccount();
    const markAll = (idsAccount.length === 1 && idsAccount[0] === -1) || idsAccount.length === 0;

    const psa: TreeNode[] = [];
    const selected: TreeNode[] = [];
    this.portfolios().forEach((portfolio) => {
      if (portfolio[this.listAttributeName()].length > 0) {
        const portfolioTreeNode: TreeNode = {
          data: portfolio,
          children: [],
          expanded: true,
          leaf: false
        };
        psa.push(portfolioTreeNode);
        let childSelectionCount = 0;
        portfolio[this.listAttributeName()].forEach((account: { idSecuritycashAccount: number }) => {
          const accountTeeNode = { data: account, leaf: true };
          portfolioTreeNode.children.push(accountTeeNode);
          if (markAll || this.idsAccount().indexOf(account.idSecuritycashAccount) >= 0) {
            selected.push(accountTeeNode);
            childSelectionCount++;
          }
        });
        if (childSelectionCount > 0 && portfolio[this.listAttributeName()].length === childSelectionCount) {
          selected.push(portfolioTreeNode);
        }
      }
    });

    this.portfolioAccounts.set(psa);
    this.selectedNodes.set(selected);
  }

  onSelectionChange(selection: TreeNode | TreeNode[]): void {
    this.selectedNodes.set(Array.isArray(selection) ? selection : selection ? [selection] : []);
  }

  getSelectedAccountIds(): number[] {
    return this.selectedNodes()
      .filter((treeNode) => treeNode.leaf)
      .map((treeNode) => treeNode.data.idSecuritycashAccount);
  }
}
