import {Injectable} from '@angular/core';
import {Observable, combineLatest} from 'rxjs';
import {map, tap} from 'rxjs/operators';
import {MenuItem, TreeNode, ConfirmationService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {MainTreeContributor} from '../../lib/maintree/contributor/main-tree-contributor.interface';
import {TreeNodeType} from '../../shared/maintree/types/tree.node.type';
import {TypeNodeData} from '../../lib/maintree/types/type.node.data';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {PortfolioService} from '../service/portfolio.service';
import {SecurityaccountService} from '../../securityaccount/service/securityaccount.service';
import {TenantService} from '../../tenant/service/tenant.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {Portfolio} from '../../entities/portfolio';
import {Cashaccount} from '../../entities/cashaccount';
import {Securityaccount} from '../../entities/securityaccount';
import {Tenant} from '../../entities/tenant';
import {TenantLimit, TenantLimitTypes} from '../../shared/types/tenant.limit';
import {AppSettings} from '../../shared/app.settings';
import {AppHelper} from '../../lib/helper/app.helper';
import {BaseSettings} from '../../lib/base.settings';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {PortfolioEditDynamicComponent} from '../component/portfolio.edit.dynamic.component';
import {SecurityaccountEditDynamicComponent} from '../../securityaccount/component/securityaccount.edit.dynamic.component';

/**
 * Contributor for Portfolio-related nodes in the main navigation tree.
 * Manages the portfolio root node and all portfolio/security account children.
 */
@Injectable()
export class PortfolioMainTreeContributor extends MainTreeContributor {

  private tenant: Tenant;
  private tenantLimits: { [key: string]: TenantLimit };
  private rootNode: TreeNode;

  constructor(
    private portfolioService: PortfolioService,
    private securityaccountService: SecurityaccountService,
    private tenantService: TenantService,
    private globalParamService: GlobalparameterService,
    private messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
    private translateService: TranslateService
  ) {
    super();
  }

  getTreeOrder(): number {
    return 0; // Portfolio is first
  }

  getRootNodes(): Observable<TreeNode[]> {
    return this.tenantService.getTenantAndPortfolio().pipe(
      tap(tenant => this.tenant = tenant),
      map(tenant => {
        this.rootNode = {
          expanded: true,
          children: [],
          data: new TypeNodeData(
            TreeNodeType.PortfolioRoot,
            this.addMainRoute(AppSettings.TENANT_TAB_MENU_KEY),
            null,
            null
          )
        };
        return [this.rootNode];
      })
    );
  }

  refreshNodes(rootNode: TreeNode): Observable<void> {
    this.rootNode = rootNode;
    const tenantObservable = this.tenantService.getTenantAndPortfolio();
    const portfolioObservable = this.portfolioService.getPortfoliosForTenantOrderByName();
    const tenantLimitsObservable = this.tenantService.getMaxTenantLimitsByMsgKey([
      TenantLimitTypes.MAX_SECURITY_ACCOUNT,
      TenantLimitTypes.MAX_PORTFOLIO,
      TenantLimitTypes.MAX_WATCHLIST
    ]);

    return combineLatest({
      tenant: tenantObservable,
      portfolios: portfolioObservable,
      tenantLimits: tenantLimitsObservable
    }).pipe(
      tap(({tenant}) => this.tenant = tenant),
      map(({tenant, portfolios, tenantLimits}): void => {
        const tenantStringify = JSON.stringify(tenant);

        // Set root node label with fresh tenant data
        this.setLangTrans('PORTFOLIOS', rootNode,
          '-' + tenant.tenantName + ' / ' + tenant.currency);

        rootNode.data.entityObject = tenantStringify;
        this.tenantLimits = tenantLimits.reduce((ac, tl) => ({...ac, [tl.msgKey]: tl}), {});
        rootNode.children.splice(0);

        this.addPortfoliosToRootNode(portfolios, tenantStringify, rootNode);
      })
    );
  }

  getContextMenuItems(treeNode: TreeNode, parentNodeData: any, selectedNodeData: any): MenuItem[] | null {
    const typeNodeData = treeNode.data;
    const menuItems: MenuItem[] = [];

    switch (typeNodeData.treeNodeType) {
      case TreeNodeType.PortfolioRoot:
        menuItems.push(
          {
            label: 'EDIT_RECORD|TENANT' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () => this.callbacks?.handleTenantEdit(selectedNodeData, false)
              ?.subscribe(result => {
                if (result) {
                  this.callbacks?.refreshTree();
                }
              })
          },
          {
            label: 'TENANT_CHANGE_CURRENCY' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () => this.callbacks?.handleTenantEdit(selectedNodeData, true)
              ?.subscribe(result => {
                if (result) {
                  this.callbacks?.refreshTree();
                }
              })
          }
        );
        menuItems.push({separator: true});
        menuItems.push({
          label: 'CREATE|PORTFOLIO' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => {
            if (BusinessHelper.isLimitCheckOk(this.tenantLimits[TenantLimitTypes.MAX_PORTFOLIO], this.messageToastService)) {
              this.callbacks?.handleEdit(PortfolioEditDynamicComponent, selectedNodeData, null, AppSettings.PORTFOLIO.toUpperCase())
                ?.subscribe(result => {
                  if (result) {
                    this.callbacks?.refreshTree();
                  }
                });
            }
          }
        });
        break;

      case TreeNodeType.Portfolio:
        menuItems.push({
          label: 'EDIT_RECORD|PORTFOLIO' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => this.callbacks?.handleEdit(PortfolioEditDynamicComponent, parentNodeData, selectedNodeData,
            AppSettings.PORTFOLIO.toUpperCase())
            ?.subscribe(result => {
              if (result) {
                this.callbacks?.refreshTree();
              }
            })
        });
        menuItems.push({
          label: 'DELETE|PORTFOLIO',
          command: () => this.handleDeletePortfolio(treeNode, selectedNodeData.idPortfolio),
          disabled: !(selectedNodeData.cashaccountList.length === 0 && selectedNodeData.securityaccountList.length === 0)
        });
        break;

      case TreeNodeType.SecurityaccountRoot:
        menuItems.push({
          label: 'CREATE|SECURITYACCOUNT' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => {
            if (BusinessHelper.isLimitCheckOk(this.tenantLimits[TenantLimitTypes.MAX_SECURITY_ACCOUNT], this.messageToastService)) {
              this.callbacks?.handleEdit(SecurityaccountEditDynamicComponent, selectedNodeData, null,
                AppSettings.SECURITYACCOUNT.toUpperCase())
                ?.subscribe(result => {
                  if (result) {
                    this.callbacks?.refreshTree();
                  }
                });
            }
          }
        });
        break;

      case TreeNodeType.SecurityAccount:
        menuItems.push({
          label: 'EDIT_RECORD|SECURITYACCOUNT',
          command: () => this.callbacks?.handleEdit(SecurityaccountEditDynamicComponent, parentNodeData, selectedNodeData,
            AppSettings.SECURITYACCOUNT.toUpperCase())
            ?.subscribe(result => {
              if (result) {
                this.callbacks?.refreshTree();
              }
            })
        });
        menuItems.push({
          label: 'DELETE|SECURITYACCOUNT',
          command: () => this.handleDeleteSecurityaccount(treeNode, parentNodeData, selectedNodeData.idSecuritycashAccount),
          disabled: selectedNodeData.hasTransaction
        });
        break;

      default:
        return null;
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems.length > 0 ? menuItems : null;
  }

  shouldRefreshOnDataChange(processedActionData: ProcessedActionData): boolean {
    return processedActionData.data instanceof Cashaccount;
  }

  override handleDelete(treeNode: TreeNode, id: number): Observable<any> | null {
    const typeNodeData = treeNode.data;
    if (typeNodeData.treeNodeType === TreeNodeType.Portfolio) {
      this.handleDeletePortfolio(treeNode, id);
    } else if (typeNodeData.treeNodeType === TreeNodeType.SecurityAccount) {
      const portfolio = JSON.parse(treeNode.data.parentObject);
      this.handleDeleteSecurityaccount(treeNode, portfolio, id);
    }
    return null;
  }

  // Private helper methods

  /**
   * Adds portfolio nodes to the root node of the tree.
   * Creates tree nodes for each portfolio with their associated security account children.
   *
   * @param portfolios - Array of portfolios to add to the tree
   * @param tenantStringify - Stringified tenant object for parent reference
   * @param rootNode - Root tree node to add portfolio children to
   * @private
   */
  private addPortfoliosToRootNode(portfolios: Portfolio[], tenantStringify: string, rootNode: TreeNode): void {
    for (const portfolio of portfolios) {
      const treeNode = {
        label: portfolio.name + ' / ' + portfolio.currency,
        data: new TypeNodeData(
          TreeNodeType.Portfolio,
          this.addMainRoute(AppSettings.PORTFOLIO_TAB_MENU_KEY),
          portfolio.idPortfolio,
          tenantStringify,
          JSON.stringify(portfolio)
        ),
        expanded: true,
        children: [this.addSecurityaccountToTree(portfolio)]
      };
      rootNode.children.push(treeNode);
    }
  }

  private addSecurityaccountToTree(portfolio: Portfolio): TreeNode {
    const portfolioJson = JSON.stringify(portfolio);
    const securityAccountNode: TreeNode = {
      expanded: true,
      data: new TypeNodeData(
        TreeNodeType.SecurityaccountRoot,
        this.addMainRoute(
          portfolio.securityaccountList.length > 0
            ? AppSettings.SECURITYACCOUNT_SUMMARIES_ROUTE_KEY
            : AppSettings.SECURITYACCOUNT_EMPTY_ROUTE_KEY
        ),
        portfolio.idPortfolio,
        null,
        portfolioJson
      ),
      children: this.addSecurityaccountsToTree(portfolioJson, portfolio.securityaccountList)
    };
    this.setLangTrans('SECURITYACCOUNTS', securityAccountNode);
    return securityAccountNode;
  }

  private addSecurityaccountsToTree(portfolioJson: string, securityaccountList: Securityaccount[]): TreeNode[] {
    const securitycashaccountNode: TreeNode[] = [];
    for (const securityaccount of securityaccountList) {
      const treeNode = {
        label: securityaccount.name,
        data: new TypeNodeData(
          TreeNodeType.SecurityAccount,
          this.addMainRoute(AppSettings.SECURITYACCOUNT_TAB_MENU_KEY),
          securityaccount.idSecuritycashAccount,
          portfolioJson,
          JSON.stringify(securityaccount),
          false
        ),
        expanded: true
      };
      securitycashaccountNode.push(treeNode);
    }
    return securitycashaccountNode;
  }

  private handleDeletePortfolio(treeNode: TreeNode, idPortfolio: number): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|PORTFOLIO',
      () => {
        this.portfolioService.deletePortfolio(idPortfolio).subscribe({
          next: () => {
            this.messageToastService.showMessageI18n(
              InfoLevelType.SUCCESS,
              'MSG_DELETE_RECORD',
              {i18nRecord: AppSettings.PORTFOLIO.toUpperCase()}
            );
            this.callbacks?.navigateToNode(this.getPreviousNode(treeNode).data);
            this.callbacks?.refreshTree();
          },
          error: err => console.error('Error deleting portfolio:', err)
        });
      }
    );
  }

  private handleDeleteSecurityaccount(treeNode: TreeNode, portfolio: Portfolio, idSecuritycashaccount: number): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|SECURITYACCOUNT',
      () => {
        this.securityaccountService.deleteSecurityaccount(idSecuritycashaccount).subscribe({
          next: () => {
            this.messageToastService.showMessageI18n(
              InfoLevelType.SUCCESS,
              'MSG_DELETE_RECORD',
              {i18nRecord: AppSettings.SECURITYACCOUNT.toUpperCase()}
            );
            if (portfolio.securityaccountList.length === 1) {
              treeNode.parent.data.route = this.addMainRoute(AppSettings.SECURITYACCOUNT_EMPTY_ROUTE_KEY);
              this.callbacks?.navigateToNode(treeNode.parent.data);
            }
            this.callbacks?.navigateToNode(this.getPreviousNode(treeNode).data);
            this.callbacks?.refreshTree();
          },
          error: err => console.error('Error deleting security account:', err)
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
