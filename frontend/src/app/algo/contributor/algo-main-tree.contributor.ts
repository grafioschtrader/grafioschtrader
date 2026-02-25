import {Injectable} from '@angular/core';
import {Observable, of, forkJoin} from 'rxjs';
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
import {AppSettings} from '../../shared/app.settings';
import {AppHelper} from '../../lib/helper/app.helper';
import {BaseSettings} from '../../lib/base.settings';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {GlobalSessionNames} from '../../lib/global.session.names';
import {FeatureType} from '../../lib/login/model/configuration-with-login';
import {AlgoRuleStrategyCreateDynamicComponent} from '../component/algo.rule.strategy.create.component';
import {AlgoCreateFromPortfolioDynamicComponent} from '../component/algo-create-from-portfolio.component';
import {AlgoTopCreateFromPortfolio} from '../../entities/backend/algo.top.create';
import {TenantService} from '../../tenant/service/tenant.service';
import {SimulationTenantInfo} from '../model/simulation.tenant';
import {AlgoSimulationCreateDynamicComponent} from '../component/algo-simulation-create.component';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {Cashaccount} from '../../entities/cashaccount';

/**
 * Contributor for Algo (algorithmic trading) nodes in the main navigation tree.
 * This contributor is conditionally enabled based on feature flags.
 */
@Injectable()
export class AlgoMainTreeContributor extends MainTreeContributor {

  private rootNode: TreeNode;
  private simulationTenants: SimulationTenantInfo[] = [];

  constructor(
    private algoTopService: AlgoTopService,
    private tenantService: TenantService,
    private portfolioService: PortfolioService,
    private messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
    private translateService: TranslateService
  ) {
    super();
  }

  getTreeOrder(): number {
    return 2; // Algo comes after Watchlist
  }

  override isEnabled(): boolean {
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

    // If we're in a simulation tenant, skip loading simulations (we're already inside one)
    if (this.isInSimulation()) {
      return this.algoTopService.getAlgoTopByIdTenantOrderByName().pipe(
        map(algoTopList => {
          rootNode.children.splice(0);
          for (const algoTop of algoTopList) {
            rootNode.children.push(this.createStrategyNode(algoTop));
          }
        })
      );
    }

    return forkJoin([
      this.algoTopService.getAlgoTopByIdTenantOrderByName(),
      this.tenantService.getSimulationTenants()
    ]).pipe(
      map(([algoTopList, simTenants]) => {
        this.simulationTenants = simTenants;
        rootNode.children.splice(0);
        for (const algoTop of algoTopList) {
          const treeNode = this.createStrategyNode(algoTop);

          // Add simulation child nodes for this strategy
          const sims = simTenants.filter(s => s.idAlgoTop === algoTop.idAlgoAssetclassSecurity);
          if (sims.length > 0) {
            treeNode.icon = 'pi pi-desktop';
            treeNode.children = [];
            for (const sim of sims) {
              treeNode.children.push({
                label: sim.tenantName,
                icon: 'pi pi-box',
                data: new TypeNodeData(
                  TreeNodeType.SimulationEnvironment,
                  this.addMainRoute(AppSettings.STRATEGY_OVERVIEW_KEY),
                  sim.idTenant,
                  null,
                  JSON.stringify(sim)
                )
              });
            }
          }

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
        if (this.isInSimulation()) {
          menuItems.push({
            label: 'SWITCH_TO_MAIN',
            command: () => this.switchToMainTenant()
          });
        } else {
          menuItems.push({
            label: 'CREATE|ALGO_PORTFOLIO_STRATEGY' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () => this.callbacks?.handleEdit(AlgoRuleStrategyCreateDynamicComponent, null,
              new AlgoTopCreate(), 'ALGO_PORTFOLIO_STRATEGY')
              ?.subscribe(result => {
                if (result) {
                  this.callbacks?.refreshTree();
                }
              })
          });
          menuItems.push({
            label: 'CREATE_STRATEGY_FROM_PORTFOLIO' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () => this.callbacks?.handleEdit(AlgoCreateFromPortfolioDynamicComponent, null,
              new AlgoTopCreateFromPortfolio(), 'CREATE_STRATEGY_FROM_PORTFOLIO')
              ?.subscribe(result => {
                if (result) {
                  this.callbacks?.refreshTree();
                }
              })
          });
        }
        break;

      case TreeNodeType.Strategy:
        if (!this.isInSimulation()) {
          menuItems.push({
            label: 'CREATE_SIMULATION' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () => {
              const algoTop: AlgoTop = JSON.parse(typeNodeData.entityObject);
              this.portfolioService.getPortfoliosForTenantOrderByName().subscribe(portfolios => {
                const cashAccounts: Cashaccount[] = [];
                for (const portfolio of portfolios) {
                  if (portfolio.cashaccountList) {
                    cashAccounts.push(...portfolio.cashaccountList);
                  }
                }
                this.callbacks?.handleEdit(AlgoSimulationCreateDynamicComponent, {cashAccounts},
                  algoTop, 'CREATE_SIMULATION')
                  ?.subscribe(result => {
                    if (result) {
                      this.callbacks?.refreshTree();
                    }
                  });
              });
            }
          });
        }
        menuItems.push({
          label: 'DELETE|STRATEGY',
          command: () => this.handleDeleteStrategy(treeNode, selectedNodeData.idAlgoAssetclassSecurity)
        });
        break;

      case TreeNodeType.SimulationEnvironment:
        menuItems.push({
          label: 'SWITCH_TO_SIMULATION',
          command: () => {
            const sim: SimulationTenantInfo = JSON.parse(typeNodeData.entityObject);
            this.switchToSimulationTenant(sim.idTenant);
          }
        });
        menuItems.push({
          label: 'DELETE_SIMULATION',
          command: () => {
            const sim: SimulationTenantInfo = JSON.parse(typeNodeData.entityObject);
            this.handleDeleteSimulation(treeNode, sim.idTenant);
          }
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

  private createStrategyNode(algoTop: AlgoTop): TreeNode {
    return {
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
  }

  private isInSimulation(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.MAIN_ID_TENANT) != null;
  }

  private switchToSimulationTenant(simIdTenant: number): void {
    // Save main tenant ID if not already saved
    if (!sessionStorage.getItem(GlobalSessionNames.MAIN_ID_TENANT)) {
      sessionStorage.setItem(GlobalSessionNames.MAIN_ID_TENANT,
        sessionStorage.getItem(GlobalSessionNames.ID_TENANT));
    }

    this.tenantService.switchTenant(simIdTenant).subscribe({
      next: (response) => {
        sessionStorage.setItem(GlobalSessionNames.JWT, response.token);
        sessionStorage.setItem(GlobalSessionNames.ID_TENANT, simIdTenant.toString());
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'SIMULATION_SWITCHED');
        this.callbacks?.refreshTree();
      },
      error: err => console.error('Error switching to simulation:', err)
    });
  }

  private switchToMainTenant(): void {
    const mainIdTenant = sessionStorage.getItem(GlobalSessionNames.MAIN_ID_TENANT);
    if (!mainIdTenant) {
      return;
    }

    this.tenantService.switchTenant(parseInt(mainIdTenant, 10)).subscribe({
      next: (response) => {
        sessionStorage.setItem(GlobalSessionNames.JWT, response.token);
        sessionStorage.setItem(GlobalSessionNames.ID_TENANT, mainIdTenant);
        sessionStorage.removeItem(GlobalSessionNames.MAIN_ID_TENANT);
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MAIN_TENANT_SWITCHED');
        this.callbacks?.refreshTree();
      },
      error: err => console.error('Error switching to main tenant:', err)
    });
  }

  private handleDeleteSimulation(treeNode: TreeNode, idSimTenant: number): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|SIMULATION_ENVIRONMENT',
      () => {
        this.tenantService.deleteSimulationTenant(idSimTenant).subscribe({
          next: () => {
            this.messageToastService.showMessageI18n(
              InfoLevelType.SUCCESS,
              'MSG_DELETE_RECORD',
              {i18nRecord: 'SIMULATION_ENVIRONMENT'}
            );
            this.callbacks?.refreshTree();
          },
          error: err => console.error('Error deleting simulation:', err)
        });
      }
    );
  }

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
