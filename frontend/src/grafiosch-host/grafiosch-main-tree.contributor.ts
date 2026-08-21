import {Injectable} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {TreeNode} from '@openng/optimus-ui/api';
import {Observable, of} from 'rxjs';

import {BaseSettings} from '../app/lib/base.settings';
import {AuditHelper} from '../app/lib/helper/audit.helper';
import {LibDataMainTreeContributor} from '../app/lib/maintree/contributor/lib-data-main-tree.contributor';
import {LibTreeNodeType} from '../app/lib/maintree/types/lib.tree.node.type';
import {TypeNodeData} from '../app/lib/maintree/types/type.node.data';
import {GlobalparameterService} from '../app/lib/services/globalparameter.service';
import {GrafioschSettings} from './grafiosch.settings';
import {GrafioschTreeContributorBase} from './grafiosch-tree-contributor.base';

/**
 * The administrative root of this host, built from the nodes the library already offers through
 * {@link LibDataMainTreeContributor}. It is the counterpart of Grafioschtrader's
 * {@code AdminDataMainTreeContributor}, reduced to what a standalone Grafiosch server can actually serve — the trading
 * calendar, the history quality and the tax data of that tree belong to the application.
 *
 * <p>The base data root comes from {@link GrafioschBaseDataMainTreeContributor}; an application supplies at least one
 * contributor, otherwise {@code /mainview} renders an empty tree.</p>
 */
@Injectable()
export class GrafioschMainTreeContributor extends GrafioschTreeContributorBase {

  private rootNode: TreeNode;

  constructor(translateService: TranslateService, private gps: GlobalparameterService) {
    super(translateService);
  }

  getTreeOrder(): number {
    return 2;
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

  private addChildren(): void {
    this.rootNode.children = [
      this.navigationNode('MAIL_TO_FROM', GrafioschSettings.USER_MESSAGE_KEY + '/' + BaseSettings.MAIL_SEND_RECV_KEY),
      LibDataMainTreeContributor.createGlobalSettingsNode()
    ];

    this.addGTNetToTree();

    this.rootNode.children.push(LibDataMainTreeContributor.createTaskDataMonitorNode());

    if (AuditHelper.hasAdminRole(this.gps)) {
      this.rootNode.children.push(LibDataMainTreeContributor.createConnectorApiKeyNode());
      this.rootNode.children.push(LibDataMainTreeContributor.createUserSettingsNode());
      this.rootNode.children.push(LibDataMainTreeContributor.createEntityLimitNode());
    }
  }

  /**
   * Adds the GTNet branch, the largest area of the library this host reaches. The whole peer-to-peer implementation is
   * library code — the Angular components in {@code src/app/lib/gnet} and the {@code GTNet*Resource} classes of
   * {@code grafiosch-server-base} — so a standalone Grafiosch server offers it in full.
   *
   * <p>The exchange log is shown but not selectable while {@code g.gnet.use.log} is off, so the branch does not change
   * shape when logging is switched on. The parent node targets the setup table, which is where this instance's own
   * GTNet entry is created in the first place — the feature flag therefore must not depend on that entry existing.</p>
   */
  private addGTNetToTree(): void {
    if (!this.gps.useGtnet()) {
      return;
    }
    const logEnabled = this.gps.isGtNetLogEnabled();
    const gtNetNode: TreeNode = {
      label: 'GT_NET_NET_AND_MESSAGE',
      expanded: true,
      children: [
        this.navigationNode('GT_NET_MESSAGE_ANSWER', BaseSettings.GT_NET_MESSAGE_ANSWER_KEY),
        {
          ...this.navigationNode('GT_NET_EXCHANGE_LOG', BaseSettings.GT_NET_EXCHANGE_LOG_KEY),
          styleClass: logEnabled ? '' : 'p-disabled',
          selectable: logEnabled
        }
      ],
      data: new TypeNodeData(LibTreeNodeType.NO_MENU,
        this.addMainRoute(GrafioschSettings.GT_NET_TAB_MENU_KEY), null, null, null)
    };
    this.rootNode.children.push(gtNetNode);
  }
}
