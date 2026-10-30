import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { IonApp, IonRouterOutlet, ToastController } from '@ionic/angular';
import { TranslateService } from '@ngx-translate/core';
import { InfoLevelType } from '../../app/lib/message/info.leve.type';
import { MessageContainer } from '../../app/lib/message/message.container';
import { MessageToastService } from '../../app/lib/message/message.toast.service';

/**
 * Shell of the mobile application. Besides the router outlet it shows the messages of the shared services, such as a
 * failed sign-in or a request the server refused, as Ionic toasts.
 */
@Component({
  selector: 'gt-mobile-root',
  imports: [IonApp, IonRouterOutlet],
  template: `
    <ion-app>
      <ion-router-outlet />
    </ion-app>
  `
})
export class MobileAppComponent {
  private readonly toastController = inject(ToastController);
  private readonly translateService = inject(TranslateService);

  constructor() {
    inject(MessageToastService)
      .showMessageSource$.pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((message) => this.showToast(message));
  }

  private async showToast(message: MessageContainer): Promise<void> {
    const text: string | string[] = message.i8n
      ? this.translateService.instant(message.key, message.interpolateParams)
      : message.key;
    const toast = await this.toastController.create({
      message: Array.isArray(text) ? text.join(' ') : String(text),
      duration: message.infoLevelType === InfoLevelType.ERROR ? 6000 : 3000,
      color: message.infoLevelType === InfoLevelType.ERROR ? 'danger' : 'medium',
      position: 'bottom'
    });
    await toast.present();
  }
}
