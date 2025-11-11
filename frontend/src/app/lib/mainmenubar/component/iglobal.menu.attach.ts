/**
 * Interface for components that can be attached to the global menu system.
 * Components implementing this interface can provide context-sensitive help.
 */
export interface IGlobalMenuAttach {
  isActivated(): boolean;

  onComponentClick(event): void;

  hideContextMenu(): void;

  callMeDeactivate(): void;

  /**
   * Returns the help context ID for this component.
   * The ID should correspond to a help page in the documentation.
   */
  getHelpContextId(): string;
}
