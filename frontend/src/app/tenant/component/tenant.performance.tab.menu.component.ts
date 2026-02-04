import {Component} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {AppSettings} from '../../shared/app.settings';
import {TabItem} from '../../lib/types/tab.item';
import {SharedTabMenuComponent} from '../../lib/tabmenu/component/shared.tab.menu.component';

@Component({
  // Selector is not used
  selector: 'tenant-performance-tab-menu',
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
export class TenantPerformanceTabMenuComponent {
  tabs: TabItem[] = [
    {
      label: 'PERFORMANCE',
      route: AppSettings.PERFORMANCE_KEY,
      icon: ''
    },
    {
      label: 'PERFORMANCE_EOD_MISSING',
      route: AppSettings.EOD_DATA_QUALITY_KEY,
      icon: ''
    }
  ];

  defaultRoute = AppSettings.PERFORMANCE_KEY;
}
