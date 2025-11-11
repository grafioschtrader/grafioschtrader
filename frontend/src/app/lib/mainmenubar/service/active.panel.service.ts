import {Injectable} from '@angular/core';
import {IGlobalMenuAttach} from '../component/iglobal.menu.attach';
import {TopMenuTypes} from '../component/top.menu.types';
import {MenuItem} from 'primeng/api';

/**
 * Service for managing active panels and their associated top menu items in the application.
 * This service coordinates the activation and deactivation of panels (components that implement IGlobalMenuAttach),
 * manages the top menu bar items (Show and Edit menus), and ensures proper cleanup when panels are switched.
 *
 * The service maintains a single active panel at a time and provides lifecycle notifications to panels
 * that use ChangeDetectionStrategy.OnPush, allowing them to react to activation/deactivation events.
 *
 * Usage Pattern:
 * 1. Component registers itself when created: activePanelService.registerPanel(this)
 * 2. Component activates when user interacts: activePanelService.activatePanel(this, {showMenu, editMenu})
 * 3. Component destroys itself on ngOnDestroy: activePanelService.destroyPanel(this)
 */
@Injectable()
export class ActivePanelService {

  /**
   * Reference to the top-level menu items array from the main menu bar.
   * Indexed by TopMenuTypes enum (SHOW=0, EDIT=1) to access specific menu sections.
   */
  topMenuItems: MenuItem[];

  /**
   * Array of panels that have registered for deactivation notifications.
   * Only panels using ChangeDetectionStrategy.OnPush need to register to receive callMeDeactivate() callbacks.
   */
  private registeredPanel: IGlobalMenuAttach[] = [];

  /**
   * Creates the active panel service.
   */
  constructor() {
  }

  /**
   * The currently active panel in the application.
   * Only one panel can be active at a time. When a new panel is activated, the previous one is deactivated.
   * @private
   */
  _activatedPanel: IGlobalMenuAttach;

  /**
   * Gets the currently active panel.
   *
   * @returns The active panel component, or undefined if no panel is active
   */
  get activatedPanel(): IGlobalMenuAttach {
    return this._activatedPanel;
  }

  /**
   * Registers a panel to receive deactivation notifications.
   * Panels using ChangeDetectionStrategy.OnPush should register to receive callMeDeactivate() callbacks
   * when another panel becomes active, allowing them to update their state appropriately.
   *
   * @param componentClass - The panel component to register for deactivation notifications
   */
  registerPanel(componentClass: IGlobalMenuAttach): void {
    this.registeredPanel.push(componentClass);
  }

  /**
   * Destroys a panel and cleans up its resources.
   * If the panel being destroyed is currently active, clears the active panel and removes all menu items.
   * Removes the panel from the registered panels list.
   *
   * This method should be called in the component's ngOnDestroy() lifecycle hook.
   *
   * @param componentClass - The panel component to destroy
   */
  destroyPanel(componentClass: IGlobalMenuAttach): void {
    if (componentClass === this._activatedPanel) {
      this._activatedPanel = null;
      this.setMenuItems(TopMenuTypes.SHOW, null);
      this.setMenuItems(TopMenuTypes.EDIT, null);
    }

    const index = this.registeredPanel.indexOf(componentClass);
    if (index >= 0) {
      this.registeredPanel.splice(index, 1);
    }
  }

  /**
   * Activates a panel and sets its associated menu items in the top menu bar.
   * This method performs several operations:
   * 1. Hides context menu of previously active panel (if different)
   * 2. Sets the new panel as active
   * 3. Updates the Show and Edit menus in the top menu bar
   * 4. Notifies all other registered panels that they have been deactivated
   *
   * @param componentClass - The panel component to activate
   * @param menus - Menu configuration containing Show menu and Edit menu items. Defaults to empty menus.
   */
  activatePanel(componentClass: IGlobalMenuAttach, menus: Menus = {
    editMenu: null,
    showMenu: null
  }): void {
    if (this._activatedPanel && componentClass !== this._activatedPanel) {
      this._activatedPanel.hideContextMenu();
    }

    this._activatedPanel = componentClass;
    this.setMenuItems(TopMenuTypes.SHOW, menus.showMenu);
    this.setMenuItems(TopMenuTypes.EDIT, menus.editMenu);
    this.registeredPanel.filter(panel => panel !== componentClass).forEach(panel => panel.callMeDeactivate());
  }

  /**
   * Checks if the specified panel is currently active.
   *
   * @param componentClass - The panel component to check
   * @returns True if the specified panel is the active panel, false otherwise
   */
  isActivated(componentClass: IGlobalMenuAttach): boolean {
    return componentClass === this._activatedPanel;
  }

  /**
   * Directly updates the Edit menu items for the currently active panel.
   * This is a convenience method for panels that need to dynamically update their edit menu
   * without re-activating the entire panel.
   *
   * @param menuItems - Array of menu items to set in the Edit menu
   */
  addEditItems(menuItems: MenuItem[]): void {
    this.topMenuItems[TopMenuTypes.EDIT].items = menuItems;
  }

  /**
   * Updates a specific menu section (Show or Edit) in the top menu bar.
   * Disables the menu section if no items are provided, otherwise populates it with the given items.
   *
   * @param menuType - The type of menu to update (TopMenuTypes.SHOW or TopMenuTypes.EDIT)
   * @param menuItems - Array of menu items to display, or null/undefined to disable the menu section
   * @private
   */
  private setMenuItems(menuType: TopMenuTypes, menuItems?: MenuItem[]): void {
    this.topMenuItems[menuType].disabled = !menuItems;
    this.topMenuItems[menuType].items = (menuItems) ? menuItems : null;
    this.topMenuItems = [...this.topMenuItems];
  }
}

/**
 * Configuration interface for menu items passed to activatePanel().
 * Defines which menu items should be displayed in the top menu bar when a panel is activated.
 */
export interface Menus {
  /** Menu items to display in the "Show" menu section of the top menu bar */
  showMenu?: MenuItem[];

  /** Menu items to display in the "Edit" menu section of the top menu bar */
  editMenu?: MenuItem[];
}
