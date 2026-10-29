import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { Component, DestroyRef, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, switchMap } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';
import { MenuItem, TreeNode } from '@openng/optimus-ui/api';
import { TreeTableConfigBase } from '../../lib/datashowbase/tree.table.config.base';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { HelpIds } from '../../lib/help/help.ids';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { SecurityActionService } from '../service/security-action.service';
import {
  SecurityAction,
  SecurityActionApplication,
  SecurityActionTreeData,
  SecurityTransfer
} from '../model/security-action.model';

import { Panel } from '@openng/optimus-ui/panel';
import { SharedModule } from '@openng/optimus-ui/api';
import { ConfigurableTreeTableComponent } from '../../lib/datashowbase/configurable-tree-table.component';
import { BaseSettings } from '../../lib/base.settings';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { SecurityActionCreateDialogComponent } from './security-action-create-dialog.component';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';

enum NodeLevel {
  SYSTEM_ROOT,
  SYSTEM_ACTION,
  CLIENT_ROOT,
  CLIENT_TRANSFER,
  CLIENT_ISIN_CHANGE
}

@Component({
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <p-panel>
        <p-header>
          <h4>{{ 'SECURITY_ACTION' | translate }}</h4>
        </p-header>
      </p-panel>
      <configurable-tree-table
        [data]="treeNodes()"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        dataKey="nodeKey"
        sortField="name"
        [(selection)]="tableSelection"
        (nodeSelect)="onNodeSelect($event)"
        (nodeUnselect)="onNodeUnselect($event)"
        [contextMenuItems]="contextMenuItems()"
        [showContextMenu]="!!contextMenuItems()"
        [valueGetterFn]="valueGetter" />
      @if (visibleCreateDialog()) {
        <security-action-create
          [visibleDialog]="visibleCreateDialog()"
          (closeDialog)="handleCloseCreateDialog($event)" />
      }
    </div>
  `,
  imports: [TranslatePipe, Panel, SharedModule, ConfigurableTreeTableComponent, SecurityActionCreateDialogComponent]
})
export class SecurityActionTreetableComponent
  extends TreeTableConfigBase
  implements OnInit, OnDestroy, IGlobalMenuAttach
{
  private securityActionService: SecurityActionService = inject(SecurityActionService);
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private messageToastService: MessageToastService = inject(MessageToastService);

  readonly treeNodes = signal<TreeNode[]>([]);
  /** Row highlighted by the single-selection tree table. */
  tableSelection: TreeNode = null;
  /** Translated menu of the selected node, shown as context menu and in the menu bar */
  readonly contextMenuItems = signal<MenuItem[]>(null);
  isAdmin: boolean;
  readonly visibleCreateDialog = signal(false);

  private selectedNode: TreeNode;
  private treeData: SecurityActionTreeData;
  private readonly destroyRef = inject(DestroyRef);
  /** Latest tree request; a reload replaces one still outstanding */
  private loadSubscription: Subscription;
  /** Translation of the latest menu; a newer menu replaces one that is still being translated */
  private menuSubscription: Subscription;

  constructor() {
    super();
    this.isAdmin = AuditHelper.hasAdminRole(this.gps);
    this.addColumnFeqH(DataType.String, 'name', true, false, { width: 250 });
    this.addColumnFeqH(DataType.String, 'isinOld', true, false, { width: 120 });
    this.addColumnFeqH(DataType.String, 'isinNew', true, false, { width: 120 });
    this.addColumnFeqH(DataType.DateString, 'actionDate', true, false, {
      width: 120
    });
    this.addColumnFeqH(DataType.NumericInteger, 'fromFactor', true, false, {
      width: 80
    });
    this.addColumnFeqH(DataType.NumericInteger, 'toFactor', true, false, {
      width: 80
    });
    this.addColumnFeqH(DataType.NumericInteger, 'affectedCount', true, false, {
      width: 80
    });
    this.addColumnFeqH(DataType.NumericInteger, 'appliedCount', true, false, {
      width: 80
    });
    this.addColumn(DataType.String, 'status', 'PROGRESS_STATE_TYPE', true, false, {
      width: 120,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnFeqH(DataType.Numeric, 'units', true, false, { width: 100 });
    this.addColumnFeqH(DataType.Numeric, 'quotation', true, false, {
      width: 100
    });
    this.translateHeadersAndColumns();
  }

  ngOnInit(): void {
    this.readData();
  }

  ngOnDestroy(): void {
    this.menuSubscription?.unsubscribe();
    this.activePanelService.destroyPanel(this);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_SECURITY_ACTION;
  }

  onComponentClick(event: PanelClickEvent): void {
    this.resetMenu();
  }

  onNodeSelect(event: { node: TreeNode }): void {
    this.selectedNode = event.node;
    this.resetMenu();
  }

  onNodeUnselect(event: unknown): void {
    this.selectedNode = null;
    this.resetMenu();
  }

  private readData(): void {
    this.loadSubscription?.unsubscribe();
    this.loadSubscription = this.securityActionService
      .getTree()
      .pipe(
        switchMap((data: SecurityActionTreeData) => {
          this.treeData = data;
          return this.translateService.get(['SYSTEM_ACTIONS', 'CLIENT_TRANSFERS']);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((t) => {
        const treeNodes = this.buildTree(this.treeData, t);
        this.createTranslateValuesStoreForTranslation(treeNodes);
        this.treeNodes.set(treeNodes);
      });
  }

  private buildTree(data: SecurityActionTreeData, translations: any): TreeNode[] {
    const systemRoot: TreeNode = {
      data: {
        name: translations['SYSTEM_ACTIONS'],
        nodeLevel: NodeLevel.SYSTEM_ROOT,
        nodeKey: 'sys_root'
      },
      children: (data.systemActions || []).map((action) => {
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
            name: action.securityOld?.name || action.isinOld,
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

    const transferNodes = (data.clientTransfers || []).map((transfer) => ({
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
      .filter((app) => !app.reversed)
      .map((app) => {
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

  /** Publishes the translated menu of the selected node as context menu and in the menu bar. */
  private resetMenu(): void {
    this.menuSubscription?.unsubscribe();
    this.menuSubscription = TranslateHelper.translateMenuItems$(
      this.prepareEditMenu(),
      this.translateService
    ).subscribe((menuItems) => {
      this.contextMenuItems.set(menuItems);
      this.activePanelService.activatePanel(this, { showMenu: null, editMenu: menuItems });
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

    return menuItems;
  }

  private createIsinChange(): void {
    this.visibleCreateDialog.set(true);
  }

  handleCloseCreateDialog(processedActionData: ProcessedActionData): void {
    this.visibleCreateDialog.set(false);
    if (processedActionData?.action === ProcessedAction.CREATED) {
      this.readData();
    }
  }

  private applyIsinChange(): void {
    const action: SecurityAction = this.selectedNode.data.entity;
    this.securityActionService.applySecurityAction(action.idSecurityAction).subscribe((application) => {
      if (application?.alreadyPerformedCount > 0) {
        this.messageToastService.showMessageI18n(InfoLevelType.INFO, 'APPLY_ISIN_CHANGE_ALREADY_PERFORMED', {
          count: application.alreadyPerformedCount
        });
      } else {
        this.messageToastService.showMessageI18n(null, 'APPLY_ISIN_CHANGE_SUCCESS');
      }
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
