import { Component } from '@angular/core';
import { AppSettings } from '../../../shared/app.settings';
import { AppHelper } from '../../helper/app.helper';
import {TabItem} from '../../../shared/types/tab.item';
import {BaseSettings} from '../../base.settings';

/**
 * The tab menu for messages.
 * Uses SharedTabMenuComponent for consistent tab behavior.
 */
@Component({
  // Selector is not used
  selector: 'send-recv-forward-tab-menu',
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
      label: AppHelper.toUpperCaseWithUnderscore(BaseSettings.MAIL_SEND_RECV),
      route: BaseSettings.MAIL_SEND_RECV_KEY,
      icon: ''
    },
    {
      label: AppHelper.toUpperCaseWithUnderscore(BaseSettings.MAIL_SETTING_FORWARD),
      route: BaseSettings.MAIL_SETTING_FORWARD_KEY,
      icon: ''
    }
  ];

  defaultRoute: string = BaseSettings.MAIL_SEND_RECV_KEY;
}
