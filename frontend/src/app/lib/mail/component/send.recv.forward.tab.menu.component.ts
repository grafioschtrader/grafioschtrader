import {Component} from '@angular/core';
import {AppHelper} from '../../helper/app.helper';
import {TabItem} from '../../types/tab.item';
import {BaseSettings} from '../../base.settings';
import {CommonModule} from '@angular/common';
import {RouterModule} from '@angular/router';
import {SharedTabMenuComponent} from '../../tabmenu/component/shared.tab.menu.component';

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
  standalone: true,
  imports: [CommonModule, RouterModule, SharedTabMenuComponent]
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
