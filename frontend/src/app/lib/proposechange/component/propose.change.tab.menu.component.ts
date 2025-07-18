import { Component } from '@angular/core';
import { AppSettings } from '../../../shared/app.settings';
import {TabItem} from '../../../shared/types/tab.item';


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
      route: AppSettings.PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY,
      icon: ''
    },
    {
      label: 'YOUR_CHANGE_REQUESTS',
      route: AppSettings.PROPOSE_CHANGE_YOUR_PROPOSAL_KEY,
      icon: ''
    }
  ];

  defaultRoute = AppSettings.PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY;
}
