import {Component, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, Params, Router, RouterModule} from '@angular/router';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {combineLatest, Observable, Subscription} from 'rxjs';
import {CommonModule} from '@angular/common';
import {Tab, TabList, Tabs} from 'primeng/tabs';
import {Watchlist} from '../../entities/watchlist';
import {UDFMetadataSecurityService} from '../../udfmetasecurity/service/udf.metadata.security.service';
import {GlobalSessionNames} from '../../lib/global.session.names';
import {UDFMetadataGeneralService} from '../../lib/udfmeta/service/udf.metadata.general.service';
import {BaseTabMenuComponent} from '../../lib/tabmenu/component/base.tab.menu.component';
import {AppSettings} from '../../shared/app.settings';
import {TabItem} from '../../lib/types/tab.item';

/**
 * Watchlist Tab Menu Component extending BaseTabMenuComponent.
 * Manages tab navigation for watchlist-related views with complex initialization
 * including UDF metadata loading and watchlist parameter handling.
 * Preserves active tab state when switching between different watchlists.
 */
@Component({
  template: `
    <div class="card">
      <p-tabs [value]="activeRoute">
        <p-tablist>
          @for (tab of tabs; track tab.route) {
            <p-tab [value]="tab.route" (click)="navigateTo(tab.route)">
              <span>{{ tab.label | translate }}</span>
            </p-tab>
          }
        </p-tablist>
      </p-tabs>
      <router-outlet></router-outlet>
    </div>
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule, RouterModule, Tabs, TabList, Tab]
})
export class WatchlistTabMenuComponent extends BaseTabMenuComponent implements OnInit, OnDestroy {

  /** The current watchlist object containing ID and data. */
  private watchlist: Watchlist;

  /** Subscription to route parameter changes for watchlist updates. */
  private routeSubscribe: Subscription;

  /** The last active route to preserve tab state across watchlist changes. */
  private lastActiveRoute: string = AppSettings.WATCHLIST_PERFORMANCE_KEY;

  /** Flag to track if this is the initial component load. */
  private isFirstLoad: boolean = true;

  /**
   * Creates an instance of WatchlistTabMenuComponent.
   *
   * @param router Angular Router for navigation
   * @param activatedRoute Current activated route for parameter access
   * @param translateService Translation service for internationalization
   * @param uDFMetadataSecurityService Service for loading UDF security metadata
   * @param uDFMetadataGeneralService Service for loading UDF general metadata
   */
  constructor(
    router: Router,
    activatedRoute: ActivatedRoute,
    translateService: TranslateService,
    private uDFMetadataSecurityService: UDFMetadataSecurityService,
    private uDFMetadataGeneralService: UDFMetadataGeneralService
  ) {
    super(router, activatedRoute, translateService);
  }

  /**
   * Initializes the component with UDF metadata loading and watchlist subscription setup.
   * Loads required UDF metadata before setting up watchlist parameter tracking.
   */
  override ngOnInit(): void {
    // Handle UDF metadata loading first
    const observables: Observable<any>[] = [];
    if (!sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY)) {
      observables.push(this.uDFMetadataSecurityService.getAllByIdUserInOrderByUiOrderExcludeDisabled());
    }
    if (!sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL)) {
      observables.push(this.uDFMetadataGeneralService.getFieldDescriptorByIdUserAndEveryUserForEntity(AppSettings.CURRENCYPAIR));
    }
    if (observables.length > 0) {
      combineLatest(observables).subscribe((data: any[]) => {
        let dataIndex = 0;
        if (!sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY)) {
          sessionStorage.setItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY, JSON.stringify(data[dataIndex]));
          dataIndex++;
        }
        if (!sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL)) {
          sessionStorage.setItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL, JSON.stringify(data[dataIndex]));
        }
        this.subscribeWatchlistChange();
      });
    } else {
      this.subscribeWatchlistChange();
    }
    // Call parent initialization after setting up watchlist subscription
    super.ngOnInit();
  }

  /**
   * Cleans up component subscriptions to prevent memory leaks.
   */
  override ngOnDestroy(): void {
    super.ngOnDestroy();
    if (this.routeSubscribe) {
      this.routeSubscribe.unsubscribe();
    }
  }

  /**
   * Sets up subscription to route parameter changes to track watchlist updates.
   * Preserves the active tab when switching between different watchlists.
   */
  private subscribeWatchlistChange(): void {
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      if (params['object']) {
        this.watchlist = JSON.parse(params['object']);

        // Preserve the current active tab when switching watchlists
        if (this.isFirstLoad) {
          // On first load, check if there's already an active route in URL
          const currentRouteFromUrl = this.getCurrentRouteFromUrl();
          if (currentRouteFromUrl) {
            this.lastActiveRoute = currentRouteFromUrl;
            this.activeRoute = currentRouteFromUrl;
          }
          this.isFirstLoad = false;
        }
        // Navigate to the last active route (or default on first load)
        this.navigateTo(this.lastActiveRoute);
      }
    });
  }

  /**
   * Extracts the current route from the URL by matching against available tabs.
   * @returns The matching route or null if no match found
   */
  private getCurrentRouteFromUrl(): string | null {
    const urlSegments = this.router.url.split('/');
    return this.tabs.find(tab => urlSegments.includes(tab.route))?.route || null;
  }

  /**
   * Defines the available tabs for the watchlist menu.
   * @returns Array of tab items for watchlist functionality
   */
  protected initializeTabs(): TabItem[] {
    return [
      {
        label: 'WATCHLIST_PERFORMANCE',
        route: AppSettings.WATCHLIST_PERFORMANCE_KEY,
        icon: ''
      },
      {
        label: 'WATCHLIST_PRICE_FEED',
        route: AppSettings.WATCHLIST_PRICE_FEED_KEY,
        icon: ''
      },
      {
        label: 'UDF',
        route: AppSettings.WATCHLIST_UDF_KEY,
        icon: ''
      },
      {
        label: 'WATCHLIST_DIVIDEND_SPLIT_FEED',
        route: AppSettings.WATCHLIST_DIVIDEND_SPLIT_FEED_KEY,
        icon: ''
      }
    ];
  }

  /**
   * Gets the default route for initial navigation.
   * @returns The default watchlist route
   */
  protected getDefaultRoute(): string {
    return AppSettings.WATCHLIST_PERFORMANCE_KEY;
  }

  /**
   * Override navigateTo to handle watchlist parameter and preserve active tab.
   * Navigates with watchlist ID and serialized watchlist object as route parameters.
   *
   * @param route The route to navigate to
   */
  override navigateTo(route: string): void {
    if (!this.watchlist) {
      console.warn('No watchlist available for navigation');
      return;
    }
    // Update active route and remember the last active route
    this.activeRoute = route;
    this.lastActiveRoute = route;
    // Navigate with watchlist ID and object
    this.router.navigate([route, this.watchlist.idWatchlist, {
      watchlist: JSON.stringify(this.watchlist)
    }], {
      relativeTo: this.activatedRoute,
    });
  }

  /**
   * Override the default navigation to wait for watchlist data.
   * Prevents immediate navigation until watchlist is loaded from route parameters.
   */
  protected override navigateToDefault(): void {
    // Don't navigate immediately - wait for watchlist to be set
    // Navigation will happen in subscribeWatchlistChange
  }
}
