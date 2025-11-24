import {Component, OnDestroy, OnInit} from '@angular/core';
import {LoginService} from '../../login/service/log-in.service';
import {ActivePanelService} from '../service/active.panel.service';
import {TopMenuTypes} from './top.menu.types';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../helper/app.helper';
import {MainDialogService} from '../service/main.dialog.service';
import {ViewSizeChangedService} from '../../layout/service/view.size.changed.service';
import {Location} from '@angular/common';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {UserDataService} from '../service/user.data.service';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {UserSettingsDialogs} from './user-settings-dialogs';
import {Subscription} from 'rxjs';
import {TranslateHelper} from '../../helper/translate.helper';
import {ConfirmationService, MenuItem} from 'primeng/api';
import saveAs from '../../filesaver/filesaver';
import {BaseSettings} from '../../base.settings';
import {HelpIds} from '../../help/help.ids';
import {MenubarModule} from 'primeng/menubar';


/**
 * Represents the menubar of GT
 */
@Component({
  selector: 'menubar',
  template: `
    <p-menubar [model]="this.activePanelService.topMenuItems"></p-menubar>
  `,
  standalone: true,
  imports: [MenubarModule]
})
export class MenubarComponent implements OnInit, OnDestroy {
  menuItems: MenuItem[] = new Array<MenuItem>(4);
  private subscriptionViewSizeChanged: Subscription;

  constructor(public translateService: TranslateService,
    public mainDialogService: MainDialogService,
    private messageToastService: MessageToastService,
    public activePanelService: ActivePanelService,
    private loginService: LoginService,
    private viewSizeChangedService: ViewSizeChangedService,
    private location: Location,
    private gps: GlobalparameterService,
    private userDataService: UserDataService,
    private confirmationService: ConfirmationService) {
    this.activePanelService.topMenuItems = this.menuItems;
  }

  ngOnInit() {
    this.menuItems[TopMenuTypes.COLLAPSE_TREE] = {
      command: (event) => this.toggleMainTree(false)
    };
    this.toggleMainTree(true);
    this.menuItems[TopMenuTypes.SHOW] = {label: 'SHOW', icon: 'fa fa-fw fa-list', visible: true};
    this.menuItems[TopMenuTypes.EDIT] = {label: 'EDIT', icon: 'fa fa-fw fa-edit', visible: true};
    this.menuItems[TopMenuTypes.CUSTOM] = {label: 'XXX', visible: false};
    this.menuItems[TopMenuTypes.SETTINGS] = {
      label: 'SETTINGS', icon: 'fa fa-fw fa-wrench', visible: true,
      items: [
        {
          label: 'PASSWORD_CHANGE' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => this.mainDialogService.visibleDialog(true, UserSettingsDialogs.Password)
        },
        {
          label: 'NICKNAME_LOCALE_CHANGE' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => this.mainDialogService.visibleDialog(true, UserSettingsDialogs.NicknameLocale)
        },
        {label: '_EXPORT_DATA_SQL', command: () => this.downloadPersonalDataAsZip()},
        {label: 'DELETE_MY', command: () => this.deleteMyDataAndUserAccount()}
      ]
    };
    this.menuItems[TopMenuTypes.LOGOUT] = {
      label: 'LOGOUT', icon: 'fa fa-fw fa-minus', visible: true,
      command: (event) => this.loginService.logoutWithLoginView()
    };
    this.menuItems[TopMenuTypes.CONTEXT_HELP] = {
      icon: 'fa fa-fw fa-question-circle', visible: true,
      command: (event) => this.contextHelp()
    };
    TranslateHelper.translateMenuItems(this.menuItems, this.translateService);
    this.subscriptionViewSizeChanged = this.viewSizeChangedService.viewSizeChanged$.subscribe(
      () => this.toggleMainTree(true));
  }


  public async downloadPersonalDataAsZip(): Promise<void> {
    const blob = await this.userDataService.getExportPersonalDataAsZip()
      .catch(error => this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'DOWNLOAD_PERSONAL_DATA_FAILED'));
    if (blob) {
      saveAs(blob, 'gtPersonalData.zip');
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'DOWNLOAD_PERSONAL_DATA_SUCCESS');
    }
  }

  ngOnDestroy(): void {
    this.subscriptionViewSizeChanged && this.subscriptionViewSizeChanged.unsubscribe();
  }

  private deleteMyDataAndUserAccount(): void {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'DELETE_MY_SURE', () => {
        this.userDataService.deleteMyDataAndUserAccount().subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'DELETE_MY_SUCCESS');
          this.loginService.logoutWithLoginView();
        });
      });
  }

  private toggleMainTree(setOnlyIcon: boolean) {
    !setOnlyIcon && this.viewSizeChangedService.toggleMainTree();
    this.menuItems[TopMenuTypes.COLLAPSE_TREE].icon = 'pi ' + (this.viewSizeChangedService.isMainTreeVisible() ?
      'pi-chevron-left' : 'pi-chevron-right');
  }

  private contextHelp() {
    if (this.activePanelService.activatedPanel) {
      const helpIds: string = this.activePanelService.activatedPanel.getHelpContextId();
      if (helpIds) {
        this.gps.toExternalHelpWebpage(this.gps.getUserLang(), helpIds);
      } else {
        // Show first steps
        this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_INTRO);
      }
    } else {
      this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_INTRO);
    }
  }
}
