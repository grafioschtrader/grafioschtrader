import {Injectable} from '@angular/core';
import {Observable, of} from 'rxjs';
import {MenuItem, TreeNode} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {MainTreeContributor} from '../../lib/maintree/contributor/main-tree-contributor.interface';
import {TreeNodeType} from '../maintree/types/tree.node.type';
import {TypeNodeData} from '../../lib/maintree/types/type.node.data';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {AppSettings} from '../app.settings';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {LibDataMainTreeContributor} from '../../lib/maintree/contributor/lib-data-main-tree.contributor';
import {BaseSettings} from '../../lib/base.settings';

/**
 * Contributor for Admin Data nodes (Trading Calendar, Global Settings, etc.)
 * These nodes are available to admin users.
 */
@Injectable()
export class AdminDataMainTreeContributor extends MainTreeContributor {

  private rootNode: TreeNode;

  constructor(
    private translateService: TranslateService,
    private globalParamService: GlobalparameterService
  ) {
    super();
  }

  getTreeOrder(): number {
    return 4; // AdminData is last
  }

  override isEnabled(): boolean {
    // Admin data is always enabled but visibility is controlled by content
    return true;
  }

  getRootNodes(): Observable<TreeNode[]> {
    this.rootNode = {
      label: 'ADMIN_DATA',
      expanded: true,
      children: [],
      data: new TypeNodeData(
        TreeNodeType.AdminDataRoot,
        this.addMainRoute(AppSettings.USER_MESSAGE_KEY),
        null,
        null
      )
    };
    this.addAdminDataChildren();
    this.setLangTransNode(this.rootNode);
    return of([this.rootNode]);
  }

  refreshNodes(rootNode: TreeNode): Observable<void> {
    // Admin data is mostly static, no refresh needed
    return of(void 0);
  }

  getContextMenuItems(treeNode: TreeNode, parentNodeData: any, selectedNodeData: any): MenuItem[] | null {
    // Admin data nodes don't have context menus
    return null;
  }

  shouldRefreshOnDataChange(processedActionData: ProcessedActionData): boolean {
    // Admin data doesn't need to refresh on data changes
    return false;
  }

  // Private helper methods

  private addAdminDataChildren(): void {
    this.rootNode.children = [];

    this.rootNode.children.push({
      label: 'TRADING_CALENDAR_GLOBAL',
      data: new TypeNodeData(
        TreeNodeType.NO_MENU,
        this.addMainRoute(AppSettings.TRADING_CALENDAR_GLOBAL_KEY),
        null,
        null,
        null
      )
    });

    this.rootNode.children.push({
      label: 'SECURITY_HISTORY_QUALITY',
      data: new TypeNodeData(
        TreeNodeType.NO_MENU,
        this.addMainRoute(AppSettings.SECURITY_HISTORY_QUALITY_KEY),
        null,
        null,
        null
      )
    });

    // Global Settings node defined in lib contributor
    this.rootNode.children.push(LibDataMainTreeContributor.createGlobalSettingsNode());

    // GTNet peer-to-peer network for data sharing between Grafioschtrader instances
    this.addGTNetToTree();

    // Task Data Monitor node defined in lib contributor
    this.rootNode.children.push(LibDataMainTreeContributor.createTaskDataMonitorNode());

    // Admin-only nodes defined in lib contributor
    if (AuditHelper.hasAdminRole(this.globalParamService)) {
      this.rootNode.children.push(LibDataMainTreeContributor.createConnectorApiKeyNode());
      this.rootNode.children.push(LibDataMainTreeContributor.createUserSettingsNode());
    }
  }

  private addGTNetToTree(): void {
    // Only add GTNet if feature is enabled
    if (!this.globalParamService.useGtnet()) {
      return;
    }
    const gtNetNode: TreeNode = {
      expanded: true,
      children: [],
      label: 'GT_NET_NET_AND_MESSAGE',
      data: new TypeNodeData(
        TreeNodeType.NO_MENU,
        this.addMainRoute(AppSettings.GT_NET_TAB_MENU_KEY),
        null,
        null,
        null
      )
    };

    gtNetNode.children.push({
      label: 'GT_NET_MESSAGE_ANSWER',
      data: new TypeNodeData(
        TreeNodeType.NO_MENU,
        this.addMainRoute(AppSettings.GT_NET_MESSAGE_ANSWER_KEY),
        null,
        null,
        null
      )
    });

    gtNetNode.children.push({
      label: 'GT_NET_EXCHANGE',
      data: new TypeNodeData(
        TreeNodeType.NO_MENU,
        this.addMainRoute(AppSettings.GT_NET_EXCHANGE_KEY),
        null,
        null,
        null
      )});

    gtNetNode.children.push({
      label: 'GT_NET_EXCHANGE_LOG',
      data: new TypeNodeData(
        TreeNodeType.NO_MENU,
        this.addMainRoute(AppSettings.GT_NET_EXCHANGE_LOG_KEY),
        null,
        null,
        null
      )
    });

    this.rootNode.children.push(gtNetNode);
  }

  private setLangTransNode(treeNode: TreeNode): void {
    this.setLangTrans(treeNode.label, treeNode);
    if (treeNode.children) {
      this.setLangTransNodes(treeNode.children);
    }
  }

  private setLangTransNodes(treeNodes: TreeNode[]): void {
    treeNodes.forEach(treeNode => this.setLangTransNode(treeNode));
  }

  private setLangTrans(key: string, target: TreeNode, suffix: string = ''): void {
    this.translateService.get(key).subscribe(translated => target.label = translated + suffix);
  }

  private addMainRoute(suffix: string): string {
    return BaseSettings.MAINVIEW_KEY + '/' + suffix;
  }
}
