import {Component, Injectable, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {TreeNodeType} from '../types/treeNodeType';
import {AppSettings} from '../../app.settings';
import {Router} from '@angular/router';
import {TypeNodeData} from '../types/type.node.data';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../helper/app.helper';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../../mainmenubar/component/iglobal.menu.attach';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {Portfolio} from '../../../entities/portfolio';
import {Cashaccount} from '../../../entities/cashaccount';
import {CallParam, DialogVisible} from '../types/dialog.visible';
import {PortfolioService} from '../../../portfolio/service/portfolio.service';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {Securityaccount} from '../../../entities/securityaccount';
import {SecurityaccountService} from '../../../securityaccount/service/securityaccount.service';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {TenantService} from '../../../tenant/service/tenant.service';
import {combineLatest, Subscription} from 'rxjs';
import {WatchlistService} from '../../../watchlist/service/watchlist.service';
import {Watchlist} from '../../../entities/watchlist';
import {Tenant} from '../../../entities/tenant';
import {DataChangedService} from '../service/data.changed.service';
import {HelpIds} from '../../help/help.ids';
import {AlgoTopService} from '../../../algo/service/algo.top.service';
import {AlgoTopCreate} from '../../../entities/backend/algo.top.create';
import {RuleStrategy} from '../../types/rule.strategy';
import {TenantLimit, TenantLimitTypes} from '../../../entities/backend/tenant.limit';
import {AuditHelper} from '../../helper/audit.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {BusinessHelper} from '../../helper/business.helper';
import {WatchlistSecurityExists} from '../../../entities/dnd/watchlist.security.exists';
import {ConfirmationService, MenuItem, TreeNode} from 'primeng/api';
import {AlgoTop} from '../../../algo/model/algo.top';
import {GlobalSessionNames} from '../../global.session.names';


@Component({
  selector: 'main-tree',
  templateUrl: '../view/maintree.html',
  // changeDetection: ChangeDetectionStrategy.OnPush
})
export class MainTreeComponent implements OnInit, OnDestroy, IGlobalMenuAttach {

  // @ViewChild('cm', {static: true}) contextMenu: ContextMenu;
  @ViewChild('cm', {static: true}) contextMenu: any;

  /**
   * Only used to get primeng p-tabmenu working. For example when portfolio is clicked the 2nd time in the navigator, it could produce
   * a empty p-tabmenu.
   */
  lastRoute: string;
  lastId: number;
  portfolioTrees: TreeNode [];
  hasSecurityObject: { [key: number]: number } = {};
  selectedNode: TreeNode;
  contextMenuItems: MenuItem[];
  // For modal dialogs
  visibleDialogs: boolean[] = [false, false];
  callParam: CallParam;
  onlyCurrency = false;
  // Otherwise enum DialogVisible can't be used in a html template
  DialogVisible: typeof DialogVisible = DialogVisible;
  tenantLimits: { [key: string]: TenantLimit };
  tenant: Tenant;
  private PORTFOLIO_INDEX: number;
  private ALGO_INDEX: number;
  private WATCHLIST_INDEX: number;
  private BASEDATA_INDEX: number;
  // Admin node must be the last index
  private ADMINDATA_INDEX: number;
  private readonly UPDATE_TREE_PARTS = 2;
  private subscription: Subscription;

  private reRoutePrevSelection = -1;

  constructor(private dataChangedService: DataChangedService,
              private confirmationService: ConfirmationService,
              private activePanelService: ActivePanelService,
              private router: Router,
              private algoTopService: AlgoTopService,
              private portfolioService: PortfolioService,
              private securityaccountService: SecurityaccountService,
              private messageToastService: MessageToastService,
              public translateService: TranslateService,
              private globalParamService: GlobalparameterService,
              private tenantService: TenantService,
              private watchlistService: WatchlistService) {
    let i = 0;
    this.PORTFOLIO_INDEX = i++;
    if (this.useAlgo()) {
      this.ALGO_INDEX = i++;
    }
    this.WATCHLIST_INDEX = i++;
    this.BASEDATA_INDEX = i++;
    this.ADMINDATA_INDEX = i++;

    this.refreshTreeBecauseOfParentAction();
  }

  ////////////////////////////////////////////////////////////////////////////////
  addFirstLevelNode(): void {
    this.portfolioTrees[this.PORTFOLIO_INDEX] = {
      expanded: true, children: [],
      data: new TypeNodeData(TreeNodeType.PortfolioRoot, this.addMainRoute(AppSettings.TENANT_TAB_MENU_KEY),
        null, null)
    };

    if (this.useAlgo()) {
      this.portfolioTrees[this.ALGO_INDEX] = {
        expanded: true, children: [],
        data: new TypeNodeData(TreeNodeType.AlgoRoot, this.addMainRoute(AppSettings.STRATEGY_OVERVIEW_KEY),
          null, null)
      };
    }

    this.portfolioTrees[this.WATCHLIST_INDEX] = {
      expanded: true, children: [],
      data: new TypeNodeData(TreeNodeType.WatchlistRoot, this.addMainRoute(AppSettings.WATCHLIST_KEY),
        this.globalParamService.getIdTenant(), null)
    };
    this.portfolioTrees[this.BASEDATA_INDEX] = {
      label: 'BASE_DATA',
      expanded: true, children: [],
      data: new TypeNodeData(TreeNodeType.BaseDataRoot, this.addMainRoute(AppSettings.PROPOSE_CHANGE_TAB_MENU_KEY),
        null, null)
    };


    this.portfolioTrees[this.ADMINDATA_INDEX] = {
      label: 'ADMIN_DATA',
      expanded: true, children: [],
      data: new TypeNodeData(TreeNodeType.AdminDataRoot, this.addMainRoute(AppSettings.USER_MESSAGE_KEY),
        null, null)
    };
    this.addAdminData();

    this.tenantService.getTenantAndPortfolio().subscribe(tenant => {
      this.tenant = tenant;
      this.useAlgo() && this.setLangTrans('ALGO_OVERVIEW', this.portfolioTrees[this.ALGO_INDEX]);
      this.setLangTrans(AppSettings.WATCHLIST.toUpperCase(), this.portfolioTrees[this.WATCHLIST_INDEX]);
      this.addAndRefreshPortfolioToTree();
      this.addRefreshAlgoToTree();
      this.addAndRefreshWatchlistToTree();
      this.addBaseData();
    });
  }

  addAndRefreshWatchlistToTree(): void {
    combineLatest([this.watchlistService.getWatchlistsByIdTenant(),
      this.watchlistService.watchlistsOfTenantHasSecurity()])
      .subscribe(data => {
        const watchlists: Watchlist[] = data[0];
        data[1].forEach(keyvalue => this.hasSecurityObject[keyvalue[0]] = keyvalue[1]);
        this.portfolioTrees[this.WATCHLIST_INDEX].children.splice(0);
        for (const watchlist of watchlists) {
          const treeNode = {
            label: watchlist.name,
            icon: 'pi ' + (watchlist.idWatchlist === this.tenant.idWatchlistPerformance ? 'pi-chart-line' : null),
            data: new TypeNodeData(TreeNodeType.Watchlist, this.addMainRoute(AppSettings.WATCHLIST_TAB_MENU_KEY), watchlist.idWatchlist,
              null, JSON.stringify(watchlist))
          };
          this.portfolioTrees[this.WATCHLIST_INDEX].children.push(treeNode);
        }
        this.reRoutePrevSelection--;
        this.selectPreviousSelection();
      });
  }


  ////////////////////////////////////////////////////////////////////////////////
  // Build Tree

  addBaseData(): void {
    this.portfolioTrees[this.BASEDATA_INDEX].children =
      [
        {
          label: AppSettings.ASSETCLASS.toUpperCase(),
          data: new TypeNodeData(TreeNodeType.AssetClass, this.addMainRoute(AppSettings.ASSETCLASS_KEY), null, null, null)
        },
        {
          label: AppSettings.STOCKEXCHANGE.toUpperCase(),
          data: new TypeNodeData(TreeNodeType.Stockexchange, this.addMainRoute(AppSettings.STOCKEXCHANGE_KEY), null, null, null)
        },
        {
          label: AppSettings.TRADING_PLATFORM_PLAN.toUpperCase(),
          data: new TypeNodeData(TreeNodeType.TradingPlatformPlan, this.addMainRoute(AppSettings.TRADING_PLATFORM_PLAN_KEY),
            null, null, null)
        },
        {
          label: 'IMPORTTRANSACTIONGROUP',
          data: new TypeNodeData(TreeNodeType.ImpTransTemplate, this.addMainRoute(AppSettings.IMP_TRANS_TEMPLATE_KEY),
            null, null, null)
        }
      ];
    this.setLangTransNode(this.portfolioTrees[this.BASEDATA_INDEX]);
  }

  addAdminData(): void {
    this.portfolioTrees[this.ADMINDATA_INDEX].children = [];

    this.portfolioTrees[this.ADMINDATA_INDEX].children.push({
      label: 'TRADING_CALENDAR_GLOBAL',
      data: new TypeNodeData(TreeNodeType.NO_MENU, this.addMainRoute(AppSettings.TRADING_CALENDAR_GLOBAL_KEY),
        null, null, null)
    });


    this.portfolioTrees[this.ADMINDATA_INDEX].children.push({
      label: 'SECURITY_HISTORY_QUALITY',
      data: new TypeNodeData(TreeNodeType.NO_MENU, this.addMainRoute(AppSettings.SECURITY_HISTORY_QUALITY_KEY),
        null, null, null)
    });

    this.portfolioTrees[this.ADMINDATA_INDEX].children.push({
      label: 'GLOBAL_SETTINGS',
      data: new TypeNodeData(TreeNodeType.NO_MENU, this.addMainRoute(AppSettings.GLOBAL_SETTINGS_KEY),
        null, null, null)
    });

    // this.addGTNetToTree();

    this.portfolioTrees[this.ADMINDATA_INDEX].children.push({
      label: 'TASK_DATA_MONITOR',
      data: new TypeNodeData(TreeNodeType.NO_MENU, this.addMainRoute(AppSettings.TASK_DATA_CHANGE_MONITOR_KEY),
        null, null, null)
    });

    if (AuditHelper.hasAdminRole(this.globalParamService)) {
      this.portfolioTrees[this.ADMINDATA_INDEX].children.push({
        label: 'CONNECTORAPIKEY',
        data: new TypeNodeData(TreeNodeType.NO_MENU, this.addMainRoute(AppSettings.CONNECTOR_API_KEY_KEY), null, null, null)
      });

      this.portfolioTrees[this.ADMINDATA_INDEX].children.push({
        label: 'USER_SETTINGS',
        data: new TypeNodeData(TreeNodeType.NO_MENU, this.addMainRoute(AppSettings.USER_ENTITY_LIMIT_KEY), null, null, null)
      });
    }
    this.setLangTransNode(this.portfolioTrees[this.ADMINDATA_INDEX]);
  }

  private addGTNetToTree(): void {
    const gtNetNode: TreeNode = {
      expanded: true, children: [],
      label: 'GTNET_CONSUME_MONITOR',
      data: new TypeNodeData(TreeNodeType.NO_MENU, this.addMainRoute(AppSettings.GTNET_CONSUME_MONITOR_KEY),
        null, null, null)
    };

    gtNetNode.children.push({
      label: 'GTNET_SETUP',
      data: new TypeNodeData(TreeNodeType.NO_MENU, this.addMainRoute(AppSettings.GTNET_KEY),
        null, null, null)
    });

    gtNetNode.children.push({
      label: 'GTNET_AUTOANWSER',
      data: new TypeNodeData(TreeNodeType.NO_MENU, this.addMainRoute(AppSettings.GTNET_AUTO_ANWSER_KEY),
        null, null, null)
    });

    gtNetNode.children.push({
      label: 'GTNET_PROVIDER_MONITOR',
      data: new TypeNodeData(TreeNodeType.NO_MENU, this.addMainRoute(AppSettings.GTNET_PROVIDER_MONITOR_KEY),
        null, null, null)
    });

    this.portfolioTrees[this.ADMINDATA_INDEX].children.push(gtNetNode);

  }

  addSecurityaccountToTree(portfolio: Portfolio): TreeNode {
    const portfolioJson = JSON.stringify(portfolio);
    const securityAccountNode: TreeNode = {
      expanded: true,
      data: new TypeNodeData(TreeNodeType.SecurityaccountRoot,
        this.addMainRoute((portfolio.securityaccountList.length > 0) ? AppSettings.SECURITYACCOUNT_SUMMERIES_ROUTE_KEY :
          AppSettings.SECURITYACCOUNT_EMPTY_ROUTE_KEY),
        portfolio.idPortfolio, null, portfolioJson),
      children: this.addSecurityaccountsToTree(portfolioJson, portfolio.securityaccountList)
    };
    this.setLangTrans('SECURITYACCOUNTS', securityAccountNode);
    return securityAccountNode;
  }

  addSecurityaccountsToTree(portfolioJson: string, securityaccountList: Securityaccount[]): TreeNode[] {
    const securitycashaccountNode: TreeNode[] = [];
    for (const securityaccount of securityaccountList) {
      const treeNode = {
        label: securityaccount.name,
        data: new TypeNodeData(TreeNodeType.SecurityAccount, this.addMainRoute(AppSettings.SECURITYACCOUNT_TAB_MENU_KEY),
          securityaccount.idSecuritycashAccount, portfolioJson, JSON.stringify(securityaccount), false),
        expanded: true
      };
      securitycashaccountNode.push(treeNode);
    }
    return securitycashaccountNode;
  }

  ////////////////////////////////////////////////////////////////////////////////
  ngOnInit(): void {
    this.portfolioTrees = new Array(this.ADMINDATA_INDEX + (AuditHelper.hasAdminRole(this.globalParamService) ? 1 : 0));
    this.addFirstLevelNode();
  }

  handleNew(dialogVisible: DialogVisible, parentObject: any, data: Tenant | Portfolio | Cashaccount | Securityaccount
    | AlgoTopCreate, tenantLimitType: TenantLimitTypes): void {
    if (tenantLimitType) {
      if (BusinessHelper.isLimitCheckOk(this.tenantLimits[tenantLimitType], this.messageToastService)) {
        this.handleEdit(dialogVisible, parentObject, data);
      }
    } else {
      this.handleEdit(dialogVisible, parentObject, data);
    }
  }

  handleEdit(dialogVisible: DialogVisible, parentObject: any, data: Tenant | Portfolio | Cashaccount | Securityaccount
    | AlgoTopCreate = null): void {
    this.callParam = new CallParam(parentObject, data);
    this.visibleDialogs[dialogVisible] = true;
  }

  /*
  checkLimits(data: Tenant | Portfolio | Cashaccount | Securityaccount
    | AlgoTopCreate = null) {
    if(data in)
  }

  */
  handleDeletePortfolio(treeNode: TreeNode, idPortfolio: number) {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|PORTFOLIO', () => {
        this.portfolioService.deletePortfolio(idPortfolio).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: AppSettings.PORTFOLIO.toUpperCase()});
          this.navigateRoute(this.getPreviousNode(treeNode).data);
          this.clearSelection();
        });
      });
  }

  handleDeleteSecurityaccount(treeNode: TreeNode, portfolio: Portfolio, idSecuritycashaccount: number) {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|SECURITYACCOUNT', () => {
        this.securityaccountService.deleteSecurityaccount(idSecuritycashaccount).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: AppSettings.SECURITYACCOUNT.toUpperCase()});
          if (portfolio.securityaccountList.length === 1) {
            treeNode.parent.data.route = this.addMainRoute(AppSettings.SECURITYACCOUNT_EMPTY_ROUTE_KEY);
            this.navigateRoute(treeNode.parent.data);
          }
          this.navigateRoute(this.getPreviousNode(treeNode).data);
          this.clearSelection();
        });
      });
  }

  handleDeleteStrategy(treeNode: TreeNode, idAlgoAssetclassSecurity: number) {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|STRATEGY', () => {
        this.algoTopService.deleteEntity(idAlgoAssetclassSecurity).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: 'STRATEGY'});
          this.navigateRoute(this.getPreviousNode(treeNode).data);
          this.clearSelection();
        });
      });
  }

  handleDeleteWatchlist(treeNode: TreeNode, idWatchlist: number) {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|WATCHLIST', () => {
        this.watchlistService.delete(idWatchlist).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: AppSettings.WATCHLIST.toUpperCase()});
          this.navigateRoute(this.getPreviousNode(treeNode).data);
          this.clearSelection();
        });
      });
  }

  getEditMenuItemsByTypeNode(treeNode: TreeNode): MenuItem[] {
    const typeNodeData = treeNode.data;
    const menuItems: MenuItem[] = [];
    const parentNodeData = (typeNodeData.parentObject) ? JSON.parse(typeNodeData.parentObject) : null;
    const selectedNodeData = (typeNodeData.entityObject) ? JSON.parse(typeNodeData.entityObject) : null;
    switch (typeNodeData.treeNodeType) {
      case TreeNodeType.PortfolioRoot:
        menuItems.push({
            label: 'EDIT_RECORD|CLIENT' + AppSettings.DIALOG_MENU_SUFFIX,
            command: (event) => {
              this.onlyCurrency = false;
              this.handleEdit(DialogVisible.DvTenant,
                null, selectedNodeData);
            }
          },
          {
            label: 'CLIENT_CHANGE_CURRENCY' + AppSettings.DIALOG_MENU_SUFFIX,
            command: (event) => {
              this.onlyCurrency = true;
              this.handleEdit(DialogVisible.DvTenant,
                null, selectedNodeData);
            }
          });
        menuItems.push({separator: true});
        menuItems.push({
          label: 'CREATE|PORTFOLIO' + AppSettings.DIALOG_MENU_SUFFIX,
          command: (event) => this.handleNew(DialogVisible.DvPortfolio,
            selectedNodeData, null, TenantLimitTypes.MAX_PORTFOLIO)
        });
        break;
      case TreeNodeType.Portfolio:
        menuItems.push({
          label: 'EDIT_RECORD|PORTFOLIO' + AppSettings.DIALOG_MENU_SUFFIX, command: (event) =>
            this.handleEdit(DialogVisible.DvPortfolio, parentNodeData, selectedNodeData)
        });
        menuItems.push({
          label: 'DELETE|PORTFOLIO', command: (event) =>
            this.handleDeletePortfolio(treeNode, selectedNodeData.idPortfolio),
          disabled: !(selectedNodeData.cashaccountList.length === 0 && selectedNodeData.securityaccountList.length === 0)
        });
        break;
      case TreeNodeType.SecurityaccountRoot:
        menuItems.push({
          label: 'CREATE|SECURITYACCOUNT' + AppSettings.DIALOG_MENU_SUFFIX,
          command: (event) => this.handleNew(DialogVisible.DvSecurityaccount, selectedNodeData, null,
            TenantLimitTypes.MAX_SECURITY_ACCOUNT)
        });
        break;
      case TreeNodeType.SecurityAccount:
        menuItems.push({
          label: 'EDIT_RECORD|SECURITYACCOUNT', command: (event) =>
            this.handleEdit(DialogVisible.DvSecurityaccount, parentNodeData, selectedNodeData)
        });
        menuItems.push({
          label: 'DELETE|SECURITYACCOUNT', command: (event) =>
            this.handleDeleteSecurityaccount(treeNode, parentNodeData, selectedNodeData.idSecuritycashAccount),
          disabled: selectedNodeData.hasTransaction
        });
        break;
      case TreeNodeType.AlgoRoot:
        menuItems.push({
          label: 'CREATE|ALGO_PORTFOLIO_STRATEGY' + AppSettings.DIALOG_MENU_SUFFIX, command: (event) =>
            this.handleNew(DialogVisible.DvAlgoRuleStrategy, parentNodeData, new AlgoTopCreate(RuleStrategy.RS_STRATEGY), null)
        });
        menuItems.push({
          label: 'CREATE|ALGO_RULE_BASED' + AppSettings.DIALOG_MENU_SUFFIX, command: (event) =>
            this.handleNew(DialogVisible.DvAlgoRuleStrategy, parentNodeData, new AlgoTopCreate(RuleStrategy.RS_RULE), null)
        });
        break;
      case TreeNodeType.Strategy:
        menuItems.push({
          label: 'DELETE|STRATEGY', command: (event) =>
            this.handleDeleteStrategy(treeNode, selectedNodeData.idAlgoAssetclassSecurity)
        });
        break;
      case TreeNodeType.WatchlistRoot:
        menuItems.push({
          label: 'CREATE|WATCHLIST' + AppSettings.DIALOG_MENU_SUFFIX, command: (event) =>
            this.handleNew(DialogVisible.DvWatchlist, parentNodeData, selectedNodeData, TenantLimitTypes.MAX_WATCHLIST)
        });

        break;
      case TreeNodeType.Watchlist:
        menuItems.push({
          label: 'EDIT_RECORD|WATCHLIST' + AppSettings.DIALOG_MENU_SUFFIX, command: (event) =>
            this.handleEdit(DialogVisible.DvWatchlist, parentNodeData, selectedNodeData)
        });
        menuItems.push({
          label: 'DELETE|WATCHLIST', command: (event) =>
            this.handleDeleteWatchlist(treeNode, selectedNodeData.idWatchlist),
          disabled: this.hasSecurityObject[selectedNodeData.idWatchlist] !== 0
        });
        menuItems.push({separator: true});
        menuItems.push({
          label: 'WATCHLIST_AS_PERFORMANCE' + AppSettings.DIALOG_MENU_SUFFIX, command: (event) =>
            this.handleWatchlistForPerformance(selectedNodeData.idWatchlist)
        });
        break;
    }
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return (menuItems.length > 0) ? menuItems : null;
  }

  ////////////////////////////////////////////////////////////////////////////////
  onNodeSelect(event) {
    this.nodeSelect(event.node);
  }

  onNodeContextMenuSelect(event) {
    const typeNodeData: TypeNodeData = event.node.data;
    this.contextMenuItems = this.getEditMenuItemsByTypeNode(event.node);
    if (!this.contextMenuItems) {
      this.contextMenu.hide();
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }


  ////////////////////////////////////////////////////////////////////////////////
  // Events

  callMeDeactivate(): void {
  }

  hideContextMenu(): void {
    this.contextMenu.hide();
  }

  onComponentClick(event) {
    this.activePanelService.activatePanel(this,
      {editMenu: (this.selectedNode) ? this.getEditMenuItemsByTypeNode(this.selectedNode) : null});
  }

  handleOnProcessedDialog(processedActionData: ProcessedActionData) {
    this.visibleDialogs = new Array(this.visibleDialogs.length).fill(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.clearSelection();
      this.reRoutePrevSelection = this.UPDATE_TREE_PARTS;
    }
  }

  handleWatchlistForPerformance(idWatchlist: number): void {
    this.tenantService.setWatchlistForPerformance(idWatchlist).subscribe(tenant => {
      this.tenant = tenant;
      this.addAndRefreshWatchlistToTree();
    });
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_INTRO_NAVIGATION;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  onKeydown(event) {
    // TODO Should show the content
    if (event.key === 'Enter') {
      console.log(event);
    }
  }

  public dragOver(event: DragEvent, node: TreeNode) {
    if (node.data.treeNodeType === TreeNodeType.Watchlist) {
      event.preventDefault();
    }
  }

  public drop(event: DragEvent, treeNode: TreeNode) {
    event.preventDefault();
    const wse: WatchlistSecurityExists = JSON.parse(event.dataTransfer.getData('text/plain'));
    this.watchlistService.getAllWatchlistsWithSecurityByIdSecuritycurrency(wse.idSecuritycurrency).subscribe(existWatchlistsIds => {
      if (existWatchlistsIds.includes(treeNode.data.id)) {
        // Move not possible
        this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'MOVE_SECURITY_WATCHLIST_FAILED', {to: treeNode.label});
      } else {
        // Move is possible
        this.watchlistService.moveSecuritycurrency(wse.idWatchlistSource, treeNode.data.id, wse.idSecuritycurrency).subscribe(success => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MOVE_SECURITY_WATCHLIST', {
            from: this.selectedNode.label, to: treeNode.label
          });
          this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.DELETED, new Watchlist()));
        });
      }
    });
  }

  private useAlgo(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.USE_ALGO) === 'true';
  }

  private addAndRefreshPortfolioToTree() {

    const portfolioObservable = this.portfolioService.getPortfoliosForTenantOrderByName();
    const teantLimitsObservalte =
      this.globalParamService.getMaxTenantLimitsByMsgKey([TenantLimitTypes.MAX_SECURITY_ACCOUNT,
        TenantLimitTypes.MAX_PORTFOLIO, TenantLimitTypes.MAX_WATCHLIST]);

    combineLatest([portfolioObservable, teantLimitsObservalte]).subscribe(results => {

      const tenantStringify = JSON.stringify(this.tenant);

      this.setLangTrans('PORTFOLIOS', this.portfolioTrees[this.PORTFOLIO_INDEX], '-' + this.tenant.tenantName
        + ' / ' + this.tenant.currency);
      const portfolios = results[0];
      this.portfolioTrees[this.PORTFOLIO_INDEX].data.entityObject = tenantStringify;
      this.tenantLimits = results[1].reduce((ac, tl) => ({...ac, [tl.msgKey]: tl}), {});

      this.portfolioTrees[this.PORTFOLIO_INDEX].children.splice(0);
      for (const portfolio of portfolios) {
        const treeNode = {
          label: portfolio.name + ' / ' + portfolio.currency,
          data: new TypeNodeData(TreeNodeType.Portfolio, this.addMainRoute(AppSettings.PORTFOLIO_TAB_MENU_KEY),
            portfolio.idPortfolio, tenantStringify, JSON.stringify(portfolio)), expanded: true,
          children: [this.addSecurityaccountToTree(portfolio)]
        };
        this.portfolioTrees[this.PORTFOLIO_INDEX].children.push(treeNode);
      }
      this.reRoutePrevSelection--;
      this.selectPreviousSelection();
    });
  }

  private addRefreshAlgoToTree(): void {
    if (this.useAlgo()) {
      this.algoTopService.getAlgoTopByIdTenantOrderByName().subscribe(algoTopList => {
        this.portfolioTrees[this.ALGO_INDEX].children.splice(0);
        for (const algoTop of algoTopList) {
          const treeNode = {
            label: algoTop.name,
            icon: 'pi ' + (algoTop.activatable ? 'pi-check-circle' : 'pi-question'),
            data: new TypeNodeData(TreeNodeType.Strategy, this.addMainRoute(AppSettings.ALGO_TOP_KEY),
              algoTop.idAlgoAssetclassSecurity, null, JSON.stringify(algoTop))
          };
          this.portfolioTrees[this.ALGO_INDEX].children.push(treeNode);
          this.reRoutePrevSelection--;
          this.selectPreviousSelection();
        }
      });
    }
  }

  private nodeSelect(node: TreeNode) {
    this.selectedNode = node;
    const data: TypeNodeData = this.selectedNode.data;
    this.navigateRoute(data);
  }

  private refreshTreeBecauseOfParentAction(): void {
    this.subscription = this.dataChangedService.dateChanged$.subscribe(processedActionData => {
      if (processedActionData.data instanceof Cashaccount) {
        this.addAndRefreshPortfolioToTree();
      } else if (processedActionData.data instanceof Watchlist) {
        this.addAndRefreshWatchlistToTree();
      } else if (processedActionData.data instanceof AlgoTop) {
        this.addRefreshAlgoToTree();
      }
    });
  }

  private selectPreviousSelection(): void {
    if (this.selectedNode) {
      this.selectedNode = this.getTreeNodeByTypeAndId(this.portfolioTrees, this.selectedNode.data.treeNodeType,
        this.selectedNode.data.id);
      if (this.reRoutePrevSelection === 0) {
        this.lastId = null;
        this.reRoutePrevSelection = -1;
        this.nodeSelect(this.selectedNode);
      }
    }
  }

  private setLangTransNode(treeNode: TreeNode) {
    this.setLangTrans(treeNode.label, treeNode);
    if (treeNode.children) {
      this.setLangTransNodes(treeNode.children);
    }
  }

  private setLangTransNodes(treeNodes: TreeNode[]) {
    treeNodes.forEach(treeNode => this.setLangTransNode(treeNode));
  }

  private setLangTrans(key: string, target: TreeNode, sufix: string = '') {
    this.translateService.get(key).subscribe(translated => target.label = translated + sufix);
  }

  private addMainRoute(sufix: string): string {
    return AppSettings.MAINVIEW_KEY + '/' + sufix;
  }

  private getPreviousNode(treeNode: TreeNode): TreeNode {
    let i = 0;
    while (i < treeNode.parent.children.length && treeNode.parent.children[i] !== treeNode) {
      i++;
    }
    this.selectedNode = i === 0 ? treeNode.parent : treeNode.parent.children[i - 1];
    return this.selectedNode;
  }

  private navigateRoute(data: TypeNodeData) {
    if (data && data.route) {
      if (data.id) {
        if (!this.lastRoute || this.lastRoute !== data.route || this.lastId !== data.id) {
          this.lastRoute = data.route;
          this.lastId = data.id;
          if (data.useQueryParams) {
            this.router.navigate([data.route, data.id], {queryParams: {object: data.entityObject}});
          } else {
            this.router.navigate([data.route, data.id, {object: data.entityObject}]);
          }
        }
      } else {
        this.router.navigate([data.route]);
        this.lastRoute = data.route;
      }
    }
  }

  private clearSelection(): void {
    this.tenantService.getTenantAndPortfolio().subscribe(tenant => {
      this.tenant = tenant;
      this.activePanelService.activatePanel(this, {editMenu: null});
      this.addAndRefreshPortfolioToTree();
      this.addRefreshAlgoToTree();
      // Must be on the last refresh
      this.addAndRefreshWatchlistToTree();
    });
  }

  private getTreeNodeByTypeAndId(treeNodeParent: TreeNode[], type: TreeNodeType, id: number): TreeNode {
    let foundTreeNode: TreeNode = null;
    for (const treeNode of treeNodeParent) {

      if (treeNode.data.treeNodeType === type && treeNode.data.id === id) {
        return treeNode;
      } else if (treeNode.children) {
        foundTreeNode = this.getTreeNodeByTypeAndId(treeNode.children, type, id);
        if (foundTreeNode) {
          return foundTreeNode;
        }
      }
    }
    return null;
  }


}



