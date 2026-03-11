import {Component, OnDestroy, OnInit} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {MenuItem, TreeNode} from 'primeng/api';
import {TreeTableConfigBase} from '../../lib/datashowbase/tree.table.config.base';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {HelpIds} from '../../lib/help/help.ids';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {SecurityActionService} from '../service/security-action.service';
import {
  SecurityAction,
  SecurityActionApplication,
  SecurityActionTreeData,
  SecurityTransfer
} from '../model/security-action.model';
import {NgClass} from '@angular/common';
import {Panel} from 'primeng/panel';
import {SharedModule} from 'primeng/api';
import {ConfigurableTreeTableComponent} from '../../lib/datashowbase/configurable-tree-table.component';
import {BaseSettings} from '../../lib/base.settings';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {SecurityActionCreateComponent} from './security-action-create.component';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';

enum NodeLevel {
  SYSTEM_ROOT, SYSTEM_ACTION, CLIENT_ROOT, CLIENT_TRANSFER, CLIENT_ISIN_CHANGE
}

@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-panel>
        <p-header>
          <h4>{{ 'SECURITY_ACTION' | translate }}</h4>
        </p-header>
      </p-panel>
      <configurable-tree-table
        [data]="treeNodes" [fields]="fields" dataKey="nodeKey"
        sortField="name"
        [(selection)]="selectedNodes" (nodeSelect)="onNodeSelect($event)"
        (nodeUnselect)="onNodeUnselect($event)"
        [contextMenuItems]="contextMenuItems" [showContextMenu]="!!contextMenuItems"
        [valueGetterFn]="getValueByPath.bind(this)">
      </configurable-tree-table>
      @if (visibleCreateDialog) {
        <security-action-create
          [visibleDialog]="visibleCreateDialog"
          (closeDialog)="handleCloseCreateDialog($event)">
        </security-action-create>
      }
    </div>
  `,
  standalone: true,
  imports: [NgClass, TranslatePipe, Panel, SharedModule, ConfigurableTreeTableComponent, SecurityActionCreateComponent]
})
export class SecurityActionTreetableComponent extends TreeTableConfigBase implements OnInit, OnDestroy, IGlobalMenuAttach {

  treeNodes: TreeNode[] = [];
  selectedNodes: TreeNode[] = [];
  contextMenuItems: MenuItem[];
  isAdmin: boolean;
  visibleCreateDialog = false;

  private selectedNode: TreeNode;
  private treeData: SecurityActionTreeData;

  constructor(private securityActionService: SecurityActionService,
              private activePanelService: ActivePanelService,
              private messageToastService: MessageToastService,
              translateService: TranslateService,
              gps: GlobalparameterService) {
    super(translateService, gps);
    this.isAdmin = AuditHelper.hasAdminRole(gps);
    this.addColumnFeqH(DataType.String, 'name', true, false, {width: 250});
    this.addColumnFeqH(DataType.String, 'isinOld', true, false, {width: 120});
    this.addColumnFeqH(DataType.String, 'isinNew', true, false, {width: 120});
    this.addColumnFeqH(DataType.DateString, 'actionDate', true, false, {width: 120});
    this.addColumnFeqH(DataType.NumericInteger, 'fromFactor', true, false, {width: 80});
    this.addColumnFeqH(DataType.NumericInteger, 'toFactor', true, false, {width: 80});
    this.addColumnFeqH(DataType.NumericInteger, 'affectedCount', true, false, {width: 80});
    this.addColumnFeqH(DataType.NumericInteger, 'appliedCount', true, false, {width: 80});
    this.addColumn(DataType.String, 'status', 'PROGRESS_STATE_TYPE', true, false, {width: 120, translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.Numeric, 'units', true, false, {width: 100});
    this.addColumnFeqH(DataType.Numeric, 'quotation', true, false, {width: 100});
    this.translateHeadersAndColumns();
  }

  ngOnInit(): void {
    this.readData();
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_SECURITY_ACTION;
  }

  onComponentClick(event): void {
    this.resetMenu();
  }

  onNodeSelect(event): void {
    this.selectedNode = event.node;
    this.resetMenu();
  }

  onNodeUnselect(event): void {
    this.selectedNode = null;
    this.resetMenu();
  }

  private readData(): void {
    this.securityActionService.getTree().subscribe((data: SecurityActionTreeData) => {
      this.treeData = data;
      this.translateService.get(['SYSTEM_ACTIONS', 'CLIENT_TRANSFERS']).subscribe(t => {
        this.treeNodes = this.buildTree(data, t);
        this.createTranslateValuesStoreForTranslation(this.treeNodes);
      });
    });
  }

  private buildTree(data: SecurityActionTreeData, translations: any): TreeNode[] {
    const systemRoot: TreeNode = {
      data: {
        name: translations['SYSTEM_ACTIONS'],
        nodeLevel: NodeLevel.SYSTEM_ROOT,
        nodeKey: 'sys_root'
      },
      children: (data.systemActions || []).map(action => {
        const app = data.appliedByCurrentTenant[action.idSecurityAction];
        let status = '';
        if (app && !app.reversed) {
          status = 'APPLIED';
        } else if (app && app.reversed) {
          status = 'REVERSED';
        } else {
          status = 'NOT_APPLIED';
        }
        return {
          data: {
            name: (action.securityOld?.name || action.isinOld),
            isinOld: action.isinOld,
            isinNew: action.isinNew,
            actionDate: action.actionDate,
            fromFactor: action.fromFactor,
            toFactor: action.toFactor,
            affectedCount: action.affectedCount,
            appliedCount: action.appliedCount,
            status: status,
            nodeLevel: NodeLevel.SYSTEM_ACTION,
            entity: action,
            application: app,
            nodeKey: 'sa_' + action.idSecurityAction
          },
          leaf: true
        };
      }),
      expanded: true
    };

    const transferNodes = (data.clientTransfers || []).map(transfer => ({
      data: {
        name: transfer.security?.name || '',
        actionDate: transfer.transferDate,
        units: transfer.units,
        quotation: transfer.quotation,
        status: '',
        nodeLevel: NodeLevel.CLIENT_TRANSFER,
        entity: transfer,
        nodeKey: 'st_' + transfer.idSecurityTransfer
      },
      leaf: true
    }));

    const appliedIsinChanges = Object.values(data.appliedByCurrentTenant || {})
      .filter(app => !app.reversed)
      .map(app => {
        const action = app.securityAction;
        return {
          data: {
            name: action.securityOld?.name || action.isinOld,
            isinOld: action.isinOld,
            isinNew: action.isinNew,
            actionDate: action.actionDate,
            fromFactor: action.fromFactor,
            toFactor: action.toFactor,
            status: 'APPLIED',
            nodeLevel: NodeLevel.CLIENT_ISIN_CHANGE,
            entity: action,
            application: app,
            nodeKey: 'ci_' + action.idSecurityAction
          },
          leaf: true
        };
      });

    const clientRoot: TreeNode = {
      data: {
        name: translations['CLIENT_TRANSFERS'],
        nodeLevel: NodeLevel.CLIENT_ROOT,
        nodeKey: 'cli_root'
      },
      children: [...transferNodes, ...appliedIsinChanges],
      expanded: true
    };

    return [systemRoot, clientRoot];
  }

  private resetMenu(): void {
    this.contextMenuItems = this.prepareEditMenu();
    this.activePanelService.activatePanel(this, {
      showMenu: null,
      editMenu: this.contextMenuItems
    });
  }

  private prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (!this.selectedNode) {
      return menuItems;
    }
    const level: NodeLevel = this.selectedNode.data.nodeLevel;

    if (level === NodeLevel.SYSTEM_ROOT && this.isAdmin) {
      menuItems.push({
        label: 'CREATE_ISIN_CHANGE' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.createIsinChange()
      });
    } else if (level === NodeLevel.SYSTEM_ACTION) {
      const app: SecurityActionApplication = this.selectedNode.data.application;
      if (!app || app.reversed) {
        menuItems.push({
          label: 'APPLY_ISIN_CHANGE',
          command: () => this.applyIsinChange()
        });
      }
      if (app && !app.reversed) {
        menuItems.push({
          label: 'REVERSE_ISIN_CHANGE',
          command: () => this.reverseIsinChange()
        });
      }
      if (this.isAdmin) {
        const action: SecurityAction = this.selectedNode.data.entity;
        if (action.appliedCount === 0) {
          menuItems.push({
            label: 'DELETE' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () => this.deleteIsinChange()
          });
        }
      }
    } else if (level === NodeLevel.CLIENT_TRANSFER) {
      const transfer: SecurityTransfer = this.selectedNode.data.entity;
      menuItems.push({
        label: 'REVERSE_TRANSFER',
        command: () => this.reverseTransfer(),
        disabled: !transfer.reversible
      });
    } else if (level === NodeLevel.CLIENT_ISIN_CHANGE) {
      menuItems.push({
        label: 'REVERSE_ISIN_CHANGE',
        command: () => this.reverseIsinChange()
      });
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  private createIsinChange(): void {
    this.visibleCreateDialog = true;
  }

  handleCloseCreateDialog(processedActionData: ProcessedActionData): void {
    this.visibleCreateDialog = false;
    if (processedActionData?.action === ProcessedAction.CREATED) {
      this.readData();
    }
  }

  private applyIsinChange(): void {
    const action: SecurityAction = this.selectedNode.data.entity;
    this.securityActionService.applySecurityAction(action.idSecurityAction).subscribe(() => {
      this.messageToastService.showMessageI18n(null, 'APPLY_ISIN_CHANGE_SUCCESS');
      this.readData();
    });
  }

  private reverseIsinChange(): void {
    const action: SecurityAction = this.selectedNode.data.entity;
    this.securityActionService.reverseSecurityAction(action.idSecurityAction).subscribe(() => {
      this.messageToastService.showMessageI18n(null, 'REVERSE_ISIN_CHANGE_SUCCESS');
      this.readData();
    });
  }

  private deleteIsinChange(): void {
    const action: SecurityAction = this.selectedNode.data.entity;
    this.securityActionService.deleteSecurityAction(action.idSecurityAction).subscribe(() => {
      this.selectedNode = null;
      this.readData();
    });
  }

  private reverseTransfer(): void {
    const transfer: SecurityTransfer = this.selectedNode.data.entity;
    this.securityActionService.reverseTransfer(transfer.idSecurityTransfer).subscribe(() => {
      this.messageToastService.showMessageI18n(null, 'REVERSE_TRANSFER_SUCCESS');
      this.selectedNode = null;
      this.readData();
    });
  }
}
