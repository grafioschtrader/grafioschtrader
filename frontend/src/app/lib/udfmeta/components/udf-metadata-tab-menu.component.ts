import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { BaseSettings } from '../../base.settings';
import { SharedTabMenuComponent } from '../../tabmenu/component/shared.tab.menu.component';
import { TabItem } from '../../types/tab.item';

@Component({
  selector: 'udf-metadata-tab-menu',
  template: `<app-shared-tab-menu [tabs]="tabs" [defaultRoute]="defaultRoute"><router-outlet /></app-shared-tab-menu>`,
  imports: [SharedTabMenuComponent, RouterOutlet]
})
export class UDFMetadataTabMenuComponent {
  readonly defaultRoute = BaseSettings.UDF_METADATA_OWN_KEY;
  readonly tabs: TabItem[] = [
    { label: 'UDF_TAB_OWN', route: BaseSettings.UDF_METADATA_OWN_KEY, icon: '' },
    { label: 'UDF_TAB_SHARED', route: BaseSettings.UDF_METADATA_SHARED_KEY, icon: '' }
  ];
}
