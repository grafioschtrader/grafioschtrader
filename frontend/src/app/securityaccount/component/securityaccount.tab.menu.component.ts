import { Component, OnInit, OnDestroy, inject, effect, input, numberAttribute, untracked } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { RouterOutlet } from '@angular/router';
import { AppSettings } from '../../shared/app.settings';
import { Securityaccount } from '../../entities/securityaccount';
import { BaseTabMenuComponent } from '../../lib/tabmenu/component/base.tab.menu.component';
import { TabItem } from '../../lib/types/tab.item';
import { BaseSettings } from '../../lib/base.settings';
import { TreeNavigationStateService } from '../../lib/maintree/service/tree.navigation.state.service';
import { Tabs, TabList, Tab } from '@openng/optimus-ui/tabs';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';

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
      <router-outlet />
    </div>
  `,
  imports: [Tabs, TabList, Tab, RouterOutlet, TranslatePipe]
})
export class SecurityaccountTabMenuComponent extends BaseTabMenuComponent implements OnInit, OnDestroy {
  readonly importResult = input<string>(undefined, { alias: 'successFailedDirectImpTran' });

  readonly id = input.required({ transform: numberAttribute });

  private treeNavState = inject(TreeNavigationStateService);
  private gpsGT = inject(GlobalparameterGTService);

  private securityaccount: Securityaccount | null;
  private lastRouteKey: string = AppSettings.SECURITYACCOUNT_SUMMERY_ROUTE_KEY;
  private successFailedImportTransParam: any = null;
  /**
   * Id of the security account the default-tab navigation was last performed for. Guards against the
   * spurious params re-emission that occurs when entering a child route strips the matrix param from
   * this component's :id segment - without it, handleNormalNavigation() would re-fire and issue a
   * competing navigation that resets the child router-outlet (e.g. losing the failed-import param).
   */
  private lastHandledId: number;
  constructor() {
    super();
    effect(() => {
      const id = this.id();
      const importResult = this.importResult();
      untracked(() => {
        if (id) {
          const route =
            this.activatedRoute.snapshot.routeConfig?.path?.split('/')[0] ?? AppSettings.SECURITYACCOUNT_TAB_MENU_KEY;
          this.securityaccount = this.treeNavState.getEntity<Securityaccount>(
            BaseSettings.MAINVIEW_KEY + '/' + route,
            id
          );

          if (this.securityaccount) {
            // Handle special import transaction parameter
            if (importResult) {
              this.lastHandledId = id;
              this.successFailedImportTransParam = importResult;
              this.navigateTo(AppSettings.SECURITYACCOUNT_IMPORT_TAB_MENU_KEY);
            } else if (id !== this.lastHandledId) {
              // Genuine account entry/switch - pick the default/last tab. Skipped on the re-emission
              // triggered by internal child navigation (same id, no param) to avoid a competing navigate.
              this.lastHandledId = id;
              this.handleNormalNavigation();
            }
          }
        }
      });
    });
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
   * Check if import functionality is disabled. Import is available when the securities account is mapped to an
   * import platform, or when the tenant has configured a Grafioschtrader import platform (for re-importing GT
   * exports into accounts without their own platform mapping).
   */
  private isImportDisabled(): boolean {
    return (
      !this.securityaccount?.tradingPlatformPlan?.importTransactionPlatform &&
      this.gpsGT.getTenantGtImportPlatformId() == null
    );
  }

  /**
   * Check if a specific tab should be disabled
   * @param tab - The tab to check
   */
  isTabDisabled(tab: TabItem): boolean {
    if (tab.route === AppSettings.SECURITYACCOUNT_IMPORT_TAB_MENU_KEY) {
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
        label: 'IMPORT',
        route: AppSettings.SECURITYACCOUNT_IMPORT_TAB_MENU_KEY,
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
    // Store entity in shared service so child components can access it without URL bloat
    this.treeNavState.setEntity(route, this.securityaccount.idSecuritycashAccount, this.securityaccount);
    // Prepare navigation data - only non-entity params
    const data: any = {};
    if (this.successFailedImportTransParam) {
      data[AppSettings.SUCCESS_FAILED_IMP_TRANS] = this.successFailedImportTransParam;
      this.successFailedImportTransParam = null;
    }
    // Navigate with just the ID - no serialized entity
    this.router.navigate([
      `${BaseSettings.MAINVIEW_KEY}/${AppSettings.SECURITYACCOUNT_TAB_MENU_KEY}/${this.securityaccount.idSecuritycashAccount}/${route}`,
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
    // Navigation will happen in the route input effect
  }
}
