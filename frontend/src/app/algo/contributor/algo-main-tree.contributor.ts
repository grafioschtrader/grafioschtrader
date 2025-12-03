import {Injectable} from '@angular/core';
import {Observable, of} from 'rxjs';
import {map} from 'rxjs/operators';
import {MenuItem, TreeNode, ConfirmationService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {MainTreeContributor} from '../../lib/maintree/contributor/main-tree-contributor.interface';
import {TreeNodeType} from '../../shared/maintree/types/tree.node.type';
import {TypeNodeData} from '../../lib/maintree/types/type.node.data';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {AlgoTopService} from '../service/algo.top.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {AlgoTop} from '../model/algo.top';
import {AlgoTopCreate} from '../../entities/backend/algo.top.create';
import {RuleStrategyType} from '../../shared/types/rule.strategy.type';
import {AppSettings} from '../../shared/app.settings';
import {AppHelper} from '../../lib/helper/app.helper';
import {BaseSettings} from '../../lib/base.settings';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {GlobalSessionNames} from '../../lib/global.session.names';
import {FeatureType} from '../../lib/login/model/configuration-with-login';
import {AlgoRuleStrategyCreateDynamicComponent} from '../component/algo.rule.strategy.create.component';

/**
 * Contributor for Algo (algorithmic trading) nodes in the main navigation tree.
 * This contributor is conditionally enabled based on feature flags.
 */
@Injectable()
export class AlgoMainTreeContributor extends MainTreeContributor {

  private rootNode: TreeNode;

  constructor(
    private algoTopService: AlgoTopService,
    private messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
    private translateService: TranslateService
  ) {
    super();
  }

  getTreeOrder(): number {
    return 1; // Algo comes between Portfolio and Watchlist
  }

  override isEnabled(): boolean {
    // Check if Algo feature is enabled
    const features = sessionStorage.getItem(GlobalSessionNames.USE_FEATURES);
    if (!features) {
      return false;
    }
    return JSON.parse(features).indexOf(FeatureType[FeatureType.ALGO]) >= 0;
  }

  getRootNodes(): Observable<TreeNode[]> {
    this.rootNode = {
      expanded: true,
      children: [],
      data: new TypeNodeData(
        TreeNodeType.AlgoRoot,
        this.addMainRoute(AppSettings.STRATEGY_OVERVIEW_KEY),
        null,
        null
      )
    };
    this.setLangTrans('ALGO_OVERVIEW', this.rootNode);
    return of([this.rootNode]);
  }

  refreshNodes(rootNode: TreeNode): Observable<void> {
    this.rootNode = rootNode;

    return this.algoTopService.getAlgoTopByIdTenantOrderByName().pipe(
      map(algoTopList => {
        rootNode.children.splice(0);
        for (const algoTop of algoTopList) {
          const treeNode = {
            label: algoTop.name,
            icon: 'pi ' + (algoTop.activatable ? 'pi-check-circle' : 'pi-question'),
            data: new TypeNodeData(
              TreeNodeType.Strategy,
              this.addMainRoute(AppSettings.ALGO_TOP_KEY),
              algoTop.idAlgoAssetclassSecurity,
              null,
              JSON.stringify(algoTop)
            )
          };
          rootNode.children.push(treeNode);
        }
      })
    );
  }

  getContextMenuItems(treeNode: TreeNode, parentNodeData: any, selectedNodeData: any): MenuItem[] | null {
    const typeNodeData = treeNode.data;
    const menuItems: MenuItem[] = [];

    switch (typeNodeData.treeNodeType) {
      case TreeNodeType.AlgoRoot:
        menuItems.push({
          label: 'CREATE|ALGO_PORTFOLIO_STRATEGY' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => this.callbacks?.handleEdit(AlgoRuleStrategyCreateDynamicComponent, null,
            new AlgoTopCreate(RuleStrategyType.RS_STRATEGY), 'ALGO_PORTFOLIO_STRATEGY')
            ?.subscribe(result => {
              if (result) {
                this.callbacks?.refreshTree();
              }
            })
        });
        menuItems.push({
          label: 'CREATE|ALGO_RULE_BASED' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => this.callbacks?.handleEdit(AlgoRuleStrategyCreateDynamicComponent, null,
            new AlgoTopCreate(RuleStrategyType.RS_RULE), 'ALGO_RULE_BASED')
            ?.subscribe(result => {
              if (result) {
                this.callbacks?.refreshTree();
              }
            })
        });
        break;

      case TreeNodeType.Strategy:
        menuItems.push({
          label: 'DELETE|STRATEGY',
          command: () => this.handleDeleteStrategy(treeNode, selectedNodeData.idAlgoAssetclassSecurity)
        });
        break;

      default:
        return null;
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems.length > 0 ? menuItems : null;
  }

  shouldRefreshOnDataChange(processedActionData: ProcessedActionData): boolean {
    return processedActionData.data instanceof AlgoTop;
  }

  override handleDelete(treeNode: TreeNode, id: number): Observable<any> | null {
    if (treeNode.data.treeNodeType === TreeNodeType.Strategy) {
      this.handleDeleteStrategy(treeNode, id);
    }
    return null;
  }

  // Private helper methods

  private handleDeleteStrategy(treeNode: TreeNode, idAlgoAssetclassSecurity: number): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|STRATEGY',
      () => {
        this.algoTopService.deleteEntity(idAlgoAssetclassSecurity).subscribe({
          next: () => {
            this.messageToastService.showMessageI18n(
              InfoLevelType.SUCCESS,
              'MSG_DELETE_RECORD',
              {i18nRecord: 'STRATEGY'}
            );
            this.callbacks?.navigateToNode(this.getPreviousNode(treeNode).data);
            this.callbacks?.refreshTree();
          },
          error: err => console.error('Error deleting strategy:', err)
        });
      }
    );
  }

  private getPreviousNode(treeNode: TreeNode): TreeNode {
    let i = 0;
    while (i < treeNode.parent.children.length && treeNode.parent.children[i] !== treeNode) {
      i++;
    }
    return i === 0 ? treeNode.parent : treeNode.parent.children[i - 1];
  }

  private setLangTrans(key: string, target: TreeNode, suffix: string = ''): void {
    this.translateService.get(key).subscribe(translated => target.label = translated + suffix);
  }

  private addMainRoute(suffix: string): string {
    return BaseSettings.MAINVIEW_KEY + '/' + suffix;
  }
}
