import {Component, OnDestroy, OnInit, ViewChild, Type, Inject} from '@angular/core';
import {Router} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {MenuItem, TreeNode} from 'primeng/api';
import {Subscription} from 'rxjs';
import {MainTreeService} from '../service/main-tree.service';
import {TypeNodeData} from '../types/type.node.data';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../../mainmenubar/component/iglobal.menu.attach';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {DataChangedService} from '../service/data.changed.service';
import {DialogHandler, DIALOG_HANDLER} from '../handler/dialog-handler.interface';
import {HelpIds} from '../../help/help.ids';
import {CommonModule} from '@angular/common';
import {TreeModule} from 'primeng/tree';
import {ContextMenuModule} from 'primeng/contextmenu';
import {ConfirmDialogModule} from 'primeng/confirmdialog';
import {ButtonModule} from 'primeng/button';
import {SharedModule} from 'primeng/api';

/**
 * This is the component for displaying the navigation tree. It is used to control the indicators of the main area.
 */
@Component({
  selector: 'main-tree',
  templateUrl: '../view/maintree.html',
  standalone: true,
  imports: [CommonModule, TreeModule, ContextMenuModule, ConfirmDialogModule, ButtonModule, SharedModule, TranslateModule]
})
export class MainTreeComponent implements OnInit, OnDestroy, IGlobalMenuAttach {
  @ViewChild('cm', {static: true}) contextMenu: any;

  /**
   * Only used to get primeng p-tabmenu working. For example when portfolio is clicked the 2nd time in the navigator, it could produce
   * an empty p-tabmenu.
   */
  lastRoute: string;
  lastId: number;
  portfolioTrees: TreeNode[] = [];
  selectedNode: TreeNode;
  contextMenuItems: MenuItem[];

  private subscription: Subscription;

  constructor(
    private dataChangedService: DataChangedService,
    private activePanelService: ActivePanelService,
    private router: Router,
    public translateService: TranslateService,
    @Inject(DIALOG_HANDLER) private dialogHandler: DialogHandler,
    private mainTreeService: MainTreeService
  ) {
    // Setup component callbacks for the service
    this.mainTreeService.setComponentCallbacks({
      handleEdit: (componentType: Type<any>, parentObject: any, data: any, titleKey: string) =>
        this.dialogHandler.openEditDialog(componentType, parentObject, data, titleKey),
      handleTenantEdit: (data: any, onlyCurrency: boolean) =>
        this.dialogHandler.openTenantDialog(data, onlyCurrency),
      navigateToNode: (data: TypeNodeData) => this.navigateRoute(data),
      refreshTree: () => this.refreshTree()
    });

    this.refreshTreeBecauseOfParentAction();
  }

   ngOnInit(): void {
    // Build the tree using the service
    this.mainTreeService.buildTree().subscribe(trees => {
      this.portfolioTrees = trees;
      // Trigger initial refresh
      this.mainTreeService.refreshAllNodes().subscribe();
    });
  }

  /**
   * Refreshes the entire tree.
   */
  private refreshTree(): void {
    this.mainTreeService.refreshAllNodes().subscribe();
  }

  getEditMenuItemsByTypeNode(treeNode: TreeNode): MenuItem[] {
    // Delegate to the service
    return this.mainTreeService.getContextMenuItems(treeNode);
  }

  onNodeSelect(event) {
    this.nodeSelect(event.node);
  }

  onNodeContextMenuSelect(event) {
    const typeNodeData: TypeNodeData = event.node.data;
    this.contextMenuItems = this.getEditMenuItemsByTypeNode(event.node);
    if (!this.contextMenuItems) {
      this.contextMenu.hide();
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }


  ////////////////////////////////////////////////////////////////////////////////
  // Events

  callMeDeactivate(): void {
  }

  hideContextMenu(): void {
    this.contextMenu.hide();
  }

  onComponentClick(event) {
    this.activePanelService.activatePanel(this,
      {editMenu: (this.selectedNode) ? this.getEditMenuItemsByTypeNode(this.selectedNode) : null});
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

  onKeydown(event) {
    // TODO Should show the content
    if (event.key === 'Enter') {
      console.log(event);
    }
  }

  public dragOver(event: DragEvent, node: TreeNode) {
    const dragData = event.dataTransfer.getData('text/plain');
    if (this.mainTreeService.canDrop(node, dragData)) {
      event.preventDefault();
    }
  }

  public drop(event: DragEvent, treeNode: TreeNode) {
    event.preventDefault();
    const dragData = event.dataTransfer.getData('text/plain');
    const sourceLabel = this.selectedNode?.label;
    this.mainTreeService.handleDrop(treeNode, dragData, sourceLabel);
  }

  private nodeSelect(node: TreeNode) {
    this.selectedNode = node;
    const data: TypeNodeData = this.selectedNode.data;
    this.navigateRoute(data);
  }

  private refreshTreeBecauseOfParentAction(): void {
    this.subscription = this.dataChangedService.dateChanged$.subscribe(processedActionData => {
      // Delegate to the service to refresh appropriate nodes
      this.mainTreeService.refreshNodesForDataChange(processedActionData).subscribe();
    });
  }

  private navigateRoute(data: TypeNodeData) {
    if (data && data.route) {
      if (data.id) {
        if (!this.lastRoute || this.lastRoute !== data.route || this.lastId !== data.id) {
          this.lastRoute = data.route;
          this.lastId = data.id;
          if (data.useQueryParams) {
            this.router.navigate([data.route, data.id], {queryParams: {object: data.entityObject}});
          } else {
            this.router.navigate([data.route, data.id, {object: data.entityObject}]);
          }
        }
      } else {
        this.router.navigate([data.route]);
        this.lastRoute = data.route;
      }
    }
  }

  private clearSelection(): void {
    this.activePanelService.activatePanel(this, {editMenu: null});
    this.refreshTree();
  }

}



