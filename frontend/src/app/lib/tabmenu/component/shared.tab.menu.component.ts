import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {TranslateHelper} from '../../helper/translate.helper';
import {TabItem} from '../../types/tab.item';
import {Subscription} from 'rxjs';
import {TabMenuService} from '../service/tab.menu.service';
import {SessionStorageTabHelper} from './session.storage.tab.helper';
import {CommonModule} from '@angular/common';
import {TabsModule} from 'primeng/tabs';

/**
 * Shared Tab Menu Component providing a consistent tab menu interface using PrimeNG p-tabs.
 * Supports automatic translation, session storage persistence, and custom navigation handlers.
 *
 * @example
 * ```html
 * <app-shared-tab-menu [tabs]="tabs" [defaultRoute]="defaultRoute">
 *   <router-outlet></router-outlet>
 * </app-shared-tab-menu>
 * ```
 */
@Component({
  selector: 'app-shared-tab-menu',
  template: `
    <div class="card">
      <p-tabs [value]="activeRoute">
        <p-tablist>
          @for (tab of tabs; track tab.route) {
            <p-tab [value]="tab.route" (click)="onTabClick(tab.route)">
              <span>{{ tab.label | translate }}</span>
            </p-tab>
          }
        </p-tablist>
      </p-tabs>
      <ng-content></ng-content>
    </div>
  `,
  standalone: true,
  imports: [CommonModule, TabsModule, TranslateModule],
  providers: [TabMenuService]
})
export class SharedTabMenuComponent implements OnInit, OnDestroy {

  /**
   * Array of tab items to display in the tab menu.
   * @example [{ label: 'TAB_ONE', route: 'route-one', icon: '' }]
   */
  @Input() tabs: TabItem[] = [];

  /** The default route to navigate to when the component is initialized. */
  @Input() defaultRoute: string = '';

  /** Whether to enable session storage for persisting the active tab. */
  @Input() useSessionStorage: boolean = false;

  /** The session storage key to use for persisting the active tab. */
  @Input() sessionStorageKey: string = '';

  /**
   * Optional custom navigation handler for complex routing scenarios.
   * @example (route: string) => this.router.navigate([route, this.entityId])
   */
  @Input() customNavigationHandler: ((route: string) => void) | null = null;

  /** The currently active route. */
  activeRoute: string = '';

  private subscription: Subscription;

  constructor(
    private tabMenuService: TabMenuService,
    private activatedRoute: ActivatedRoute,
    private translateService: TranslateService,
    private sessionStorageHelper: SessionStorageTabHelper
  ) {
  }

  /**
   * Initializes the component, handles session storage restoration,
   * sets up tab navigation, and configures route tracking.
   */
  ngOnInit(): void {
    // Handle session storage for active route using helper
    if (this.useSessionStorage && this.sessionStorageKey) {
      const savedRoute = this.sessionStorageHelper.getSavedActiveRoute(this.sessionStorageKey, this.tabs);
      if (savedRoute) {
        this.defaultRoute = savedRoute;
      }
    }

    // Initialize tabs through service only if no custom navigation handler
    if (!this.customNavigationHandler) {
      this.tabs = this.tabMenuService.initializeTabs(
        this.tabs,
        this.defaultRoute,
        this.activatedRoute
      );

      // Subscribe to active route changes
      this.subscription = this.tabMenuService.activeRoute$.subscribe(route => {
        this.activeRoute = route;
        this.saveActiveRoute(route);
      });
    } else {
      // For custom navigation, just translate tabs and set initial route
      TranslateHelper.translateMenuItems(this.tabs, this.translateService);
      this.activeRoute = this.defaultRoute;
    }
  }

  /** Cleans up subscriptions to prevent memory leaks. */
  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  /**
   * Handles tab click events using either custom navigation handler or default navigation.
   * @param route The route of the clicked tab
   */
  onTabClick(route: string): void {
    // Use custom navigation handler if provided
    if (this.customNavigationHandler) {
      this.customNavigationHandler(route);
      this.activeRoute = route;
      this.saveActiveRoute(route);
    } else {
      this.tabMenuService.navigateTo(route, this.activatedRoute);
    }
  }

  /** Saves the active route to session storage if enabled. */
  private saveActiveRoute(route: string): void {
    if (this.useSessionStorage && this.sessionStorageKey) {
      this.sessionStorageHelper.saveActiveRoute(this.sessionStorageKey, route, this.tabs);
    }
  }
}
