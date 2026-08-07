import {Injectable} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {MenuItem, TreeNode} from 'primeng/api';
import {Observable, of} from 'rxjs';

import {BaseSettings} from '../app/lib/base.settings';
import {AuditHelper} from '../app/lib/helper/audit.helper';
import {LibDataMainTreeContributor} from '../app/lib/maintree/contributor/lib-data-main-tree.contributor';
import {MainTreeContributor} from '../app/lib/maintree/contributor/main-tree-contributor.interface';
import {LibTreeNodeType} from '../app/lib/maintree/types/lib.tree.node.type';
import {TypeNodeData} from '../app/lib/maintree/types/type.node.data';
import {ProcessedActionData} from '../app/lib/types/processed.action.data';
import {GlobalparameterService} from '../app/lib/services/globalparameter.service';
import {GrafioschSettings} from './grafiosch.settings';

/**
 * The single navigation tree of this host, built from the nodes the library already offers through
 * {@link LibDataMainTreeContributor}. It is the counterpart of Grafioschtrader's {@code AdminDataMainTreeContributor},
 * reduced to what a standalone Grafiosch server can actually serve.
 *
 * <p>An application supplies at least one contributor, otherwise {@code /mainview} renders an empty tree. Nothing here
 * refreshes on data changes: every node is static navigation.</p>
 */
@Injectable()
export class GrafioschMainTreeContributor extends MainTreeContributor {

  private rootNode: TreeNode;

  constructor(private translateService: TranslateService, private gps: GlobalparameterService) {
    super();
  }

  getTreeOrder(): number {
    return 1;
  }

  getRootNodes(): Observable<TreeNode[]> {
    this.rootNode = {
      label: 'ADMIN_DATA',
      expanded: true,
      children: [],
      data: new TypeNodeData(LibTreeNodeType.NO_MENU, this.addMainRoute(GrafioschSettings.USER_MESSAGE_KEY), null, null)
    };
    this.addChildren();
    this.translateNodes([this.rootNode]);
    return of([this.rootNode]);
  }

  refreshNodes(rootNode: TreeNode): Observable<void> {
    return of(void 0);
  }

  getContextMenuItems(treeNode: TreeNode, parentNodeData: any, selectedNodeData: any): MenuItem[] | null {
    return null;
  }

  shouldRefreshOnDataChange(processedActionData: ProcessedActionData): boolean {
    return false;
  }

  private addChildren(): void {
    this.rootNode.children = [
      this.navigationNode('MAIL_TO_FROM', GrafioschSettings.USER_MESSAGE_KEY + '/' + BaseSettings.MAIL_SEND_RECV_KEY),
      this.navigationNode('PROPOSE_CHANGE_ENTITY', GrafioschSettings.PROPOSE_CHANGE_TAB_MENU_KEY + '/'
        + BaseSettings.PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY),
      LibDataMainTreeContributor.createUdfMetadataGeneralNode(LibTreeNodeType.NO_MENU),
      LibDataMainTreeContributor.createGlobalSettingsNode(),
      LibDataMainTreeContributor.createTaskDataMonitorNode()
    ];

    if (AuditHelper.hasAdminRole(this.gps)) {
      this.rootNode.children.push(LibDataMainTreeContributor.createConnectorApiKeyNode());
      this.rootNode.children.push(LibDataMainTreeContributor.createUserSettingsNode());
    }
  }

  private navigationNode(label: string, route: string): TreeNode {
    return {
      label,
      data: new TypeNodeData(LibTreeNodeType.NO_MENU, this.addMainRoute(route), null, null, null)
    };
  }

  private translateNodes(treeNodes: TreeNode[]): void {
    treeNodes.forEach(treeNode => {
      this.translateService.get(treeNode.label).subscribe(translated => treeNode.label = translated);
      if (treeNode.children) {
        this.translateNodes(treeNode.children);
      }
    });
  }

  private addMainRoute(suffix: string): string {
    return BaseSettings.MAINVIEW_KEY + '/' + suffix;
  }
}
