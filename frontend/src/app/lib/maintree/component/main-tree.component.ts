import type { PanelClickEvent } from '../../mainmenubar/component/iglobal.menu.attach';
import { Component, OnDestroy, OnInit, signal, viewChild, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MenuItem, TreeNode } from '@openng/optimus-ui/api';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { MainTreeService } from '../service/main-tree.service';
import { TreeNavigationStateService } from '../service/tree.navigation.state.service';
import { TypeNodeData } from '../types/type.node.data';
import { ActivePanelService } from '../../mainmenubar/service/active.panel.service';
import { IGlobalMenuAttach } from '../../mainmenubar/component/iglobal.menu.attach';
import { ProcessedActionData } from '../../types/processed.action.data';
import { ProcessedAction } from '../../types/processed.action';
import { DataChangedService } from '../service/data.changed.service';
import { DialogHandler, DIALOG_HANDLER } from '../handler/dialog-handler.interface';
import { HelpIds } from '../../help/help.ids';

import { TreeModule } from '@openng/optimus-ui/tree';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { ConfirmDialogModule } from '@openng/optimus-ui/confirmdialog';
import { ButtonModule } from '@openng/optimus-ui/button';
import { SharedModule } from '@openng/optimus-ui/api';
import { DialogComponentLoader } from '../handler/lazy-dialog';

/**
 * This is the component for displaying the navigation tree. It is used to control the indicators of the main area.
 *
 * Important: Optimus Tree uses OnPush change detection strategy. To successfully refresh the tree after data changes,
 * all TreeNode objects must be deep cloned to create new object references. A shallow copy (e.g., [...array]) is not
 * sufficient - the entire node hierarchy must have new references. This is handled by MainTreeService.getClonedPortfolioTrees()
 * which recursively clones all nodes while preserving properties like 'expanded'.
 */
@Component({
  selector: 'main-tree',
  templateUrl: '../view/maintree.html',
  imports: [TreeModule, ContextMenuModule, ConfirmDialogModule, ButtonModule, SharedModule, TranslateModule]
})
export class MainTreeComponent implements OnInit, OnDestroy, IGlobalMenuAttach {
  private dataChangedService = inject(DataChangedService);
  private activePanelService = inject(ActivePanelService);
  private router = inject(Router);
  translateService = inject(TranslateService);
  private dialogHandler = inject<DialogHandler>(DIALOG_HANDLER);
  private mainTreeService = inject(MainTreeService);
  private treeNavState = inject(TreeNavigationStateService);

  readonly contextMenu = viewChild<any>('cm');

  /**
   * Only used to get the Optimus UI p-tabmenu working. For example when portfolio is clicked the 2nd time in the
   * navigator, it could produce
   * an empty p-tabmenu.
   */
  lastRoute: string | null = null;
  lastId: number | null | undefined = null;
  /** The rendered tree; replaced by a deep clone after every refresh, which the async contributor calls trigger */
  readonly portfolioTrees = signal<TreeNode[]>([]);
  selectedNode: TreeNode<TypeNodeData> | null = null;
  readonly contextMenuItems = signal<MenuItem[] | null>(null);

  private subscription: Subscription;

  constructor() {
    // Setup component callbacks for the service
    this.mainTreeService.setComponentCallbacks({
      handleEdit: (loadComponent: DialogComponentLoader, parentObject: any, data: any, titleKey: string) =>
        this.dialogHandler.openEditDialog(loadComponent, parentObject, data, titleKey),
      handleTenantEdit: (data: any, onlyCurrency: boolean) => this.dialogHandler.openTenantDialog(data, onlyCurrency),
      navigateToNode: (data: TypeNodeData) => this.navigateRoute(data),
      refreshTree: () => this.refreshTree()
    });

    this.refreshTreeBecauseOfParentAction();
    this.forgetNodeOnNavigationElsewhere();
  }

  ngOnInit(): void {
    // Build the tree using the service
    this.mainTreeService.buildTree().subscribe((trees) => {
      this.portfolioTrees.set(trees);
      // Trigger initial refresh
      this.mainTreeService.refreshAllNodes().subscribe(() => {
        this.triggerTreeUpdate();
      });
    });
  }

  /**
   * Refreshes the entire tree.
   * Triggers Optimus Tree change detection by updating the value reference and serialized data.
   */
  private refreshTree(): void {
    this.mainTreeService.refreshAllNodes().subscribe(() => {
      this.triggerTreeUpdate();
    });
  }

  /**
   * Triggers Optimus Tree to recognize changes by deep cloning the tree structure.
   * This creates new object references for all nodes, which is required for
   * Optimus Tree's OnPush change detection strategy.
   */
  private triggerTreeUpdate(): void {
    this.portfolioTrees.set(this.mainTreeService.getClonedPortfolioTrees());
  }

  getEditMenuItemsByTypeNode(treeNode: TreeNode): MenuItem[] | null {
    // Delegate to the service
    return this.mainTreeService.getContextMenuItems(treeNode);
  }

  onNodeSelect(event: { node: TreeNode<TypeNodeData> }) {
    this.nodeSelect(event.node);
  }

  onNodeContextMenuSelect(event: { node: TreeNode<TypeNodeData> }) {
    this.contextMenuItems.set(this.getEditMenuItemsByTypeNode(event.node));
    if (!this.contextMenuItems()) {
      this.contextMenu()?.hide();
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  ////////////////////////////////////////////////////////////////////////////////
  // Events

  callMeDeactivate(): void {}

  hideContextMenu(): void {
    this.contextMenu()?.hide();
  }

  onComponentClick(event: PanelClickEvent) {
    this.activePanelService.activatePanel(this, {
      editMenu: this.selectedNode ? this.getEditMenuItemsByTypeNode(this.selectedNode) : null
    });
  }

  handleOnProcessedDialog(processedActionData: ProcessedActionData) {
    if (processedActionData?.action !== ProcessedAction.NO_CHANGE) {
      this.refreshTree();
    }
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_INTRO_NAVIGATION;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  onKeydown(event: { key: string }) {
    // TODO Should show the content
    if (event.key === 'Enter') {
      console.log(event);
    }
  }

  public dragOver(event: DragEvent, node: TreeNode) {
    const transfer = event.dataTransfer;
    if (!transfer) return;
    const dragData = transfer.getData('text/plain');
    const canDrop = this.mainTreeService.canDrop(node, dragData);
    if (canDrop) {
      event.preventDefault();
      event.stopPropagation();
      transfer.dropEffect = 'move';
    } else {
    }
  }

  public drop(event: DragEvent, treeNode: TreeNode) {
    event.preventDefault();
    event.stopPropagation();
    const transfer = event.dataTransfer;
    if (!transfer) return;
    const dragData = transfer.getData('text/plain');
    const sourceLabel = this.selectedNode?.label;
    this.mainTreeService.handleDrop(treeNode, dragData, sourceLabel);
  }

  private nodeSelect(node: TreeNode<TypeNodeData>) {
    this.selectedNode = node;
    this.navigateRoute(node.data);
  }

  /**
   * Drops the memory of the last visited node once the application has routed somewhere else on its own, for instance
   * through a link inside a view. Without this the memory would still name a view that is no longer on screen, and
   * {@link navigateRoute} would take the next click on that very node for a repeated one and stay where it is - the
   * node then has to be clicked twice to come back.
   */
  private forgetNodeOnNavigationElsewhere(): void {
    this.subscription.add(
      this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe((event: NavigationEnd) => {
        const visited = '/' + this.lastRoute + (this.lastId ? '/' + this.lastId : '');
        if (this.lastRoute && !event.urlAfterRedirects.startsWith(visited)) {
          this.lastRoute = null;
          this.lastId = null;
        }
      })
    );
  }

  private refreshTreeBecauseOfParentAction(): void {
    this.subscription = this.dataChangedService.dateChanged$.subscribe((processedActionData) => {
      // Delegate to the service to refresh appropriate nodes
      this.mainTreeService.refreshNodesForDataChange(processedActionData).subscribe(() => {
        this.triggerTreeUpdate();
      });
    });
  }

  private navigateRoute(data: TypeNodeData | null | undefined) {
    if (data && data.route) {
      if (data.id) {
        if (!this.lastRoute || this.lastRoute !== data.route || this.lastId !== data.id) {
          this.lastRoute = data.route;
          this.lastId = data.id;
          if (data.entityObject) {
            this.treeNavState.setEntity(data.route, data.id, JSON.parse(data.entityObject));
          }
          if (data.parentObject) {
            this.treeNavState.setEntity(data.route + '_parent', data.id, JSON.parse(data.parentObject));
          }
          this.router.navigate([data.route, data.id]);
        }
      } else {
        this.router.navigate([data.route]);
        this.lastRoute = data.route;
      }
    }
  }

  private clearSelection(): void {
    this.activePanelService.activatePanel(this, { editMenu: null });
    this.refreshTree();
  }
}
