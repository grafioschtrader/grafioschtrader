import { Component, OnDestroy, OnInit, effect, input, numberAttribute, untracked } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Tab, TabList, Tabs } from '@openng/optimus-ui/tabs';
import { BaseTabMenuComponent } from '../../lib/tabmenu/component/base.tab.menu.component';
import { TabItem } from '../../lib/types/tab.item';
import { AppSettings } from '../../shared/app.settings';

/**
 * Tabs of a simulation environment in the navigation tree: the historical replay with its progress and figures, and the
 * strategy hierarchy the latest replay was submitted with. The active tab is kept when another environment is chosen.
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
  imports: [TranslateModule, RouterModule, Tabs, TabList, Tab]
})
export class SimulationRunTabMenuComponent extends BaseTabMenuComponent implements OnInit, OnDestroy {
  readonly id = input.required({ transform: numberAttribute });

  /** Tenant id of the simulation environment the tabs belong to. */
  private idTenant: number;
  constructor() {
    super();
    effect(() => {
      const id = this.id();
      untracked(() => {
        this.idTenant = id;
        const routeFromUrl = this.tabs.find((tab) => this.router.url.split('/').includes(tab.route))?.route;
        this.navigateTo(routeFromUrl ?? this.activeRoute);
      });
    });
  }

  protected initializeTabs(): TabItem[] {
    return [
      { label: 'SIMULATION_RUN', route: AppSettings.SIMULATION_RUN_KEY, icon: '' },
      { label: 'SIMULATION_RUN_SETTINGS', route: AppSettings.SIMULATION_RUN_SETTINGS_KEY, icon: '' }
    ];
  }

  protected getDefaultRoute(): string {
    return AppSettings.SIMULATION_RUN_KEY;
  }

  /** Opens the tab for the current environment, whose tenant id every child route carries. */
  override navigateTo(route: string): void {
    if (!this.idTenant) {
      return;
    }
    this.activeRoute = route;
    this.router.navigate([route, this.idTenant], { relativeTo: this.activatedRoute });
  }

  /** Waits for the environment id from the route instead of navigating before it is known. */
  protected override navigateToDefault(): void {}
}
