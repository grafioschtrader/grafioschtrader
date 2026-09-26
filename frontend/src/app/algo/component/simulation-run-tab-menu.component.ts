import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Params, Router, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
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
      <router-outlet></router-outlet>
    </div>
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslateModule, RouterModule, Tabs, TabList, Tab]
})
export class SimulationRunTabMenuComponent extends BaseTabMenuComponent implements OnInit, OnDestroy {
  /** Tenant id of the simulation environment the tabs belong to. */
  private idTenant: number;
  private routeSubscribe: Subscription;

  constructor(router: Router, activatedRoute: ActivatedRoute, translateService: TranslateService) {
    super(router, activatedRoute, translateService);
  }

  override ngOnInit(): void {
    super.ngOnInit();
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      this.idTenant = +params['id'];
      const routeFromUrl = this.tabs.find((tab) => this.router.url.split('/').includes(tab.route))?.route;
      this.navigateTo(routeFromUrl ?? this.activeRoute);
    });
  }

  override ngOnDestroy(): void {
    super.ngOnDestroy();
    this.routeSubscribe?.unsubscribe();
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
