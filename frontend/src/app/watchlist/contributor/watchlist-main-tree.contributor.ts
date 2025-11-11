import {Injectable} from '@angular/core';
import {Observable, combineLatest} from 'rxjs';
import {map, tap} from 'rxjs/operators';
import {MenuItem, TreeNode, ConfirmationService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {MainTreeContributor} from '../../lib/maintree/contributor/main-tree-contributor.interface';
import {TreeNodeType} from '../../shared/maintree/types/tree.node.type';
import {TypeNodeData} from '../../lib/maintree/types/type.node.data';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {WatchlistService} from '../service/watchlist.service';
import {TenantService} from '../../tenant/service/tenant.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {Watchlist} from '../../entities/watchlist';
import {Tenant} from '../../entities/tenant';
import {TenantLimitTypes} from '../../shared/types/tenant.limit';
import {WatchlistSecurityExists} from '../../entities/dnd/watchlist.security.exists';
import {AppSettings} from '../../shared/app.settings';
import {AppHelper} from '../../lib/helper/app.helper';
import {BaseSettings} from '../../lib/base.settings';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {WatchlistEditDynamicComponent} from '../component/watchlist.edit.dynamic.component';

/**
 * Contributor for Watchlist-related nodes in the main navigation tree.
 */
@Injectable()
export class WatchlistMainTreeContributor extends MainTreeContributor {

  private tenant: Tenant;
  private hasSecurityObject: { [key: number]: number } = {};
  private rootNode: TreeNode;
  private tenantLimits: any;

  constructor(
    private watchlistService: WatchlistService,
    private tenantService: TenantService,
    private globalParamService: GlobalparameterService,
    private messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
    private translateService: TranslateService
  ) {
    super();
  }

  getTreeOrder(): number {
    return 2; // Watchlist comes after Portfolio and potentially Algo
  }

  getRootNodes(): Observable<TreeNode[]> {
    return this.tenantService.getTenantAndPortfolio().pipe(
      tap(tenant => this.tenant = tenant),
      map(tenant => {
        this.rootNode = {
          expanded: true,
          children: [],
          data: new TypeNodeData(
            TreeNodeType.WatchlistRoot,
            this.addMainRoute(AppSettings.WATCHLIST_KEY),
            this.globalParamService.getIdTenant(),
            null
          )
        };
        this.setLangTrans('WATCHLIST_CORRELATION_MATRIX', this.rootNode);
        return [this.rootNode];
      })
    );
  }

  refreshNodes(rootNode: TreeNode): Observable<void> {
    this.rootNode = rootNode;

    return combineLatest([
      this.watchlistService.getWatchlistsByIdTenant(),
      this.watchlistService.getWatchlistsOfTenantHasSecurity(),
      this.tenantService.getMaxTenantLimitsByMsgKey([TenantLimitTypes.MAX_WATCHLIST])
    ]).pipe(
      map(([watchlists, hasSecurityData, tenantLimits]) => {
        // Update has security mapping
        hasSecurityData.forEach(item => this.hasSecurityObject[item.idWatchlist] = item.hasSecurity);

        // Store tenant limits
        this.tenantLimits = tenantLimits.reduce((ac, tl) => ({...ac, [tl.msgKey]: tl}), {});

        rootNode.children.splice(0);
        for (const watchlist of watchlists) {
          const treeNode = {
            label: watchlist.name,
            icon: 'pi ' + (watchlist.idWatchlist === this.tenant.idWatchlistPerformance ? 'pi-chart-line' : null),
            data: new TypeNodeData(
              TreeNodeType.Watchlist,
              this.addMainRoute(AppSettings.WATCHLIST_TAB_MENU_KEY),
              watchlist.idWatchlist,
              null,
              JSON.stringify(watchlist)
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
      case TreeNodeType.WatchlistRoot:
        menuItems.push({
          label: 'CREATE|WATCHLIST' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => {
            if (this.tenantLimits && BusinessHelper.isLimitCheckOk(
              this.tenantLimits[TenantLimitTypes.MAX_WATCHLIST],
              this.messageToastService
            )) {
              this.callbacks?.handleEdit(WatchlistEditDynamicComponent, parentNodeData, null,
                AppSettings.WATCHLIST.toUpperCase())
                ?.subscribe(result => {
                  if (result) {
                    this.callbacks?.refreshTree();
                  }
                });
            }
          }
        });
        break;

      case TreeNodeType.Watchlist:
        menuItems.push({
          label: 'EDIT_RECORD|WATCHLIST' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => this.callbacks?.handleEdit(WatchlistEditDynamicComponent, parentNodeData, selectedNodeData,
            AppSettings.WATCHLIST.toUpperCase())
            ?.subscribe(result => {
              if (result) {
                this.callbacks?.refreshTree();
              }
            })
        });
        menuItems.push({
          label: 'DELETE|WATCHLIST',
          command: () => this.handleDeleteWatchlist(treeNode, selectedNodeData.idWatchlist),
          disabled: this.hasSecurityObject[selectedNodeData.idWatchlist] !== 0
        });
        menuItems.push({separator: true});
        menuItems.push({
          label: 'WATCHLIST_AS_PERFORMANCE' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => this.handleWatchlistForPerformance(selectedNodeData.idWatchlist)
        });
        break;

      default:
        return null;
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems.length > 0 ? menuItems : null;
  }

  shouldRefreshOnDataChange(processedActionData: ProcessedActionData): boolean {
    return processedActionData.data instanceof Watchlist;
  }

  override handleDelete(treeNode: TreeNode, id: number): Observable<any> | null {
    if (treeNode.data.treeNodeType === TreeNodeType.Watchlist) {
      this.handleDeleteWatchlist(treeNode, id);
    }
    return null;
  }

  // Public method to update tenant limits (called from main component)
  updateTenantLimits(limits: any): void {
    this.tenantLimits = limits;
  }

  override canDrop(targetNode: TreeNode, dragData: string): boolean {
    return targetNode.data.treeNodeType === TreeNodeType.Watchlist;
  }

  override handleDrop(targetNode: TreeNode, dragData: string, sourceLabel?: string): void {
    try {
      const wse: WatchlistSecurityExists = JSON.parse(dragData);
      this.watchlistService.getAllWatchlistsWithSecurityByIdSecuritycurrency(wse.idSecuritycurrency).subscribe(existWatchlistsIds => {
        if (existWatchlistsIds.includes(targetNode.data.id)) {
          // Move not possible
          this.messageToastService.showMessageI18n(
            InfoLevelType.ERROR,
            'MOVE_SECURITY_WATCHLIST_FAILED',
            {to: targetNode.label}
          );
        } else {
          // Move is possible
          this.watchlistService.moveSecuritycurrency(wse.idWatchlistSource, targetNode.data.id, wse.idSecuritycurrency).subscribe(success => {
            this.messageToastService.showMessageI18n(
              InfoLevelType.SUCCESS,
              'MOVE_SECURITY_WATCHLIST',
              {from: sourceLabel, to: targetNode.label}
            );
            this.callbacks?.refreshTree();
          });
        }
      });
    } catch (e) {
      console.error('Error handling drop:', e);
    }
  }

  // Private helper methods

  private handleDeleteWatchlist(treeNode: TreeNode, idWatchlist: number): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|WATCHLIST',
      () => {
        this.watchlistService.delete(idWatchlist).subscribe({
          next: () => {
            this.messageToastService.showMessageI18n(
              InfoLevelType.SUCCESS,
              'MSG_DELETE_RECORD',
              {i18nRecord: AppSettings.WATCHLIST.toUpperCase()}
            );
            this.callbacks?.navigateToNode(this.getPreviousNode(treeNode).data);
            this.callbacks?.refreshTree();
          },
          error: err => console.error('Error deleting watchlist:', err)
        });
      }
    );
  }

  private handleWatchlistForPerformance(idWatchlist: number): void {
    this.tenantService.setWatchlistForPerformance(idWatchlist).subscribe(tenant => {
      this.tenant = tenant;
      this.refreshNodes(this.rootNode).subscribe();
    });
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
