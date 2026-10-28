import { Component, OnInit, signal, inject } from '@angular/core';
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
  imports: [SharedTabMenuComponent, RouterModule],
  template: `
    @if (tabs().length) {
      <app-shared-tab-menu [tabs]="tabs()" [defaultRoute]="defaultRoute()">
        <router-outlet />
      </app-shared-tab-menu>
    }
  `
})
export class GTNetExchangeLogTabMenuComponent implements OnInit {
  private gtNetService = inject(GTNetService);

  /** One tab per exchange kind, known once the GTNet overview is loaded */
  readonly tabs = signal<TabItem[]>([]);
  readonly defaultRoute = signal('');

  ngOnInit(): void {
    this.gtNetService.getAllGTNetsWithMessages().subscribe((response) => {
      const tabs = response.exchangeKindTypes.map((kind) => ({
        label: kind.name,
        route: kind.name.toLowerCase(),
        icon: ''
      }));
      this.defaultRoute.set(tabs[0]?.route ?? '');
      this.tabs.set(tabs);
    });
  }
}
