import {InfoLevelType} from './info.leve.type';

export class MessageContainer {
  titleKey: string;
  title: string;
  enableHtml: boolean;

  constructor(public i8n: boolean, public infoLevelType: InfoLevelType,
              public key: string | Array<string>, public interpolateParams?: any) {
  }
}
