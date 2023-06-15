import {HelpIds} from '../../help/help.ids';

export interface IGlobalMenuAttach {
  isActivated(): boolean;

  onComponentClick(event): void;

  hideContextMenu(): void;

  callMeDeactivate(): void;

  getHelpContextId(): HelpIds;
}
