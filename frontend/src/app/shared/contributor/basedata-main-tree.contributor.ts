import {Injectable} from '@angular/core';
import {Observable, of} from 'rxjs';
import {MenuItem, TreeNode} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {MainTreeContributor} from '../../lib/maintree/contributor/main-tree-contributor.interface';
import {TreeNodeType} from '../maintree/types/tree.node.type';
import {TypeNodeData} from '../../lib/maintree/types/type.node.data';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {AppSettings} from '../app.settings';
import {AppHelper} from '../../lib/helper/app.helper';
import {LibDataMainTreeContributor} from '../../lib/maintree/contributor/lib-data-main-tree.contributor';
import {BaseSettings} from '../../lib/base.settings';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';

/**
 * Contributor for Base Data nodes (AssetClass, StockExchange, etc.)
 * These are mostly static configuration nodes.
 */
@Injectable()
export class BaseDataMainTreeContributor extends MainTreeContributor {

  private rootNode: TreeNode;

  constructor(private translateService: TranslateService, private globalParamService: GlobalparameterService) {
    super();
  }

  getTreeOrder(): number {
    return 3; // BaseData comes after Watchlist
  }

  getRootNodes(): Observable<TreeNode[]> {
    this.rootNode = {
      label: 'BASE_DATA_PROPOSECHANGEENTITY',
      expanded: true,
      children: [],
      data: new TypeNodeData(
        TreeNodeType.BaseDataRoot,
        this.addMainRoute(AppSettings.PROPOSE_CHANGE_TAB_MENU_KEY),
        null,
        null
      )
    };
    this.addBaseDataChildren();
    this.setLangTransNode(this.rootNode);
    return of([this.rootNode]);
  }

  refreshNodes(rootNode: TreeNode): Observable<void> {
    // Base data is mostly static, no refresh needed
    return of(void 0);
  }

  getContextMenuItems(treeNode: TreeNode, parentNodeData: any, selectedNodeData: any): MenuItem[] | null {
    // Base data nodes don't have context menus
    return null;
  }

  shouldRefreshOnDataChange(processedActionData: ProcessedActionData): boolean {
    // Base data doesn't need to refresh on data changes
    return false;
  }

  // Private helper methods

  private addBaseDataChildren(): void {
    // Get UDF Metadata General node from lib contributor
    // Pass the application-specific node type for UDF metadata
    const udfMetadataGeneralNode = LibDataMainTreeContributor.createUdfMetadataGeneralNode(TreeNodeType.UDFMetadataSecurity);
    // Add grafioschtrader-specific child node
    udfMetadataGeneralNode.expanded = true;
    udfMetadataGeneralNode.children = [this.getUDFMetadataSecurityChild()];

    this.rootNode.children = [
      {
        label: AppSettings.ASSETCLASS.toUpperCase(),
        data: new TypeNodeData(
          TreeNodeType.AssetClass,
          this.addMainRoute(AppSettings.ASSETCLASS_KEY),
          null,
          null,
          null
        )
      },
      {
        label: AppSettings.STOCKEXCHANGE.toUpperCase(),
        data: new TypeNodeData(
          TreeNodeType.Stockexchange,
          this.addMainRoute(AppSettings.STOCKEXCHANGE_KEY),
          null,
          null,
          null
        )
      },
      {
        label: AppHelper.toUpperCaseWithUnderscore(AppSettings.TRADING_PLATFORM_PLAN),
        data: new TypeNodeData(
          TreeNodeType.TradingPlatformPlan,
          this.addMainRoute(AppSettings.TRADING_PLATFORM_PLAN_KEY),
          null,
          null,
          null
        )
      },
      {
        label: 'IMPORT_TRANSACTION_PLATFORM',
        data: new TypeNodeData(
          TreeNodeType.ImpTransTemplate,
          this.addMainRoute(AppSettings.IMP_TRANS_TEMPLATE_KEY),
          null,
          null,
          null
        )
      },
      udfMetadataGeneralNode
    ];
    if (this.globalParamService.useGtnet()) {
      this.rootNode.children.push({
        label: 'GTNET_SECURITY_IMPORT',
        data: new TypeNodeData(
          TreeNodeType.GTNetSecurityImport,
          this.addMainRoute(AppSettings.GT_NET_SECURITY_IMPORT_KEY),
          null,
          null,
          null
        )
      });
    }
  }

  private getUDFMetadataSecurityChild(): TreeNode {
    return {
      label: AppHelper.toUpperCaseWithUnderscore(AppSettings.UDF_METADATA_SECURITY),
      data: new TypeNodeData(
        TreeNodeType.UDFMetadataSecurity,
        this.addMainRoute(AppSettings.UDF_METADATA_SECURITY_KEY),
        null,
        null,
        null
      )
    };
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
