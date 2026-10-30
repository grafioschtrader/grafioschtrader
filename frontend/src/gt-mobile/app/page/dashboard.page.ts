import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonContent,
  IonHeader,
  IonIcon,
  IonRefresher,
  IonRefresherContent,
  IonSpinner,
  IonTitle,
  IonToolbar
} from '@ionic/angular';
import { TranslatePipe } from '@ngx-translate/core';
import { addIcons } from 'ionicons';
import { expandOutline, logOutOutline } from 'ionicons/icons';
import { DataType } from '../../../app/lib/dynamic-form/models/data.type';
import { MobileDashboardStore } from '../service/mobile.dashboard.store';
import { MobileFormatService } from '../service/mobile.format.service';
import { MobileLoginService } from '../service/mobile.login.service';
import { mobileCardTitleKey } from '../widget/mobile.dashboard.state';
import { WidgetBodyComponent } from '../widget/widget-body.component';

/**
 * The dashboard of the signed-in user as a column of cards in the order of the layout. The width a widget has on the
 * desktop does not apply; every card takes the full width of the phone. Pulling the list down reads it again.
 */
@Component({
  selector: 'mobile-dashboard-page',
  imports: [
    RouterLink,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonButton,
    IonIcon,
    IonContent,
    IonRefresher,
    IonRefresherContent,
    IonCard,
    IonCardHeader,
    IonCardTitle,
    IonCardContent,
    IonSpinner,
    TranslatePipe,
    WidgetBodyComponent
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-title>{{ 'DASHBOARD' | translate }}</ion-title>
        <ion-buttons slot="end">
          <ion-button (click)="logout()" [attr.aria-label]="'LOGOUT' | translate">
            <ion-icon slot="icon-only" name="log-out-outline" />
          </ion-button>
        </ion-buttons>
      </ion-toolbar>
    </ion-header>
    <ion-content>
      <ion-refresher slot="fixed" (ionRefresh)="refresh($event)">
        <ion-refresher-content />
      </ion-refresher>
      @if (!useDashboard) {
        <p class="widget-note ion-padding">{{ 'DASHBOARD_DISABLED' | translate }}</p>
      } @else if (store.loadFailed()) {
        <p class="widget-note loss ion-padding">{{ 'DASHBOARD_LOAD_ERROR' | translate }}</p>
      } @else if (store.widgets(); as widgets) {
        @for (widget of widgets; track widget.instanceId) {
          <ion-card>
            <ion-card-header class="card-header">
              <ion-card-title>{{ titleKey(widget.type) | translate }}</ion-card-title>
              <ion-button
                fill="clear"
                size="small"
                [routerLink]="['/widget', widget.instanceId]"
                [attr.aria-label]="'DASHBOARD_MAXIMIZE' | translate">
                <ion-icon slot="icon-only" name="expand-outline" />
              </ion-button>
            </ion-card-header>
            <ion-card-content>
              <mobile-widget-body [result]="store.results()[widget.instanceId]" [loading]="store.loading()" />
            </ion-card-content>
          </ion-card>
        } @empty {
          <p class="widget-note ion-padding">{{ 'DASHBOARD_EMPTY' | translate }}</p>
        }
        @if (store.loadedAt(); as loadedAt) {
          <p class="widget-note ion-padding">
            {{ 'DASHBOARD_LOADED_AT' | translate }}: {{ format.format({ loadedAt }, 'loadedAt', dateTime) }}
          </p>
        }
      } @else {
        <div class="widget-loading"><ion-spinner /></div>
      }
    </ion-content>
  `
})
export class DashboardPage implements OnInit {
  protected readonly store = inject(MobileDashboardStore);
  protected readonly format = inject(MobileFormatService);
  private readonly loginService = inject(MobileLoginService);

  protected readonly useDashboard = this.loginService.useDashboard();
  protected readonly titleKey = mobileCardTitleKey;
  protected readonly dateTime = DataType.DateTimeString;

  constructor() {
    addIcons({ expandOutline, logOutOutline });
  }

  /** Reads the dashboard on the first visit; coming back from a full-screen widget keeps what is shown. */
  ngOnInit(): void {
    if (this.useDashboard && !this.store.hasCurrentDashboard()) {
      this.store.reload();
    }
  }

  protected refresh(event: { target: { complete(): void } }): void {
    this.store.reload().then(() => event.target.complete());
  }

  protected logout(): void {
    this.store.clear();
    this.loginService.logout();
  }
}
