import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { Injectable } from '@angular/core';
import { Observable, of, forkJoin } from 'rxjs';
import { map } from 'rxjs/operators';
import { MenuItem, TreeNode, ConfirmationService } from '@openng/optimus-ui/api';
import { TranslateService } from '@ngx-translate/core';
import { MainTreeContributor } from '../../lib/maintree/contributor/main-tree-contributor.interface';
import { TreeNodeType } from '../../shared/maintree/types/tree.node.type';
import { TypeNodeData } from '../../lib/maintree/types/type.node.data';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { AlgoTopService } from '../service/algo.top.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { AlgoTop } from '../model/algo.top';
import { AlgoTopCreate } from '../../entities/backend/algo.top.create';
import { AppSettings } from '../../shared/app.settings';
import { AppHelper } from '../../lib/helper/app.helper';
import { BaseSettings } from '../../lib/base.settings';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { GlobalSessionNames } from '../../lib/global.session.names';
import { FeatureType } from '../../lib/login/model/configuration-with-login';
import { AlgoRuleStrategyCreateDynamicComponent } from '../component/algo.rule.strategy.create.component';
import { AlgoCreateFromPortfolioDynamicComponent } from '../component/algo-create-from-portfolio.component';
import { AlgoCreateFromWatchlistDynamicComponent } from '../component/algo-create-from-watchlist.component';
import { AlgoTopCreateFromPortfolio, AlgoTopCreateFromWatchlist } from '../../entities/backend/algo.top.create';
import { TenantService } from '../../tenant/service/tenant.service';
import { ManageClientService } from '../../lib/manageclient/service/manage-client.service';
import { SimulationContextService } from '../service/simulation.context.service';
import { SimulationTenantInfo } from '../model/simulation.tenant';
import { AlgoSimulationCreateDynamicComponent } from '../component/algo-simulation-create.component';
import { AlgoSimulationRunStartDynamicComponent } from '../component/algo-simulation-run-start.component';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { Cashaccount } from '../../entities/cashaccount';

/**
 * Contributor for Algo (algorithmic trading) nodes in the main navigation tree.
 * This contributor is conditionally enabled based on feature flags.
 */
@Injectable()
export class AlgoMainTreeContributor extends MainTreeContributor {
  private rootNode: TreeNode;
  private simulationTenants: SimulationTenantInfo[] = [];

  constructor(
    private gps: GlobalparameterService,
    private algoTopService: AlgoTopService,
    private tenantService: TenantService,
    private manageClientService: ManageClientService,
    private simulationContext: SimulationContextService,
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
      data: new TypeNodeData(TreeNodeType.AlgoRoot, this.addMainRoute(AppSettings.ALGO_OVERVIEW_KEY), null, null)
    };
    this.setLangTrans('ALGO_OVERVIEW', this.rootNode);
    return of([this.rootNode]);
  }

  refreshNodes(rootNode: TreeNode): Observable<void> {
    this.rootNode = rootNode;

    // If we're in a simulation tenant, skip loading simulations (we're already inside one)
    if (this.isInSimulation()) {
      return this.algoTopService.getAlgoTopByIdTenantOrderByName().pipe(
        map((algoTopList) => {
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
          const sims = simTenants.filter((s) => s.idAlgoTop === algoTop.idAlgoAssetclassSecurity);
          if (sims.length > 0) {
            treeNode.icon = 'pi pi-desktop';
            treeNode.children = [];
            for (const sim of sims) {
              treeNode.children.push({
                label: `${sim.tenantName} — ${sim.simulationStartDate ?? this.translateService.instant('SIMULATION_RECREATE_REQUIRED')}${sim.initializationMode ? ' / ' + this.translateService.instant(sim.initializationMode) : ''}`,
                icon: 'pi pi-box',
                data: new TypeNodeData(
                  TreeNodeType.SimulationEnvironment,
                  this.addMainRoute(AppSettings.SIMULATION_RUN_KEY),
                  // The replay panel addresses the environment by its tenant, so the node carries that id into the
                  // route. The serialized entity stays: the switch and delete actions of the context menu read it.
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
        } else if (!this.gps.isReadOnlyUser()) {
          menuItems.push({
            label: 'CREATE|ALGO_PORTFOLIO_STRATEGY' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () =>
              this.callbacks
                ?.handleEdit(
                  AlgoRuleStrategyCreateDynamicComponent,
                  null,
                  new AlgoTopCreate(),
                  'ALGO_PORTFOLIO_STRATEGY'
                )
                ?.subscribe((result) => {
                  if (result) {
                    this.callbacks?.refreshTree();
                  }
                })
          });
          menuItems.push({
            label: 'CREATE_STRATEGY_FROM_PORTFOLIO' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () =>
              this.callbacks
                ?.handleEdit(
                  AlgoCreateFromPortfolioDynamicComponent,
                  null,
                  new AlgoTopCreateFromPortfolio(),
                  'CREATE_STRATEGY_FROM_PORTFOLIO'
                )
                ?.subscribe((result) => {
                  if (result) {
                    this.callbacks?.refreshTree();
                  }
                })
          });
          menuItems.push({
            label: 'CREATE_STRATEGY_FROM_WATCHLIST' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () =>
              this.callbacks
                ?.handleEdit(
                  AlgoCreateFromWatchlistDynamicComponent,
                  null,
                  new AlgoTopCreateFromWatchlist(),
                  'CREATE_STRATEGY_FROM_WATCHLIST'
                )
                ?.subscribe((result) => {
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
              forkJoin([
                this.portfolioService.getPortfoliosForTenantOrderByName(),
                this.gps.getEntityFormDefinition('SimulationTenantCreateDTO')
              ]).subscribe(([portfolios, formDefinition]) => {
                const cashAccounts: Cashaccount[] = [];
                for (const portfolio of portfolios) {
                  if (portfolio.cashaccountList) {
                    cashAccounts.push(...portfolio.cashaccountList);
                  }
                }
                this.callbacks
                  ?.handleEdit(
                    AlgoSimulationCreateDynamicComponent,
                    { cashAccounts, formDefinition },
                    algoTop,
                    'CREATE_SIMULATION'
                  )
                  ?.subscribe((result) => {
                    if (result) {
                      this.callbacks?.refreshTree();
                    }
                  });
              });
            }
          });
          // The strategy hierarchy is read only inside an environment: it is edited in the user's own portfolio and
          // the simulation is replayed from there.
          if (!this.gps.isReadOnlyUser()) {
            menuItems.push({
              label: 'DELETE|STRATEGY',
              command: () => this.handleDeleteStrategy(treeNode, selectedNodeData.idAlgoAssetclassSecurity)
            });
          }
        }
        break;

      case TreeNodeType.SimulationEnvironment: {
        const simulation: SimulationTenantInfo = JSON.parse(typeNodeData.entityObject);
        // A replay is started from the main tenant by a user who may write; the backend books its fills as the owner.
        if (!this.isInSimulation() && !this.gps.isReadOnlyUser() && !simulation.active) {
          menuItems.push({
            label: 'SIMULATION_RUN_START' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () => this.handleStartReplay(typeNodeData)
          });
          menuItems.push({ separator: true });
        }
        // While the replay is booking its fills and rebuilding the holdings, the environment can neither be entered
        // nor deleted; the backend refuses both, so neither is offered. The node stays selectable for its figures.
        if (!simulation.active) {
          menuItems.push({
            label: 'SWITCH_TO_SIMULATION',
            command: () => this.switchToSimulationTenant(simulation.idTenant)
          });
          menuItems.push({
            label: 'DELETE_SIMULATION',
            command: () => this.handleDeleteSimulation(treeNode, simulation.idTenant)
          });
        }
        break;
      }

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
    return this.simulationContext.isInSimulation();
  }

  /**
   * Enters a simulation environment. The session is only rewritten once the backend has authorized the switch, so a
   * rejected request leaves the previous context untouched, and the application is reloaded afterwards: menus, the
   * navigation tree and every open panel are then rebuilt from the new token instead of showing home data under a
   * simulation session.
   *
   * @param simIdTenant - The simulation environment to enter
   */
  private switchToSimulationTenant(simIdTenant: number): void {
    this.manageClientService.switchAndReload(simIdTenant, false, () => this.simulationContext.enter(simIdTenant));
  }

  /** Leaves the simulation environment and returns to the user's own tenant, reloading the application. */
  private switchToMainTenant(): void {
    const mainIdTenant = sessionStorage.getItem(GlobalSessionNames.MAIN_ID_TENANT);
    if (!mainIdTenant) {
      return;
    }
    this.manageClientService.switchAndReload(+mainIdTenant, true, () => this.simulationContext.leave());
  }

  /**
   * Opens the start dialog of a historical replay and shows the replay panel of the environment once the run was
   * accepted. A panel that is already open learns about the start through the data changed service.
   *
   * @param typeNodeData the node data of the simulation environment, carrying the serialized environment
   */
  private handleStartReplay(typeNodeData: TypeNodeData): void {
    const sim: SimulationTenantInfo = JSON.parse(typeNodeData.entityObject);
    this.gps.getEntityFormDefinition('SimulationRunRequestDTO').subscribe((formDefinition) =>
      this.callbacks
        ?.handleEdit(AlgoSimulationRunStartDynamicComponent, { formDefinition }, sim, 'SIMULATION_RUN')
        ?.subscribe((result) => {
          if (result) {
            this.callbacks?.navigateToNode(typeNodeData);
          }
        })
    );
  }

  private handleDeleteSimulation(treeNode: TreeNode, idSimTenant: number): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|SIMULATION_ENVIRONMENT',
      () => {
        this.tenantService.deleteSimulationTenant(idSimTenant).subscribe({
          next: () => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
              i18nRecord: 'SIMULATION_ENVIRONMENT'
            });
            // Deleting the environment the session is in would leave the token naming a tenant that no longer exists,
            // and every following request would be refused. Return home instead of merely rebuilding the tree.
            if (sessionStorage.getItem(GlobalSessionNames.ID_TENANT) === idSimTenant.toString()) {
              this.switchToMainTenant();
            } else {
              this.callbacks?.refreshTree();
            }
          },
          error: (err) => console.error('Error deleting simulation:', err)
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
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
              i18nRecord: 'STRATEGY'
            });
            this.callbacks?.navigateToNode(this.getPreviousNode(treeNode).data);
            this.callbacks?.refreshTree();
          },
          error: (err) => console.error('Error deleting strategy:', err)
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
    this.translateService.get(key).subscribe((translated) => (target.label = translated + suffix));
  }

  private addMainRoute(suffix: string): string {
    return BaseSettings.MAINVIEW_KEY + '/' + suffix;
  }
}
