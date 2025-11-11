import {Component} from '@angular/core';
import {TabItem} from '../../types/tab.item';
import {BaseSettings} from '../../base.settings';


@Component({
  template: `
    <app-shared-tab-menu
      [tabs]="tabs"
      [defaultRoute]="defaultRoute">
      <router-outlet></router-outlet>
    </app-shared-tab-menu>
  `,
  standalone: false
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
