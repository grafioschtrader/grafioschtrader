import { InfoLevelType } from './info.leve.type';

export class MessageContainer {
  /** Translation key of the title; absent for the title of the info level. */
  titleKey?: string;
  /** Title already translated; absent when it is derived from titleKey or the info level. */
  title?: string;
  /** The message contains HTML markup. */
  enableHtml?: boolean;

  constructor(
    public i8n: boolean,
    public infoLevelType: InfoLevelType,
    public key: string | Array<string>,
    public interpolateParams?: any
  ) {}
}
