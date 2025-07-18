import {Directive, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, NavigationEnd, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {TranslateHelper} from '../../helper/translate.helper';
import {Subscription} from 'rxjs';
import {filter} from 'rxjs/operators';
import {TabItem} from '../../../shared/types/tab.item';


/**
 * Abstract base class for tab menu components using p-tabs
 * Provides common functionality for tab management, routing, and translation
 *
 * Usage:
 * 1. Extend this class in your tab menu component
 * 2. Add @Component decorator with template to your derived class
 * 3. Implement the abstract methods: initializeTabs() and getDefaultRoute()
 * 4. Override navigateTo() or navigateToDefault() if custom behavior is needed
 *
 * Template to use in derived components:
 * ```html
 * <div class="card">
 *   <p-tabs [value]="activeRoute">
 *     <p-tablist>
 *       @for (tab of tabs; track tab.route) {
 *         <p-tab [value]="tab.route" (click)="navigateTo(tab.route)">
 *           <span>{{ tab.label | translate }}</span>
 *         </p-tab>
 *       }
 *     </p-tablist>
 *   </p-tabs>
 *   <router-outlet></router-outlet>
 * </div>
 * ```
 */
@Directive()
export abstract class BaseTabMenuComponent implements OnInit, OnDestroy {
  // Public properties accessible by derived classes
  tabs: TabItem[] = [];
  activeRoute: string = '';

  // Private subscription for cleanup
  private routerSubscription: Subscription;

  protected constructor(
    protected router: Router,
    protected activatedRoute: ActivatedRoute,
    protected translateService: TranslateService
  ) {
  }

  ngOnInit(): void {
    // Initialize tabs from derived class
    this.tabs = this.initializeTabs();
    this.activeRoute = this.getDefaultRoute();

    // Translate the tab labels
    TranslateHelper.translateMenuItems(this.tabs, this.translateService);

    // Subscribe to router events to track active route
    this.routerSubscription = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: NavigationEnd) => {
        this.updateActiveRoute();
      });

    // Navigate to default route
    this.navigateToDefault();
  }

  ngOnDestroy(): void {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }

  /**
   * Update the active route based on current URL
   * Automatically detects which tab should be active
   */
  private updateActiveRoute(): void {
    const currentUrl = this.router.url;
    const matchingTab = this.tabs.find(tab => currentUrl.includes(tab.route));

    if (matchingTab) {
      this.activeRoute = matchingTab.route;
    } else {
      // Fallback to default route
      this.activeRoute = this.getDefaultRoute();
    }
  }

  /**
   * Navigate to the specified route
   * Can be overridden by derived classes for custom navigation behavior
   * @param route - The route key to navigate to
   */
  navigateTo(route: string): void {
    this.activeRoute = route;

    this.router.navigate([route], {
      relativeTo: this.activatedRoute
    });
  }

  /**
   * Navigate to the default route
   * Can be overridden by derived classes for custom behavior
   * (e.g., WatchlistTabMenuComponent overrides this to wait for watchlist data)
   */
  protected navigateToDefault(): void {
    this.navigateTo(this.getDefaultRoute());
  }

  // Abstract methods - must be implemented by derived classes

  /**
   * Initialize the tabs for this specific component
   * Each derived class must define its own tab configuration
   * @returns Array of TabItem objects defining the tabs
   */
  protected abstract initializeTabs(): TabItem[];

  /**
   * Get the default route for this component
   * Each derived class must define which tab should be active by default
   * @returns The default route string
   */
  protected abstract getDefaultRoute(): string;
}
