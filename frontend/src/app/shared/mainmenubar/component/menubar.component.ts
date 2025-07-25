import {Component, OnDestroy, OnInit} from '@angular/core';
import {LoginService} from '../../../lib/login/service/log-in.service';
import {ActivePanelService} from '../service/active.panel.service';
import {TopMenuTypes} from './top.menu.types';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../../lib/helper/app.helper';
import {MainDialogService} from '../service/main.dialog.service';
import {ViewSizeChangedService} from '../../../lib/layout/service/view.size.changed.service';
import {HelpIds} from '../../help/help.ids';
import {Location} from '@angular/common';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {TenantService} from '../../../lib/tenant/service/tenant.service';
import {InfoLevelType} from '../../../lib/message/info.leve.type';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {UserSettingsDialogs} from './main.dialog.component';
import {Subscription} from 'rxjs';
import {TranslateHelper} from '../../../lib/helper/translate.helper';
import {BusinessHelper} from '../../helper/business.helper';
import {ConfirmationService, MenuItem} from 'primeng/api';
import saveAs from '../../../lib/filesaver/filesaver';
import {BaseSettings} from '../../../lib/base.settings';


/**
 * Represents the menubar of GT
 */
@Component({
  selector: 'menubar',
  template: `
    <p-menubar [model]="this.activePanelService.topMenuItems"></p-menubar>
  `,
  standalone: false
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
    private tenantService: TenantService,
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
    const blob = await this.tenantService.getExportPersonalDataAsZip()
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
        this.tenantService.deleteMyDataAndUserAccount().subscribe(response => {
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
      const helpIds: HelpIds = this.activePanelService.activatedPanel.getHelpContextId();
      if (helpIds) {
        BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), helpIds);
      } else {
        // Show first steps
        BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_INTRO);
      }
    } else {
      BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_INTRO);
    }
  }
}

