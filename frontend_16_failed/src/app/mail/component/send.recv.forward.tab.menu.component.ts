import {MenuItem} from 'primeng/api';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {Component, OnInit} from '@angular/core';
import {AppSettings} from '../../shared/app.settings';
import {AppHelper} from '../../shared/helper/app.helper';

/**
 * The tab menu for messages.
 */
@Component({
  template: `
    <p-tabMenu [model]="items" [activeItem]="items[0]"></p-tabMenu>
    <router-outlet></router-outlet>
  `
})
export class SendRecvForwardTabMenuComponent implements OnInit {
  items: MenuItem[];

  constructor(private router: Router, private activatedRoute: ActivatedRoute, public translateService: TranslateService) {
    this.items = [
      {label: AppHelper.convertPropertyNameToUppercase(AppSettings.MAIL_SEND_RECV), command: (event) => this.navigateToChangeRequest(true)},
      {
        label: AppHelper.convertPropertyNameToUppercase(AppSettings.MAIL_SETTING_FORWARD),
        command: (event) => this.router.navigate([AppSettings.MAIL_SETTING_FORWARD_KEY],
          {relativeTo: this.activatedRoute})
      }
    ];
    TranslateHelper.translateMenuItems(this.items, this.translateService);
  }

  ngOnInit(): void {
    this.navigateToChangeRequest(false);
  }

  /**
   * Is needed for the default navigation
   */
  private navigateToChangeRequest(activatePanel: boolean) {
    this.router.navigate([AppSettings.MAIL_SEND_RECV_KEY], {relativeTo: this.activatedRoute});
  }

}
