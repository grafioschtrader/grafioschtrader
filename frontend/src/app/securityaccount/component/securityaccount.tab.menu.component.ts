import { Component, OnInit, OnDestroy } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { AppSettings } from '../../shared/app.settings';
import { Securityaccount } from '../../entities/securityaccount';
import { Subscription } from 'rxjs';
import {BaseTabMenuComponent} from '../../lib/tabmenu/component/base.tab.menu.component';
import {TabItem} from '../../shared/types/tab.item';

/**
 * Tab menu for security account.
 * Uses BaseTabMenuComponent for consistent tab behavior with custom navigation logic.
 */
@Component({
  template: `
    <div class="card">
      <p-tabs [value]="activeRoute">
        <p-tablist>
          @for (tab of tabs; track tab.route) {
            <p-tab
              [value]="tab.route"
              [disabled]="isTabDisabled(tab)"
              (click)="!isTabDisabled(tab) && navigateTo(tab.route)">
              <span>{{ tab.label | translate }}</span>
            </p-tab>
          }
        </p-tablist>
      </p-tabs>
      <router-outlet></router-outlet>
    </div>
  `,
  standalone: false
})
export class SecurityaccountTabMenuComponent extends BaseTabMenuComponent implements OnInit, OnDestroy {
  private routeParamSubscribe: Subscription;
  private queryParamSubscribe: Subscription;
  private securityaccount: Securityaccount;
  private lastRouteKey: string = AppSettings.SECURITYACCOUNT_SUMMERY_ROUTE_KEY;
  private successFailedImportTransParam: any = null;

  constructor(
    router: Router,
    activatedRoute: ActivatedRoute,
    translateService: TranslateService
  ) {
    super(router, activatedRoute, translateService);
  }

  override ngOnInit(): void {
    // Initialize tabs first
    super.ngOnInit();

    // Subscribe to route parameters for securityaccount data
    this.routeParamSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      if (params['object']) {
        this.securityaccount = JSON.parse(params['object']);

        // Handle special import transaction parameter
        if (params[AppSettings.SUCCESS_FAILED_IMP_TRANS]) {
          this.successFailedImportTransParam = params[AppSettings.SUCCESS_FAILED_IMP_TRANS];
          this.navigateTo(AppSettings.SECURITYACCOUNT_IMPORT_KEY);
        } else {
          this.handleNormalNavigation();
        }
      }
    });
  }

  override ngOnDestroy(): void {
    super.ngOnDestroy();
    if (this.routeParamSubscribe) {
      this.routeParamSubscribe.unsubscribe();
    }
    if (this.queryParamSubscribe) {
      this.queryParamSubscribe.unsubscribe();
    }
  }

  /**
   * Handle normal navigation logic based on import availability
   */
  private handleNormalNavigation(): void {
    const importDisabled = this.isImportDisabled();

    if (!importDisabled) {
      // Navigate to last active route if import is available
      this.navigateTo(this.lastRouteKey);
    } else {
      // Force navigation to summary if import is disabled
      this.navigateTo(AppSettings.SECURITYACCOUNT_SUMMERY_ROUTE_KEY);
    }
  }

  /**
   * Check if import functionality is disabled
   */
  private isImportDisabled(): boolean {
    return !this.securityaccount?.tradingPlatformPlan?.importTransactionPlatform;
  }

  /**
   * Check if a specific tab should be disabled
   * @param tab - The tab to check
   */
  isTabDisabled(tab: TabItem): boolean {
    if (tab.route === AppSettings.SECURITYACCOUNT_IMPORT_KEY) {
      return this.isImportDisabled();
    }
    return false;
  }

  protected initializeTabs(): TabItem[] {
    return [
      {
        label: AppSettings.SECURITYACCOUNT.toUpperCase(),
        route: AppSettings.SECURITYACCOUNT_SUMMERY_ROUTE_KEY,
        icon: ''
      },
      {
        label: 'IMPORT_TRANSACTION',
        route: AppSettings.SECURITYACCOUNT_IMPORT_KEY,
        icon: ''
      }
    ];
  }

  protected getDefaultRoute(): string {
    return AppSettings.SECURITYACCOUNT_SUMMERY_ROUTE_KEY;
  }

  /**
   * Override navigateTo to handle custom securityaccount navigation
   * @param route - The route to navigate to
   */
  override navigateTo(route: string): void {
    if (!this.securityaccount) {
      console.warn('No securityaccount available for navigation');
      return;
    }
    // Update active route and remember last route
    this.activeRoute = route;
    this.lastRouteKey = route;
    // Prepare navigation data
    const data: any = {
      securityaccount: JSON.stringify(this.securityaccount)
    };
    // Add special import transaction parameter if available
    if (this.successFailedImportTransParam) {
      data[AppSettings.SUCCESS_FAILED_IMP_TRANS] = this.successFailedImportTransParam;
      this.successFailedImportTransParam = null; // Reset after use
    }
    // Navigate with complex route structure
    this.router.navigate([
      `${AppSettings.MAINVIEW_KEY}/${AppSettings.SECURITYACCOUNT_TAB_MENU_KEY}/${this.securityaccount.idSecuritycashAccount}/${route}`,
      this.securityaccount.idSecuritycashAccount,
      data
    ]);
  }

  /**
   * Override the default navigation to not navigate immediately
   * Wait for securityaccount to be available
   */
  protected override navigateToDefault(): void {
    // Don't navigate immediately - wait for securityaccount to be set
    // Navigation will happen in the params subscription
  }
}
