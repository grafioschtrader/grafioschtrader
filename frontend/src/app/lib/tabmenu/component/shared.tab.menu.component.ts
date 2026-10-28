import { Component, OnDestroy, OnInit, signal, input, linkedSignal, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TranslateHelper } from '../../helper/translate.helper';
import { TabItem } from '../../types/tab.item';
import { Subscription } from 'rxjs';
import { TabMenuService } from '../service/tab.menu.service';
import { SessionStorageTabHelper } from './session.storage.tab.helper';

import { TabsModule } from '@openng/optimus-ui/tabs';

/**
 * Shared Tab Menu Component providing a consistent tab menu interface using Optimus p-tabs.
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
      <p-tabs [value]="activeRoute()">
        <p-tablist>
          @for (tab of tabs(); track tab.route) {
            <p-tab [value]="tab.route" (click)="onTabClick(tab.route)">
              <span>{{ tab.label | translate }}</span>
            </p-tab>
          }
        </p-tablist>
      </p-tabs>
      <ng-content />
    </div>
  `,
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
  imports: [TabsModule, TranslateModule],
  providers: [TabMenuService]
})
export class SharedTabMenuComponent implements OnInit, OnDestroy {
  private tabMenuService = inject(TabMenuService);
  private activatedRoute = inject(ActivatedRoute);
  private translateService = inject(TranslateService);
  private sessionStorageHelper = inject(SessionStorageTabHelper);

  /**
   * Array of tab items to display in the tab menu.
   * @example [{ label: 'TAB_ONE', route: 'route-one', icon: '' }]
   */
  readonly tabsInput = input<TabItem[]>([], { alias: 'tabs' });
  readonly tabs = linkedSignal(() => this.tabsInput());

  /** The default route to navigate to when the component is initialized. */
  readonly defaultRouteInput = input<string>('', { alias: 'defaultRoute' });
  readonly defaultRoute = linkedSignal(() => this.defaultRouteInput());

  /** Whether to enable session storage for persisting the active tab. */
  readonly useSessionStorage = input<boolean>(false);

  /** The session storage key to use for persisting the active tab. */
  readonly sessionStorageKey = input<string>('');

  /**
   * Optional custom navigation handler for complex routing scenarios.
   * @example (route: string) => this.router.navigate([route, this.entityId])
   */
  readonly customNavigationHandler = input<((route: string) => void) | null>(null);

  /** The currently active route. */
  readonly activeRoute = signal('');

  private subscription: Subscription;

  /**
   * Initializes the component, handles session storage restoration,
   * sets up tab navigation, and configures route tracking.
   */
  ngOnInit(): void {
    // Handle session storage for active route using helper
    const sessionStorageKey = this.sessionStorageKey();
    if (this.useSessionStorage() && sessionStorageKey) {
      const savedRoute = this.sessionStorageHelper.getSavedActiveRoute(sessionStorageKey, this.tabs());
      if (savedRoute) {
        this.defaultRoute.set(savedRoute);
      }
    }

    // Initialize tabs through service only if no custom navigation handler
    if (!this.customNavigationHandler()) {
      this.tabs.set(this.tabMenuService.initializeTabs(this.tabs(), this.defaultRoute(), this.activatedRoute));

      // Subscribe to active route changes
      this.subscription = this.tabMenuService.activeRoute$.subscribe((route) => {
        this.activeRoute.set(route);
        this.saveActiveRoute(route);
      });
    } else {
      // For custom navigation, just translate tabs and set initial route
      TranslateHelper.translateMenuItems(this.tabs(), this.translateService);
      this.activeRoute.set(this.defaultRoute());
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
    const customNavigationHandler = this.customNavigationHandler();
    if (customNavigationHandler) {
      customNavigationHandler(route);
      this.activeRoute.set(route);
      this.saveActiveRoute(route);
    } else {
      this.tabMenuService.navigateTo(route, this.activatedRoute);
    }
  }

  /** Saves the active route to session storage if enabled. */
  private saveActiveRoute(route: string): void {
    const sessionStorageKey = this.sessionStorageKey();
    if (this.useSessionStorage() && sessionStorageKey) {
      this.sessionStorageHelper.saveActiveRoute(sessionStorageKey, route, this.tabs());
    }
  }
}
