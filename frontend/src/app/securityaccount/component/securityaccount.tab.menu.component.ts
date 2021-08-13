import {TranslateService} from '@ngx-translate/core';
import {ActivatedRoute, Params, Router} from '@angular/router';
import {AppSettings} from '../../shared/app.settings';
import {Securityaccount} from '../../entities/securityaccount';
import {MenuItem} from 'primeng/api';
import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {Subscription} from 'rxjs';
import {TranslateHelper} from '../../shared/helper/translate.helper';

/**
 * Tab menu for security account.
 * TODO Until now the change detection for the disabled menu does not work as expected.
 */
@Component({
  template: `
    <p-tabMenu #tabMenu [model]="items" [activeItem]="activeTab"></p-tabMenu>
    <router-outlet></router-outlet>
  `
})
export class SecurityaccountTabMenuComponent implements OnInit, OnDestroy {

  /**
   * Only way to activate a menu item programmatically
   */
    // @ViewChild('tabMenu', { static: false }) tabMenu: TabMenu;
  @ViewChild('tabMenu') tabMenu: any;
  activeTab: MenuItem;
  items: MenuItem[];
  readonly platTransImportMenuItem: MenuItem;
  private lastItemIndex = 0;
  private routeParamSubscribe: Subscription;
  private queryParamSubscribe: Subscription;
  private securityaccount: Securityaccount;
  private lastRouteKey = AppSettings.SECURITYACCOUNT_SUMMERY_ROUTE_KEY;

  constructor(private router: Router,
              private activatedRoute: ActivatedRoute,
              public translateService: TranslateService) {

    this.platTransImportMenuItem = {
      label: 'IMPORT_TRANSACTION',
      command: (event) => this.navigateToRoute(AppSettings.SECURITYACCOUNT_IMPORT_KEY, 1)
    };

    this.items = [
      {
        label: AppSettings.SECURITYACCOUNT.toUpperCase(),
        command: (event) => this.navigateToRoute(AppSettings.SECURITYACCOUNT_SUMMERY_ROUTE_KEY, 0)
      },
      this.platTransImportMenuItem
    ];
    TranslateHelper.translateMenuItems(this.items, this.translateService);
  }

  ngOnInit(): void {
    this.routeParamSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      if (params['object']) {
        this.securityaccount = JSON.parse(params['object']);
        const importDisabled = !this.securityaccount.tradingPlatformPlan.importTransactionPlatform;
        this.platTransImportMenuItem.disabled = importDisabled;
        this.items = [...this.items];
        if (params[AppSettings.SUCCESS_FAILED_IMP_TRANS]) {
          this.tabMenu.activeItem = this.items[1];
          this.navigateToRoute(AppSettings.SECURITYACCOUNT_IMPORT_KEY, 1, params[AppSettings.SUCCESS_FAILED_IMP_TRANS]);
        } else {

          if (!importDisabled) {
            this.navigateToRoute(this.lastRouteKey, this.lastItemIndex);
          } else {
            this.lastItemIndex = 0;
            this.activeTab = this.items[this.lastItemIndex];
            this.navigateToRoute(AppSettings.SECURITYACCOUNT_SUMMERY_ROUTE_KEY, this.lastItemIndex);
          }
        }
      }
      this.activeTab = this.items[this.lastItemIndex];
    });
  }

  ngOnDestroy(): void {
    this.routeParamSubscribe && this.routeParamSubscribe.unsubscribe();
    this.queryParamSubscribe && this.queryParamSubscribe.unsubscribe();
  }

  /**
   * Is needed for the default navigation
   */
  private navigateToRoute(routeKey: string, itemIndex: number, sfdit?: any) {
    this.lastRouteKey = routeKey;
    this.lastItemIndex = itemIndex;
    const data = {securityaccount: JSON.stringify(this.securityaccount)};
    if (sfdit) {
      data[AppSettings.SUCCESS_FAILED_IMP_TRANS] = sfdit;
    }

    this.router.navigate([
      `${AppSettings.MAINVIEW_KEY}/${AppSettings.SECURITYACCOUNT_TAB_MENU_KEY}/${this.securityaccount.idSecuritycashAccount}/${routeKey}`,
      this.securityaccount.idSecuritycashAccount, data]);
  }
}


