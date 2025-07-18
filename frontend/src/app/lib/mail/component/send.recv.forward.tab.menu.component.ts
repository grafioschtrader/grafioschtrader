import { Component } from '@angular/core';
import { AppSettings } from '../../../shared/app.settings';
import { AppHelper } from '../../helper/app.helper';
import {TabItem} from '../../../shared/types/tab.item';

/**
 * The tab menu for messages.
 * Uses SharedTabMenuComponent for consistent tab behavior.
 */
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
export class SendRecvForwardTabMenuComponent {
  tabs: TabItem[] = [
    {
      label: AppHelper.toUpperCaseWithUnderscore(AppSettings.MAIL_SEND_RECV),
      route: AppSettings.MAIL_SEND_RECV_KEY,
      icon: ''
    },
    {
      label: AppHelper.toUpperCaseWithUnderscore(AppSettings.MAIL_SETTING_FORWARD),
      route: AppSettings.MAIL_SETTING_FORWARD_KEY,
      icon: ''
    }
  ];

  defaultRoute: string = AppSettings.MAIL_SEND_RECV_KEY;
}
