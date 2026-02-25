import {Component} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {AppSettings} from '../../shared/app.settings';
import {TabItem} from '../../lib/types/tab.item';
import {SharedTabMenuComponent} from '../../lib/tabmenu/component/shared.tab.menu.component';

@Component({
  selector: 'tenant-transaction-cost-tab-menu',
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
export class TenantTransactionCostTabMenuComponent {
  tabs: TabItem[] = [
    {
      label: 'TRANSACTION_COST',
      route: AppSettings.TRANSACTION_COST_SUMMARY_KEY,
      icon: ''
    },
    {
      label: 'FEE_MODEL_COMPARISON',
      route: AppSettings.FEE_MODEL_COMPARISON_KEY,
      icon: ''
    }
  ];

  defaultRoute = AppSettings.TRANSACTION_COST_SUMMARY_KEY;
}
