import {Injectable} from '@angular/core';
import {IGlobalMenuAttach} from '../component/iglobal.menu.attach';
import {TopMenuTypes} from '../component/top.menu.types';
import {MenuGroup, MenuItemGroup} from '../component/menu.item.group';
import {AppSettings} from '../../app.settings';
import {Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {TranslateHelper} from '../../helper/translate.helper';
import {MenuItem} from 'primeng/api';

/**
 * Some menus may be defined in this service because they may be accessed from outside with only the parameter without
 * the route. The route will be taken from the last choosen route.
 */
@Injectable()
export class ActivePanelService {

  topMenuItems: MenuItem[];
  /**
   * Save the state of MenuGroup
   */
  groupLastMenuItem: Map<MenuGroup, string> = new Map();
  /**
   * Only panel which requires a callMeDeactivate must be registered.
   */
  private registeredPanel: IGlobalMenuAttach[] = [];

  constructor(private router: Router, private translateService: TranslateService) {
    this.groupLastMenuItem.set(MenuGroup.EOT, AppSettings.TIME_SERIE_QUOTES);
  }

  _activatedPanel: IGlobalMenuAttach;

  get activatedPanel() {
    return this._activatedPanel;
  }

  /**
   * If a panel use ChangeDetectionStrategy.OnPush, then is should register to be informet when the panel
   * gets deactivated.
   */
  registerPanel(componentClass: IGlobalMenuAttach): void {
    this.registeredPanel.push(componentClass);
  }


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
   * Activate panel and set menus of menu bar
   *
   * @param IGlobalMenuAttach componentClass
   * @param Menus menus
   */
  activatePanel(componentClass: IGlobalMenuAttach, menus: Menus = {
    editMenu: null,
    showMenu: null,
    menuGroup: null,
    menuGroupParam: null
  }): void {
    if (this._activatedPanel && componentClass !== this._activatedPanel) {
      this._activatedPanel.hideContextMenu();
    }

    this._activatedPanel = componentClass;
    if (menus.menuGroup != null) {
      menus.showMenu = (menus.showMenu) ? menus.showMenu : [];
      menus.showMenu.push(...this.addMenuItemsGroup(menus.menuGroup, menus.menuGroupParam));
    }


    this.setMenuItems(TopMenuTypes.SHOW, menus.showMenu);
    this.setMenuItems(TopMenuTypes.EDIT, menus.editMenu);

    this.registeredPanel.filter(panel => panel !== componentClass).forEach(panel => panel.callMeDeactivate());

  }

  isActivated(componentClass: IGlobalMenuAttach): boolean {
    return componentClass === this._activatedPanel;
  }

  addEditItems(menuItems: MenuItem[]) {
    this.topMenuItems[TopMenuTypes.EDIT].items = menuItems;
  }

  public callFromMenuGroup(menuGroup: MenuGroup, param: any, route: string = null): void {
    if (!route) {
      route = this.groupLastMenuItem.get(menuGroup);
    }
    this.navigateToRoute(menuGroup, route, param, false);
  }

  private setMenuItems(menuType: TopMenuTypes, menuItems?: MenuItem[]) {
    this.topMenuItems[menuType].disabled = !menuItems;
    this.topMenuItems[menuType].items = (menuItems) ? menuItems : null;
    this.topMenuItems = [...this.topMenuItems];
  }

  private addMenuItemsGroup(menuGroup: MenuGroup, param: any): MenuItemGroup[] {
    const route: string = this.groupLastMenuItem.get(menuGroup);
    const menuItems: MenuItemGroup[] = [];
    switch (menuGroup) {
      case MenuGroup.EOT:
        break;
    }
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  private navigateToRoute(menuGroup: MenuGroup, route: string, param: any, fromMenu: boolean): void {
    const menuGroupStr = MenuGroup[menuGroup];
    param[menuGroupStr] = fromMenu;
    this.groupLastMenuItem.set(menuGroup, route);
    this.router.navigate([AppSettings.MAINVIEW_KEY + '/', {outlets: {mainbottom: [route, param]}}]);
  }
}

export interface Menus {
  showMenu?: MenuItem[];
  editMenu?: MenuItem[];
  menuGroup?: MenuGroup;
  menuGroupParam?: any;

}
