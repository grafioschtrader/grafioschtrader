import {Injectable} from '@angular/core';
import {ActivatedRoute, NavigationEnd, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {TranslateHelper} from '../../helper/translate.helper';
import {BehaviorSubject} from 'rxjs';
import {filter} from 'rxjs/operators';
import {TabItem} from '../../../shared/types/tab.item';

/**
 * Service for managing tab menu navigation and state tracking.
 * Provides automatic route tracking, translation, and navigation functionality for tab components.
 */
@Injectable()
export class TabMenuService {

  /** Subject for tracking the currently active route. */
  private activeRouteSubject = new BehaviorSubject<string>('');

  /** Observable stream of the currently active route for components to subscribe to. */
  activeRoute$ = this.activeRouteSubject.asObservable();

  constructor(
    private router: Router,
    private translateService: TranslateService
  ) {
  }

  /**
   * Initialize tabs with translation and router tracking.
   * Sets up automatic route tracking and navigates to the default route.
   *
   * @param tabs Array of tab items to initialize
   * @param defaultRoute The default route to navigate to
   * @param activatedRoute The activated route for relative navigation
   * @returns The translated tab items array
   */
  initializeTabs(tabs: TabItem[], defaultRoute: string, activatedRoute: ActivatedRoute): TabItem[] {
    // Translate the tab labels
    TranslateHelper.translateMenuItems(tabs, this.translateService);
    // Set initial active route
    this.activeRouteSubject.next(defaultRoute);
    // Subscribe to router events to update active route
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
        this.updateActiveRoute(tabs);
      });
    // Navigate to default route
    this.navigateTo(defaultRoute, activatedRoute);
    return tabs;
  }

  /**
   * Navigate to a specific route and update the active route state.
   *
   * @param route The route to navigate to
   * @param activatedRoute The activated route for relative navigation
   */
  navigateTo(route: string, activatedRoute: ActivatedRoute): void {
    this.activeRouteSubject.next(route);
    this.router.navigate([route], {relativeTo: activatedRoute});
  }

  /**
   * Update active route based on current URL by finding matching tab.
   * Uses longest match to avoid false matches with substring routes.
   *
   * @param tabs Array of available tabs to match against current URL
   */
  private updateActiveRoute(tabs: TabItem[]): void {
    const currentUrl = this.router.url;

    // Find the tab with the longest matching route (most specific match)
    const matchingTab = tabs
      .filter(tab => currentUrl.includes(tab.route))
      .sort((a, b) => b.route.length - a.route.length)[0];

    if (matchingTab) {
      this.activeRouteSubject.next(matchingTab.route);
    }
  }

  /**
   * Get the current active route value.
   *
   * @returns The currently active route string
   */
  getCurrentActiveRoute(): string {
    return this.activeRouteSubject.value;
  }
}
