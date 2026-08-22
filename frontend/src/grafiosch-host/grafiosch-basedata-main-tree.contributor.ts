import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { TreeNode } from '@openng/optimus-ui/api';
import { Observable, of } from 'rxjs';

import { BaseSettings } from '../app/lib/base.settings';
import { LibDataMainTreeContributor } from '../app/lib/maintree/contributor/lib-data-main-tree.contributor';
import { LibTreeNodeType } from '../app/lib/maintree/types/lib.tree.node.type';
import { TypeNodeData } from '../app/lib/maintree/types/type.node.data';
import { GrafioschSettings } from './grafiosch.settings';
import { GrafioschTreeContributorBase } from './grafiosch-tree-contributor.base';

/**
 * The base data root of this host, the counterpart of Grafioschtrader's {@code BaseDataMainTreeContributor} reduced to
 * the nodes the reusable library brings along. Grafioschtrader fills the same root mainly with its own views — asset
 * classes, stock exchanges, trading platforms — of which nothing exists in a standalone Grafiosch server.
 *
 * <p>It exists next to {@link GrafioschMainTreeContributor} rather than being folded into it, because the host should
 * exercise what an application actually does: several contributors, merged by {@code MainTreeService} in the order of
 * {@link getTreeOrder}.</p>
 */
@Injectable()
export class GrafioschBaseDataMainTreeContributor extends GrafioschTreeContributorBase {
  constructor(translateService: TranslateService) {
    super(translateService);
  }

  getTreeOrder(): number {
    return 1;
  }

  getRootNodes(): Observable<TreeNode[]> {
    const rootNode: TreeNode = {
      label: 'BASE_DATA_PROPOSECHANGEENTITY',
      expanded: true,
      children: [
        this.navigationNode(
          'PROPOSE_CHANGE_ENTITY',
          GrafioschSettings.PROPOSE_CHANGE_TAB_MENU_KEY + '/' + BaseSettings.PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY
        ),
        LibDataMainTreeContributor.createUdfMetadataGeneralNode(LibTreeNodeType.NO_MENU)
      ],
      data: new TypeNodeData(
        LibTreeNodeType.NO_MENU,
        this.addMainRoute(GrafioschSettings.PROPOSE_CHANGE_TAB_MENU_KEY),
        null,
        null
      )
    };
    this.translateNodes([rootNode]);
    return of([rootNode]);
  }
}
