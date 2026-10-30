import { Component, inject, effect, input, numberAttribute, untracked } from '@angular/core';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Portfolio } from '../../entities/portfolio';
import { TabItem } from '../../lib/types/tab.item';
import { SessionStorageTabHelper } from '../../lib/tabmenu/component/session.storage.tab.helper';
import { TreeNavigationStateService } from '../../lib/maintree/service/tree.navigation.state.service';
import { GlobalGTSessionNames } from '../../shared/global.gt.session.names';
import { SharedTabMenuComponent } from '../../lib/tabmenu/component/shared.tab.menu.component';

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
      <router-outlet />
    </app-shared-tab-menu>
  `,
  imports: [SharedTabMenuComponent, RouterModule]
})
export class PortfolioTabMenuComponent {
  readonly id = input.required({ transform: numberAttribute });

  private router = inject(Router);
  private activatedRoute = inject(ActivatedRoute);
  private treeNavState = inject(TreeNavigationStateService);
  private sessionStorageHelper = inject(SessionStorageTabHelper);

  readonly tabs: TabItem[] = [
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
  sessionStorageKey: string = GlobalGTSessionNames.TAB_MENU_PORTFOLIO;

  private portfolio: Portfolio | null;

  navigateWithPortfolio = (route: string): void => {
    if (!this.portfolio || this.portfolio.idPortfolio == null) {
      console.warn('No portfolio available for navigation');
      return;
    }
    // Store entity in shared service so child components can access it
    this.treeNavState.setEntity(route, this.portfolio.idPortfolio, this.portfolio);
    this.router.navigate([route, this.portfolio.idPortfolio], {
      relativeTo: this.activatedRoute
    });
  };
  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => {
        if (id) {
          this.portfolio = this.treeNavState.getEntity<Portfolio>(
            BaseSettings.MAINVIEW_KEY + '/' + AppSettings.PORTFOLIO_TAB_MENU_KEY,
            id
          );
          if (this.portfolio) {
            this.navigateToInitialRoute();
          }
        }
      });
    });
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
