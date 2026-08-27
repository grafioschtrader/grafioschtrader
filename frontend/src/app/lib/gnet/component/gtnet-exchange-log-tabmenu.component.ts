import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { RouterModule } from '@angular/router';

import { SharedTabMenuComponent } from '../../tabmenu/component/shared.tab.menu.component';
import { TabItem } from '../../types/tab.item';
import { GTNetService } from '../service/gtnet.service';

/**
 * Tab menu component for GTNet Exchange Log.
 * Provides navigation between LAST_PRICE, HISTORICAL_PRICES, and SECURITY_METADATA logs.
 */
@Component({
  selector: 'gtnet-exchange-log-tabmenu',
  standalone: true,
  imports: [SharedTabMenuComponent, RouterModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (tabs.length) {
      <app-shared-tab-menu [tabs]="tabs" [defaultRoute]="defaultRoute">
        <router-outlet></router-outlet>
      </app-shared-tab-menu>
    }
  `
})
export class GTNetExchangeLogTabMenuComponent implements OnInit {
  tabs: TabItem[] = [];
  defaultRoute = '';

  constructor(private gtNetService: GTNetService) {}

  ngOnInit(): void {
    this.gtNetService.getAllGTNetsWithMessages().subscribe((response) => {
      this.tabs = response.exchangeKindTypes.map((kind) => ({
        label: kind.name,
        route: kind.name.toLowerCase(),
        icon: ''
      }));
      this.defaultRoute = this.tabs[0]?.route ?? '';
    });
  }
}
