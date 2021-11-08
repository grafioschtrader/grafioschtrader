import {Component, OnDestroy, ViewContainerRef} from '@angular/core';
import {MessageToastService} from './message.toast.service';
import {InfoLevelType} from './info.leve.type';
import {TranslateService} from '@ngx-translate/core';
import {MessageContainer} from './message.container';
import {combineLatest, Subscription} from 'rxjs';
import {ToastrService} from 'ngx-toastr';

@Component({
  selector: 'toast-message',
  template: ``
})
export class MessageToastComponent implements OnDestroy {

  subscription: Subscription;


  constructor(public toastr: ToastrService, vcr: ViewContainerRef, messageToastService: MessageToastService,
              public translateService: TranslateService) {

    // TODO Remove
    // Use with angular v2.2 or above
    // this.toastr.setRootViewContainerRef(vcr);

    this.subscription = messageToastService.showMessageSource$.subscribe(messageConainer => {
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
    this.translateService.get(this.getHeaderKey(messageConainer)).subscribe(
      ((title: string) => this.showMessage(messageConainer.titleKey, title, messageConainer)));
  }

  private translateInterpolateParams(messageConainer: MessageContainer) {
    if (messageConainer.interpolateParams) {
      const paramsTranslated = {};

      Object.keys(messageConainer.interpolateParams).forEach(key => {
        if (key.indexOf('i18n') >= 0) {
          this.translateService.get(messageConainer.interpolateParams[key]).subscribe(msg => paramsTranslated[key] = msg);
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
    combineLatest([this.translateService.get(messageConainer.key,
      interpolateParams), this.translateService.get(this.getHeaderKey(messageConainer))]).subscribe(
      (messages: string[]) => {
        this.showMessage(messages[0], messages[1], messageConainer);
      }
    );
  }

  private showMessage(message: string, title: string, messageConainer: MessageContainer) {
    switch (messageConainer.infoLevelType) {
      case InfoLevelType.SUCCESS:
        this.toastr.success(message, (messageConainer.titleKey) ? messageConainer.titleKey : title,
          {timeOut: 10000, enableHtml: messageConainer.enableHtml, closeButton: true});
        break;
      case InfoLevelType.INFO:
        this.toastr.info(message, (messageConainer.titleKey) ? messageConainer.titleKey : title,
          {timeOut: 10000, enableHtml: messageConainer.enableHtml, closeButton: true});
        break;
      case InfoLevelType.WARNING:
        this.toastr.warning(message, (messageConainer.titleKey) ? messageConainer.titleKey : title,
          {timeOut: 10000, enableHtml: messageConainer.enableHtml, closeButton: true});
        break;
      case InfoLevelType.ERROR:
        this.toastr.error(message, (messageConainer.titleKey) ? messageConainer.titleKey : title,
          {disableTimeOut: true, enableHtml: true, closeButton: true});
        break;
    }

  }

}
