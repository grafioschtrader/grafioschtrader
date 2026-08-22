import { Component, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { MessageToastService } from './message.toast.service';
import { InfoLevelType } from './info.leve.type';
import { TranslateService } from '@ngx-translate/core';
import { MessageContainer } from './message.container';
import { combineLatest, Subscription } from 'rxjs';
import { MessageService, ToastMessageOptions } from '@openng/optimus-ui/api';
import { ToastModule } from '@openng/optimus-ui/toast';

/**
 * Component that bridges application messages to the Optimus UI toast service.
 * Subscribes to message events from MessageToastService and renders the shared toast outlet.
 *
 * Usage: Include once in the root component template (app.component.ts):
 * ```html
 * <toast-message></toast-message>
 * ```
 */
@Component({
  selector: 'toast-message',
  template: `
    <p-toast position="top-right" [preventDuplicates]="true">
      <ng-template pTemplate="message" let-message>
        <div class="p-toast-message-text">
          <div class="p-toast-summary">{{ message.summary }}</div>
          @if (message.data?.enableHtml) {
            <div class="p-toast-detail" [innerHTML]="message.detail"></div>
          } @else {
            <div class="p-toast-detail">{{ message.detail }}</div>
          }
        </div>
      </ng-template>
    </p-toast>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [ToastModule]
})
export class MessageToastComponent implements OnDestroy {
  subscription: Subscription;

  constructor(
    private messageService: MessageService,
    messageToastService: MessageToastService,
    public translateService: TranslateService
  ) {
    this.subscription = messageToastService.showMessageSource$.subscribe((messageConainer) => {
      if (messageConainer.i8n) {
        this.translateInterpolateParams(messageConainer);
      } else if (messageConainer.titleKey) {
        this.translateTitle(messageConainer);
      } else {
        this.showMessage(<string>messageConainer.key, messageConainer.title, messageConainer);
      }
    });
  }

  ngOnDestroy(): void {
    // prevent memory leak when component destroyed
    this.subscription.unsubscribe();
  }

  private getHeaderKey(messageConainer: MessageContainer) {
    if (messageConainer.titleKey) {
      return messageConainer.titleKey;
    } else {
      switch (messageConainer.infoLevelType) {
        case InfoLevelType.SUCCESS:
          return 'SUCCESS';
        case InfoLevelType.INFO:
          return 'INFO';
        case InfoLevelType.WARNING:
          return 'WARNING';
        case InfoLevelType.ERROR:
          return 'ERROR';
      }
    }
  }

  private translateTitle(messageConainer: MessageContainer) {
    this.translateService
      .get(this.getHeaderKey(messageConainer))
      .subscribe((title: string) => this.showMessage(messageConainer.titleKey, title, messageConainer));
  }

  private translateInterpolateParams(messageConainer: MessageContainer) {
    if (messageConainer.interpolateParams) {
      const paramsTranslated = {};

      Object.keys(messageConainer.interpolateParams).forEach((key) => {
        if (key.indexOf('i18n') >= 0) {
          this.translateService
            .get(messageConainer.interpolateParams[key])
            .subscribe((msg) => (paramsTranslated[key] = msg));
        } else {
          paramsTranslated[key] = messageConainer.interpolateParams[key];
        }
      });
      this.translateMessageAndTitle(messageConainer, paramsTranslated);
    } else {
      this.translateMessageAndTitle(messageConainer, messageConainer.interpolateParams);
    }
  }

  private translateMessageAndTitle(messageConainer: MessageContainer, interpolateParams) {
    combineLatest([
      this.translateService.get(messageConainer.key, interpolateParams),
      this.translateService.get(this.getHeaderKey(messageConainer))
    ]).subscribe((messages: string[]) => {
      this.showMessage(messages[0], messages[1], messageConainer);
    });
  }

  private showMessage(message: string, title: string, messageConainer: MessageContainer): void {
    const isError = messageConainer.infoLevelType === InfoLevelType.ERROR;
    const toastMessage: ToastMessageOptions = {
      severity: this.getSeverity(messageConainer.infoLevelType),
      summary: messageConainer.titleKey ? messageConainer.titleKey : title,
      detail: message,
      closable: true,
      sticky: isError,
      life: isError ? undefined : 10000,
      data: { enableHtml: messageConainer.enableHtml || isError }
    };
    this.messageService.add(toastMessage);
  }

  private getSeverity(infoLevelType: InfoLevelType): string {
    switch (infoLevelType) {
      case InfoLevelType.SUCCESS:
        return 'success';
      case InfoLevelType.INFO:
        return 'info';
      case InfoLevelType.WARNING:
        return 'warn';
      case InfoLevelType.ERROR:
        return 'error';
    }
  }
}
