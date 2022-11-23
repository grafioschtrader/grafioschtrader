import {Component, Input, OnInit} from '@angular/core';
import {Portfolio} from '../../entities/portfolio';
import {TreeTableConfigBase} from '../../shared/datashowbase/tree.table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TreeNode} from 'primeng/api';
import {ColumnConfig} from '../../shared/datashowbase/column.config';

/**
 * Display all portfolios and corresponding accounts in a tree structure. This enables a selection of these.
 */
@Component({
  selector: 'tenant-dividend-account-selection',
  template: `
    <p-treeTable [value]="portfolioAccounts" [columns]="fields" selectionMode="checkbox" [(selection)]="selectedNodes"
                 [scrollable]="true" scrollHeight="600px">
      <ng-template pTemplate="caption">
        <div style="text-align:left">
          <h4>{{title | translate}}</h4>
          <p-treeTableHeaderCheckbox></p-treeTableHeaderCheckbox>
          <span style="margin-left: .25em; vertical-align: middle">{{'TOGGLE_ALL' | translate}}</span>
        </div>
      </ng-template>
      <ng-template pTemplate="header" let-fields>
        <tr>
          <th *ngFor="let field of fields">
            {{field.headerTranslated}}
          </th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="fields">
        <tr>
          <td *ngFor="let field of fields; let i = index">
            <p-treeTableToggler [rowNode]="rowNode" *ngIf="i === 0"></p-treeTableToggler>
            <p-treeTableCheckbox [value]="rowNode" *ngIf="i === 0"></p-treeTableCheckbox>
            {{getValueByPath(rowData, field)}}
          </td>
        </tr>
      </ng-template>
    </p-treeTable>
    <div *ngIf="selectionRequired && selectedNodes.length === 0">
      <div class="alert alert-danger">
        {{'selectionrequried' | translate}}
      </div>
    </div>
  `
})
export class TenantDividendAccountSelectionComponent extends TreeTableConfigBase implements OnInit {
  @Input() columnConfig: ColumnConfig[];
  @Input() portfolios: Portfolio[];
  @Input() idsAccount: number[];
  @Input() listAttributeName: string;
  @Input() title: string;
  @Input() selectionRequired: boolean;

  portfolioAccounts: TreeNode[] = [];
  selectedNodes: TreeNode[] = [];

  constructor(translateService: TranslateService,
              gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.setColumnConfig(this.columnConfig);
    this.translateHeadersAndColumns();
  }

  prepareData(): void {
    const markAll = this.idsAccount.length === 1 && this.idsAccount[0] === -1 || this.idsAccount.length === 0;

    const psa: TreeNode[] = [];
    this.portfolios.forEach(portfolio => {
      if (portfolio[this.listAttributeName].length > 0) {
        const portfolioTreeNode: TreeNode = {data: portfolio, children: [], expanded: true, leaf: false};
        psa.push(portfolioTreeNode);
        let childSelectonCount = 0;
        portfolio[this.listAttributeName].forEach(account => {
          const accountTeeNode = {data: account, leaf: true};
          portfolioTreeNode.children.push(accountTeeNode);
          if (markAll || this.idsAccount.indexOf(account.idSecuritycashAccount) >= 0) {
            this.selectedNodes.push(accountTeeNode);
            childSelectonCount++;
          }
        });
        if (childSelectonCount > 0 && portfolio[this.listAttributeName].length === childSelectonCount) {
          this.selectedNodes.push(portfolioTreeNode);
        }
      }
    });

    this.portfolioAccounts = psa;
  }

  getSelectedAccountIds(): number[] {
    return this.selectedNodes.filter(treeNode => treeNode.leaf).map(treeNode => treeNode.data.idSecuritycashAccount);
  }
}
