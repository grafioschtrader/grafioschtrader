import {Component, OnInit} from '@angular/core';
import {MenuItem} from 'primeng/api';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {AppSettings} from '../../shared/app.settings';
import {TranslateHelper} from '../../shared/helper/translate.helper';

/**
 * Table menu for internal mail system
 */
@Component({
  template: `
    <p-tabMenu [model]="items" [activeItem]="items[0]"></p-tabMenu>
    <router-outlet></router-outlet>
  `
})
export class MailInOutTabMenuComponent implements OnInit {
  items: MenuItem[];

  constructor(private router: Router, private activatedRoute: ActivatedRoute, public translateService: TranslateService) {
    this.items = [
      {label: AppSettings.MAIL_INBOX.toUpperCase(), command: (event) => this.navigateToChangeRequest(true)},
      {
        label: AppSettings.MAIL_SENDBOX.toUpperCase(),
        command: (event) => this.router.navigate([AppSettings.MAIL_SENDBOX_KEY],
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
    this.router.navigate([AppSettings.MAIL_INBOX_KEY], {relativeTo: this.activatedRoute});
  }
}
