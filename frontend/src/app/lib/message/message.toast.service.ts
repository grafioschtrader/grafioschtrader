import {Injectable} from '@angular/core';
import {InfoLevelType} from './info.leve.type';
import {MessageContainer} from './message.container';
import {Subject} from 'rxjs';

@Injectable()
export class MessageToastService {

  private showMessageSource = new Subject<MessageContainer>();
  showMessageSource$ = this.showMessageSource.asObservable();


  /**
   * Message of a certain type is showing, nothing gets translated. It support of optional parameters, which will not be translated.
   *
   * @param infoLevelType Type of message.
   * @param message Translated message.
   * @param interpolateParams Parameters which will not be translated.
   */
  showMessage(infoLevelType: InfoLevelType, message: string | Array<string>, interpolateParams?: any) {
    this.showMessageSource.next(new MessageContainer(false, infoLevelType, message, interpolateParams));
  }

  showMessageTitle(infoLevelType: InfoLevelType, title: string, key: string | Array<string>, interpolateParams?: any) {
    const mc = new MessageContainer(false, infoLevelType, key, interpolateParams);
    mc.title = title;
    this.showMessageSource.next(mc);
  }

  /**
   * Message of a certain type is showing, everything gets translated.
   * It support of optional parameters, which will be translated when necessary.
   *
   * @param infoLevelType Type of message.
   * @param key Message key of the untranslated message.
   * @param interpolateParams Parameters which will be translated when parameter name starts with 'i18n'.
   */
  showMessageI18n(infoLevelType: InfoLevelType, key: string | Array<string>, interpolateParams?: any) {
    this.showMessageSource.next(new MessageContainer(true, infoLevelType, key, interpolateParams));
  }

  showMessageI18nEnableHtml(infoLevelType: InfoLevelType, key: string | Array<string>, interpolateParams?: any) {
    const messageContainer: MessageContainer = new MessageContainer(true, infoLevelType, key, interpolateParams);
    messageContainer.enableHtml = true;
    this.showMessageSource.next(messageContainer);
  }

  showMessageI18nTitle(infoLevelType: InfoLevelType, titleKey: string, key: string | Array<string>, interpolateParams?: any) {
    const mc = new MessageContainer(true, infoLevelType, key, interpolateParams);
    mc.titleKey = titleKey;
    this.showMessageSource.next(mc);
  }

}
