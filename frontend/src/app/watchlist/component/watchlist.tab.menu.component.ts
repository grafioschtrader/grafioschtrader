import {
  DestroyRef,
  inject,
  Component,
  OnDestroy,
  OnInit,
  effect,
  input,
  numberAttribute,
  signal,
  untracked
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { combineLatest, Observable } from 'rxjs';
import { Tab, TabList, Tabs } from '@openng/optimus-ui/tabs';
import { Watchlist } from '../../entities/watchlist';
import { UDFMetadataSecurityService } from '../../udfmetasecurity/service/udf.metadata.security.service';
import { GlobalSessionNames } from '../../lib/global.session.names';
import { UDFMetadataGeneralService } from '../../lib/udfmeta/service/udf.metadata.general.service';
import { BaseTabMenuComponent } from '../../lib/tabmenu/component/base.tab.menu.component';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';
import { TreeNavigationStateService } from '../../lib/maintree/service/tree.navigation.state.service';
import { TabItem } from '../../lib/types/tab.item';
import { InstrumentStatisticsCacheService } from '../../securitycurrency/service/instrument.statistics.cache.service';
import { WatchlistExpandedPanelStateService } from '../service/watchlist.expanded.panel.state.service';

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
      <router-outlet />
    </div>
  `,
  // The tab panel gets the remaining height, so the watchlist table scrolls inside its own viewport and its caption
  // and column headers stay visible instead of the whole view scrolling away.
  styles: [
    `
      :host {
        display: block;
        height: 100%;
      }
      .card {
        display: flex;
        flex-direction: column;
        height: 100%;
      }
      .card ::ng-deep > router-outlet + * {
        flex: 1;
        min-height: 0;
      }
    `
  ],
  imports: [TranslateModule, RouterModule, Tabs, TabList, Tab],
  // Both services are scoped to this component on purpose: the cached instrument statistics and the remembered
  // accordion panels of an expanded row survive watchlist and tab changes, but are discarded when the user leaves
  // the watchlist area.
  providers: [InstrumentStatisticsCacheService, WatchlistExpandedPanelStateService]
})
export class WatchlistTabMenuComponent extends BaseTabMenuComponent implements OnInit, OnDestroy {
  private readonly metadataReady = signal(false);

  readonly id = input.required({ transform: numberAttribute });

  private treeNavState = inject(TreeNavigationStateService);
  private uDFMetadataSecurityService = inject(UDFMetadataSecurityService);
  private uDFMetadataGeneralService = inject(UDFMetadataGeneralService);

  protected readonly destroyRef = inject(DestroyRef);
  /** The current watchlist object containing ID and data. */
  private watchlist: Watchlist | null = null;

  /** The last active route to preserve tab state across watchlist changes. */
  private lastActiveRoute: string = AppSettings.WATCHLIST_PERFORMANCE_KEY;

  /** Flag to track if this is the initial component load. */
  private isFirstLoad: boolean = true;
  constructor() {
    super();
    effect(() => {
      const id = this.id();
      if (!this.metadataReady()) return;
      untracked(() => {
        if (id) {
          this.watchlist = this.treeNavState.getEntity<Watchlist>(
            BaseSettings.MAINVIEW_KEY + '/' + AppSettings.WATCHLIST_TAB_MENU_KEY,
            id
          );

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
    });
  }

  /**
   * Initializes the component with UDF metadata loading and route input handling.
   * Loads required UDF metadata before handling the watchlist input.
   */
  override ngOnInit(): void {
    // Handle UDF metadata loading first
    const observables: Observable<any>[] = [];
    if (!sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY)) {
      observables.push(this.uDFMetadataSecurityService.getAllByIdUserInOrderByUiOrderExcludeDisabled());
    }
    if (!sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL)) {
      observables.push(
        this.uDFMetadataGeneralService.getFieldDescriptorByIdUserAndEveryUserForEntity(AppSettings.CURRENCYPAIR)
      );
    }
    if (observables.length > 0) {
      combineLatest(observables)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((data: any[]) => {
          let dataIndex = 0;
          if (!sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY)) {
            sessionStorage.setItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY, JSON.stringify(data[dataIndex]));
            dataIndex++;
          }
          if (!sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL)) {
            sessionStorage.setItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL, JSON.stringify(data[dataIndex]));
          }
          this.metadataReady.set(true);
        });
    } else {
      this.metadataReady.set(true);
    }
    // Initialize tabs before the route input effect runs
    super.ngOnInit();
  }

  /**
   * Extracts the current route from the URL by matching against available tabs.
   * @returns The matching route or null if no match found
   */
  private getCurrentRouteFromUrl(): string | null {
    const urlSegments = this.router.url.split('/');
    return this.tabs.find((tab) => urlSegments.includes(tab.route))?.route || null;
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
      },
      { label: 'WATCHLIST_INDICATOR', route: AppSettings.WATCHLIST_INDICATOR_KEY, icon: '' }
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
    if (!this.watchlist || this.watchlist.idWatchlist == null) {
      console.warn('No watchlist available for navigation');
      return;
    }
    // Update active route and remember the last active route
    this.activeRoute = route;
    this.lastActiveRoute = route;
    // Store entity in shared service so child components can access it
    this.treeNavState.setEntity(route, this.watchlist.idWatchlist, this.watchlist);
    // Navigate with just the ID - no serialized entity
    this.router.navigate([route, this.watchlist.idWatchlist], {
      relativeTo: this.activatedRoute
    });
  }

  /**
   * Override the default navigation to wait for watchlist data.
   * Prevents immediate navigation until watchlist is loaded from route parameters.
   */
  protected override navigateToDefault(): void {
    // Don't navigate immediately - wait for watchlist to be set
    // Navigation will happen in the route input effect
  }
}
