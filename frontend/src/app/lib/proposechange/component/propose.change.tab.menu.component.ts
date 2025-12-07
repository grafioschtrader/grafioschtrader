import {Component} from '@angular/core';
import {TabItem} from '../../types/tab.item';
import {BaseSettings} from '../../base.settings';

import {RouterModule} from '@angular/router';
import {SharedTabMenuComponent} from '../../tabmenu/component/shared.tab.menu.component';


@Component({
  template: `
    <app-shared-tab-menu
      [tabs]="tabs"
      [defaultRoute]="defaultRoute">
      <router-outlet></router-outlet>
    </app-shared-tab-menu>
  `,
  standalone: true,
  imports: [RouterModule, SharedTabMenuComponent]
})
export class ProposeChangeTabMenuComponent {
  tabs: TabItem[] = [
    {
      label: 'CHANGE_REQUESTS_FOR_YOU',
      route: BaseSettings.PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY,
      icon: ''
    },
    {
      label: 'YOUR_CHANGE_REQUESTS',
      route: BaseSettings.PROPOSE_CHANGE_YOUR_PROPOSAL_KEY,
      icon: ''
    }
  ];

  defaultRoute = BaseSettings.PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY;
}
