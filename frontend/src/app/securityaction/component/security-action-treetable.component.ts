import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { Component, DestroyRef, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, Subscription, switchMap, map } from 'rxjs';
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
  SecurityActionType,
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
import { SpinOffApplyDialogComponent } from './spin-off-apply-dialog.component';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { AppHelper } from '../../lib/helper/app.helper';
import { AppSettings } from '../../shared/app.settings';

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
        [scrollable]="true"
        [frozenColumnCount]="frozenColumnCount"
        [(selection)]="tableSelection"
        (nodeSelect)="onNodeSelect($event)"
        (nodeUnselect)="onNodeUnselect($event)"
        [contextMenuItems]="contextMenuItems()"
        [showContextMenu]="!!contextMenuItems()"
        [contextMenuAppendTo]="'body'"
        [valueGetterFn]="valueGetter" />
      @if (visibleCreateDialog()) {
        <security-action-create
          [visibleDialog]="visibleCreateDialog()"
          [initialType]="createType()"
          [actionToEdit]="actionToEdit()"
          (closeDialog)="handleCloseCreateDialog($event)" />
      }
      @if (spinOffToApply(); as actionId) {
        <spin-off-apply-dialog
          [actionId]="actionId"
          [visibleDialog]="true"
          (closeDialog)="closeSpinOffPreview($event)" />
      }
    </div>
  `,
  imports: [
    TranslatePipe,
    Panel,
    SharedModule,
    ConfigurableTreeTableComponent,
    SecurityActionCreateDialogComponent,
    SpinOffApplyDialogComponent
  ]
})
export class SecurityActionTreetableComponent
  extends TreeTableConfigBase
  implements OnInit, OnDestroy, IGlobalMenuAttach
{
  private securityActionService: SecurityActionService = inject(SecurityActionService);
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private readonly confirmationService = inject(ConfirmationService);
  readonly spinOffToApply = signal<number | null>(null);

  readonly treeNodes = signal<TreeNode[]>([]);
  /** Row highlighted by the single-selection tree table. */
  tableSelection: TreeNode | null = null;
  /** Translated menu of the selected node, shown as context menu and in the menu bar */
  readonly contextMenuItems = signal<MenuItem[] | null>(null);
  isAdmin: boolean;
  readonly visibleCreateDialog = signal(false);
  readonly createType = signal<SecurityActionType>('ISIN_CHANGE');
  readonly actionToEdit = signal<SecurityAction | null>(null);

  private selectedNode: TreeNode | null = null;
  private readonly destroyRef = inject(DestroyRef);
  /** Latest tree request; a reload replaces one still outstanding */
  private loadSubscription?: Subscription;
  /** Translation of the latest menu; a newer menu replaces one that is still being translated */
  private menuSubscription?: Subscription;

  constructor() {
    super();
    this.isAdmin = AuditHelper.hasAdminRole(this.gps);
    this.frozenColumnsSelectable = true;
    this.addColumnFeqH(DataType.String, 'name', true, false, { width: 250 });
    this.addColumnFeqH(DataType.String, 'actionType', true, false, {
      width: 100,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'status', 'PROGRESS_STATE_TYPE', true, false, {
      width: 120,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnFeqH(DataType.Numeric, 'costAllocation', true, false, {
      width: 110,
      headerSuffix: '%',
      maxFractionDigits: 4
    });
    // Whether the parent's history source is adjusted for a spin-off; an administrator resolves UNDETERMINED
    this.addColumnFeqH(DataType.String, 'historyBasis', this.isAdmin, this.isAdmin, {
      width: 100,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnFeqH(DataType.String, 'isinOld', true, false, { width: 100 });
    this.addColumnFeqH(DataType.String, 'isinNew', true, false, { width: 100 });
    this.addColumnFeqH(DataType.DateString, 'actionDate', true, false, {
      width: 80
    });
    this.addColumnFeqH(DataType.NumericInteger, 'fromFactor', true, false, {
      width: 60
    });
    this.addColumnFeqH(DataType.NumericInteger, 'toFactor', true, false, {
      width: 60
    });
    this.addColumnFeqH(DataType.Numeric, 'units', true, false, { width: 100 });
    // The transfer price; there is no QUOTATION key, PRICE is the label of a price per unit
    this.addColumn(DataType.Numeric, 'quotation', 'PRICE', true, false, {
      width: 100
    });
    this.addColumnFeqH(DataType.NumericInteger, 'affectedCount', true, true, {
      width: 80
    });
    this.addColumnFeqH(DataType.NumericInteger, 'appliedCount', true, true, {
      width: 80
    });
    this.translateHeadersAndColumns();
  }

  ngOnInit(): void {
    this.readTableDefinition(AppSettings.SECURITY_ACTION_TREETABLE_SETTINGS_STORE);
    this.readData();
  }

  ngOnDestroy(): void {
    this.writeTableDefinition(AppSettings.SECURITY_ACTION_TREETABLE_SETTINGS_STORE);
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
        switchMap((data: SecurityActionTreeData) =>
          this.translateService
            .get(['SYSTEM_ACTIONS', 'CLIENT_TRANSFERS'])
            .pipe(map((translations) => ({ data, translations })))
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(({ data, translations }) => {
        const treeNodes = this.buildTree(data, translations);
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
        const app = action.idSecurityAction == null ? undefined : data.appliedByCurrentTenant[action.idSecurityAction];
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
            actionType: action.actionType,
            costAllocation: action.costAllocation == null ? null : action.costAllocation * 100,
            historyBasis: action.historyBasis ?? null,
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
      .filter((app) => !app.reversed && app.securityAction.actionType === 'ISIN_CHANGE')
      .map((app) => {
        const action = app.securityAction;
        return {
          data: {
            name: action.securityOld?.name || action.isinOld,
            actionType: action.actionType,
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

  /**
   * Publishes the translated menu of the selected node as context menu and in the menu bar, together with the show
   * menu that switches columns on and off and sets the number of frozen columns.
   */
  private resetMenu(): void {
    this.menuSubscription?.unsubscribe();
    this.menuSubscription = forkJoin([
      TranslateHelper.translateMenuItems$(this.prepareEditMenu(), this.translateService),
      TranslateHelper.translateMenuItems$(this.getColumnsShow(), this.translateService)
    ]).subscribe(([menuItems, showMenu]) => {
      this.contextMenuItems.set(menuItems);
      this.activePanelService.activatePanel(this, {
        showMenu: showMenu.length > 0 ? showMenu : null,
        editMenu: menuItems
      });
    });
  }

  private prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (!this.selectedNode) {
      return menuItems;
    }
    const level: NodeLevel = this.selectedNode.data.nodeLevel;

    if (level === NodeLevel.SYSTEM_ROOT && this.isAdmin) {
      menuItems.push(
        {
          label: 'CREATE_ISIN_CHANGE' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => this.openActionDialog('ISIN_CHANGE')
        },
        { label: 'CREATE_SPIN_OFF' + BaseSettings.DIALOG_MENU_SUFFIX, command: () => this.openActionDialog('SPIN_OFF') }
      );
    } else if (level === NodeLevel.SYSTEM_ACTION) {
      const action: SecurityAction = this.selectedNode.data.entity;
      const app: SecurityActionApplication = this.selectedNode.data.application;
      if (action.actionType === 'SPIN_OFF') {
        menuItems.push(
          app && !app.reversed
            ? { label: 'REVERSE_SPIN_OFF', command: () => this.confirmSpinOffReversal(action) }
            : {
                label: 'APPLY_SPIN_OFF' + BaseSettings.DIALOG_MENU_SUFFIX,
                command: () => this.spinOffToApply.set(action.idSecurityAction)
              }
        );
      }
      if (action.actionType === 'ISIN_CHANGE' && (!app || app.reversed)) {
        menuItems.push({
          label: 'APPLY_ISIN_CHANGE',
          command: () => this.applyIsinChange()
        });
      }
      if (action.actionType === 'ISIN_CHANGE' && app && !app.reversed) {
        menuItems.push({
          label: 'REVERSE_ISIN_CHANGE',
          command: () => this.reverseIsinChange()
        });
      }
      if (this.isAdmin) {
        if (action.actionType === 'SPIN_OFF') {
          menuItems.push({
            label: 'EDIT_SPIN_OFF' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () => this.openActionDialog('SPIN_OFF', action)
          });
        }
        if (action.appliedCount === 0) {
          menuItems.push({
            label: 'DELETE' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () => this.deleteAction()
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

  private openActionDialog(type: SecurityActionType, action: SecurityAction | null = null): void {
    this.createType.set(type);
    this.actionToEdit.set(action);
    this.visibleCreateDialog.set(true);
  }

  closeSpinOffPreview(event: ProcessedActionData): void {
    this.spinOffToApply.set(null);
    if (event.action === ProcessedAction.CREATED) {
      this.clearSelectionAndReload();
    }
  }

  private confirmSpinOffReversal(action: SecurityAction): void {
    const id = action.idSecurityAction;
    if (id == null) return;
    AppHelper.confirmationDialog(this.translateService, this.confirmationService, 'SPIN_OFF_REVERSE_CONFIRM', () => {
      this.securityActionService
        .reverseSecurityAction(id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'REVERSE_SPIN_OFF_SUCCESS');
          this.clearSelectionAndReload();
        });
    });
  }

  private clearSelectionAndReload(): void {
    this.selectedNode = null;
    this.tableSelection = null;
    this.resetMenu();
    this.readData();
  }

  handleCloseCreateDialog(processedActionData: ProcessedActionData): void {
    this.visibleCreateDialog.set(false);
    if (
      processedActionData?.action === ProcessedAction.CREATED ||
      processedActionData?.action === ProcessedAction.UPDATED
    ) {
      this.selectedNode = null;
      this.tableSelection = null;
      this.resetMenu();
      this.readData();
    }
  }

  private applyIsinChange(): void {
    const action: SecurityAction | undefined = this.selectedNode?.data.entity;
    const actionId = action?.idSecurityAction;
    if (actionId == null) return;
    this.securityActionService.applySecurityAction(actionId).subscribe((application) => {
      if ((application?.alreadyPerformedCount ?? 0) > 0) {
        this.messageToastService.showMessageI18n(InfoLevelType.INFO, 'APPLY_ISIN_CHANGE_ALREADY_PERFORMED', {
          count: application.alreadyPerformedCount
        });
      } else {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'APPLY_ISIN_CHANGE_SUCCESS');
      }
      this.readData();
    });
  }

  private reverseIsinChange(): void {
    const action: SecurityAction | undefined = this.selectedNode?.data.entity;
    const actionId = action?.idSecurityAction;
    if (actionId == null) return;
    this.securityActionService.reverseSecurityAction(actionId).subscribe(() => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'REVERSE_ISIN_CHANGE_SUCCESS');
      this.readData();
    });
  }

  private deleteAction(): void {
    const action: SecurityAction | undefined = this.selectedNode?.data.entity;
    const actionId = action?.idSecurityAction;
    if (actionId == null) return;
    this.securityActionService.deleteSecurityAction(actionId).subscribe(() => {
      this.selectedNode = null;
      this.readData();
    });
  }

  private reverseTransfer(): void {
    const transfer: SecurityTransfer | undefined = this.selectedNode?.data.entity;
    if (!transfer) return;
    this.securityActionService.reverseTransfer(transfer.idSecurityTransfer).subscribe(() => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'REVERSE_TRANSFER_SUCCESS');
      this.selectedNode = null;
      this.readData();
    });
  }
}
