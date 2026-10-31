import { Component } from '@angular/core';
import { IonIcon, IonLabel, IonTabBar, IonTabButton, IonTabs } from '@ionic/angular';
import { TranslatePipe } from '@ngx-translate/core';
import { addIcons } from 'ionicons';
import { briefcaseOutline, gridOutline, listOutline } from 'ionicons/icons';

/** The main views of the mobile application, switched by tabs at the bottom of the screen. */
@Component({
  selector: 'mobile-tabs-page',
  imports: [IonTabs, IonTabBar, IonTabButton, IonIcon, IonLabel, TranslatePipe],
  template: `
    <ion-tabs>
      <ion-tab-bar slot="bottom">
        <ion-tab-button tab="dashboard">
          <ion-icon name="grid-outline" />
          <ion-label>{{ 'DASHBOARD' | translate }}</ion-label>
        </ion-tab-button>
        <ion-tab-button tab="portfolios">
          <ion-icon name="briefcase-outline" />
          <ion-label>{{ 'PORTFOLIOS' | translate }}</ion-label>
        </ion-tab-button>
        <ion-tab-button tab="watchlists">
          <ion-icon name="list-outline" />
          <ion-label>{{ 'MOBILE_WATCHLISTS' | translate }}</ion-label>
        </ion-tab-button>
      </ion-tab-bar>
    </ion-tabs>
  `
})
export class TabsPage {
  constructor() {
    addIcons({ briefcaseOutline, gridOutline, listOutline });
  }
}
