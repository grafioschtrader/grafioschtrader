import { Component, computed, inject, input, OnInit } from '@angular/core';
import {
  IonBackButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonRefresher,
  IonRefresherContent,
  IonTitle,
  IonToolbar
} from '@ionic/angular';
import { TranslatePipe } from '@ngx-translate/core';
import { MobileDashboardStore } from '../service/mobile.dashboard.store';
import { mobileCardTitleKey } from '../widget/mobile.dashboard.state';
import { WidgetBodyComponent } from '../widget/widget-body.component';

/**
 * One widget over the whole screen. Pulling down reads this widget alone again; a failed read keeps the figures on
 * screen and marks them stale.
 */
@Component({
  selector: 'mobile-widget-page',
  imports: [
    IonHeader,
    IonToolbar,
    IonButtons,
    IonBackButton,
    IonTitle,
    IonContent,
    IonRefresher,
    IonRefresherContent,
    TranslatePipe,
    WidgetBodyComponent
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-buttons slot="start">
          <ion-back-button defaultHref="/tabs/dashboard" />
        </ion-buttons>
        <ion-title>{{ title() | translate }}</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      <ion-refresher slot="fixed" (ionRefresh)="refresh($event)">
        <ion-refresher-content />
      </ion-refresher>
      <mobile-widget-body [result]="result()" [loading]="store.loading()" [tall]="true" />
    </ion-content>
  `
})
export class WidgetPage implements OnInit {
  /** Widget instance from the route. */
  readonly instanceId = input.required<string>();

  protected readonly store = inject(MobileDashboardStore);

  protected readonly result = computed(() => this.store.results()[this.instanceId()]);
  protected readonly title = computed(() => {
    const widget = this.store.widgets()?.find((w) => w.instanceId === this.instanceId());
    return widget ? mobileCardTitleKey(widget.type) : 'DASHBOARD';
  });

  /** A link opened directly, or a reload of the page, finds no dashboard yet and reads it. */
  ngOnInit(): void {
    if (!this.store.hasCurrentDashboard()) {
      this.store.reload();
    }
  }

  protected refresh(event: { target: { complete(): void } }): void {
    this.store.refreshWidget(this.instanceId()).subscribe({
      complete: () => event.target.complete(),
      error: () => event.target.complete()
    });
  }
}
