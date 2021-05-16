import {Component, OnInit} from '@angular/core';
import {AppSettings} from '../../shared/app.settings';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {MenuItem} from 'primeng/api';

@Component({
  template: `
    <p-tabMenu [model]="items" [activeItem]="items[0]"></p-tabMenu>
    <router-outlet></router-outlet>
  `
})
export class ProposeChangeTabMenuComponent implements OnInit {
  items: MenuItem[];

  constructor(private router: Router, private activatedRoute: ActivatedRoute, public translateService: TranslateService) {
    this.items = [
      {label: 'CHANGE_REQUESTS_FOR_YOU', command: (event) => this.navigateToChangeRequest(true)},
      {
        label: 'YOUR_CHANGE_REQUESTS',
        command: (event) => this.router.navigate([AppSettings.PROPOSE_CHANGE_YOUR_PROPOSAL_KEY],
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
    this.router.navigate([AppSettings.PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY], {relativeTo: this.activatedRoute});
  }
}
