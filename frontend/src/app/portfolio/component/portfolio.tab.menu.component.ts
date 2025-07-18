import {Component, OnDestroy, OnInit} from '@angular/core';
import {AppSettings} from '../../shared/app.settings';
import {GlobalSessionNames} from '../../shared/global.session.names';
import {ActivatedRoute, Params, Router} from '@angular/router';
import {Portfolio} from '../../entities/portfolio';
import {Subscription} from 'rxjs';
import {TabItem} from '../../shared/types/tab.item';
import {SessionStorageTabHelper} from '../../lib/tabmenu/component/session.storage.tab.helper';

/**
 * Component for the tab menu of a single portfolio.
 * Uses SharedTabMenuComponent with custom navigation for portfolio parameters.
 */
@Component({
  template: `
    <app-shared-tab-menu
      [tabs]="tabs"
      [defaultRoute]="defaultRoute"
      [useSessionStorage]="true"
      [sessionStorageKey]="sessionStorageKey"
      [customNavigationHandler]="navigateWithPortfolio">
      <router-outlet></router-outlet>
    </app-shared-tab-menu>
  `,
  standalone: false
})
export class PortfolioTabMenuComponent implements OnInit, OnDestroy {
  tabs: TabItem[] = [
    {
      label: AppSettings.PORTFOLIO.toUpperCase(),
      route: AppSettings.PORTFOLIO_SUMMARY_KEY,
      icon: ''
    },
    {
      label: 'PEROIDPERFORMANCE',
      route: AppSettings.PERFORMANCE_KEY,
      icon: ''
    },
    {
      label: 'TRANSACTIONS',
      route: AppSettings.PORTFOLIO_TRANSACTION_KEY,
      icon: ''
    }
  ];

  defaultRoute: string = AppSettings.PORTFOLIO_SUMMARY_KEY;
  sessionStorageKey: string = GlobalSessionNames.TAB_MENU_PORTFOLIO;

  private routeSubscribe: Subscription;
  private portfolio: Portfolio;

  constructor(
    private router: Router,
    private activatedRoute: ActivatedRoute,
    private sessionStorageHelper: SessionStorageTabHelper // NEW: Inject helper
  ) {
  }

  navigateWithPortfolio = (route: string): void => {
    if (!this.portfolio) {
      console.warn('No portfolio available for navigation');
      return;
    }
    this.router.navigate([route, this.portfolio.idPortfolio, {
      portfolio: JSON.stringify(this.portfolio)
    }], {
      relativeTo: this.activatedRoute
    });
  };

  ngOnInit(): void {
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      if (params['object']) {
        this.portfolio = JSON.parse(params['object']);
        this.navigateToInitialRoute();
      }
    });
  }

  ngOnDestroy(): void {
    if (this.routeSubscribe) {
      this.routeSubscribe.unsubscribe();
    }
  }

  /**
   * Navigate to initial route after portfolio is loaded
   * Uses helper service for session storage operations
   */
  private navigateToInitialRoute(): void {
    // Use helper service to get saved route
    const savedRoute = this.sessionStorageHelper.getSavedActiveRoute(this.sessionStorageKey, this.tabs);
    // Navigate to saved route or default route
    const routeToNavigate = savedRoute || this.defaultRoute;
    // Use the custom navigation handler
    this.navigateWithPortfolio(routeToNavigate);
  }
}
