import {Component} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {AppSettings} from '../../shared/app.settings';
import {TabItem} from '../../lib/types/tab.item';
import {SharedTabMenuComponent} from '../../lib/tabmenu/component/shared.tab.menu.component';

@Component({
  selector: 'standing-order-tab-menu',
  template: `
    <app-shared-tab-menu
      [tabs]="tabs"
      [defaultRoute]="defaultRoute">
      <router-outlet></router-outlet>
    </app-shared-tab-menu>
  `,
  standalone: true,
  imports: [SharedTabMenuComponent, RouterOutlet]
})
export class StandingOrderTabMenuComponent {
  tabs: TabItem[] = [
    {
      label: 'STANDING_ORDER_CASHACCOUNT',
      route: AppSettings.TENANT_STANDING_ORDER_CASHACCOUNT,
      icon: ''
    },
    {
      label: 'STANDING_ORDER_SECURITY',
      route: AppSettings.TENANT_STANDING_ORDER_SECURITY,
      icon: ''
    }
  ];

  defaultRoute = AppSettings.TENANT_STANDING_ORDER_CASHACCOUNT;
}
