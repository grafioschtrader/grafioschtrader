import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AppSettings } from '../../shared/app.settings';
import { TabItem } from '../../lib/types/tab.item';
import { SharedTabMenuComponent } from '../../lib/tabmenu/component/shared.tab.menu.component';
import { StandingOrderService } from '../service/standing.order.service';

@Component({
  selector: 'standing-order-tab-menu',
  template: `
    @if (simulationTenant) {
      <p class="sim-standing-order-info">{{ 'SIMULATION_STANDING_ORDER_INFO' | translate }}</p>
    }
    <app-shared-tab-menu [tabs]="tabs" [defaultRoute]="defaultRoute">
      <router-outlet></router-outlet>
    </app-shared-tab-menu>
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [SharedTabMenuComponent, RouterOutlet, TranslateModule]
})
export class StandingOrderTabMenuComponent implements OnInit {
  simulationTenant = false;
  tabs: TabItem[] = [];
  private readonly cashTab: TabItem = {
    label: 'STANDING_ORDER_CASHACCOUNT',
    route: AppSettings.TENANT_STANDING_ORDER_CASHACCOUNT,
    icon: ''
  };
  private readonly securityTab: TabItem = {
    label: 'STANDING_ORDER_SECURITY',
    route: AppSettings.TENANT_STANDING_ORDER_SECURITY,
    icon: ''
  };

  defaultRoute = AppSettings.TENANT_STANDING_ORDER_CASHACCOUNT;

  constructor(private standingOrderService: StandingOrderService) {}

  ngOnInit(): void {
    this.standingOrderService.getCapabilities().subscribe((capabilities) => {
      this.simulationTenant = capabilities.simulationTenant;
      this.tabs = capabilities.securityStandingOrderSupported ? [this.cashTab, this.securityTab] : [this.cashTab];
    });
  }
}
