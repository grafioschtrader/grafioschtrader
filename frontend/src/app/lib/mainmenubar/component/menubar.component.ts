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
import {DialogService} from 'primeng/dynamicdialog';
import {ManageClientService} from '../../manageclient/service/manage-client.service';
import {ClientCreateDynamicComponent} from '../../manageclient/component/client-create-dynamic.component';
import {ManagedClientsTableDialogComponent} from '../../manageclient/component/managed-clients-table.dialog.component';
import {ShareReadAccessDynamicComponent} from '../../manageclient/component/share-read-access-dynamic.component';
import {SharedViewersTableDialogComponent} from '../../manageclient/component/shared-viewers-table.dialog.component';
import {GlobalSessionNames} from '../../global.session.names';


/**
 * Represents the menubar of GT
 */
@Component({
  selector: 'menubar',
  template: `
    <p-menubar [model]="this.activePanelService.topMenuItems"></p-menubar>
  `,
  standalone: true,
  imports: [MenubarModule],
  providers: [DialogService]
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
    private confirmationService: ConfirmationService,
    private manageClientService: ManageClientService,
    private dialogService: DialogService) {
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
    this.addManageClientMenu();
    this.subscriptionViewSizeChanged = this.viewSizeChangedService.viewSizeChanged$.subscribe(
      () => this.toggleMainTree(true));
  }

  /**
   * Adds the "Client" menu directly after the "Settings" menu when the manage-client library feature
   * (g.use.manageclient) is enabled. The contents depend on the user:
   * <ul>
   *   <li>An advisor (ROLE_USER or higher) can create managed clients.</li>
   *   <li>Any non-read-only user can switch into a tenant they manage or that was shared with them, share read access
   *       to their own portfolio, and manage who they shared it with.</li>
   *   <li>A read-only user who has switched into a shared portfolio only gets the way back to their own tenant.</li>
   * </ul>
   */
  private addManageClientMenu(): void {
    if (!this.gps.useManageClient()) {
      return;
    }
    const items: MenuItem[] = [];
    if (this.gps.isReadOnlyUser()) {
      // A read-only user who switched into a portfolio shared with them only needs the way back.
      if (this.isInManagedClient()) {
        items.push(this.backToMyClientItem());
      }
    } else {
      if (this.isAdvisor()) {
        items.push({
          label: 'CREATE_CLIENT' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => this.openCreateClientDialog()
        });
      }
      // Switch into a tenant the user manages or that has been shared with them (read access).
      items.push({
        label: 'SWITCH_TO_CLIENT' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.openManageClientsDialog()
      });
      if (this.isInManagedClient()) {
        items.push(this.backToMyClientItem());
      } else {
        // Sharing your own portfolio is available to any registered user, but only while in your own home tenant.
        items.push({
          label: 'SHARE_READ_ACCESS' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => this.openShareReadAccessDialog()
        });
        items.push({
          label: 'MANAGE_SHARED_VIEWERS' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => this.openSharedViewersDialog()
        });
      }
    }
    if (items.length === 0) {
      return;
    }
    const clientMenu: MenuItem = {label: 'MANAGE_CLIENT', icon: 'fa fa-fw fa-users', visible: true, items};
    TranslateHelper.translateMenuItems([clientMenu], this.translateService);
    // Insert right after the Settings menu.
    this.menuItems.splice(TopMenuTypes.SETTINGS + 1, 0, clientMenu);
    this.refreshTopMenu();
  }

  private backToMyClientItem(): MenuItem {
    return {
      label: 'BACK_TO_MY_CLIENT',
      command: () => this.manageClientService.switchAndReload(
        +sessionStorage.getItem(GlobalSessionNames.MAIN_ID_TENANT), true)
    };
  }

  private openShareReadAccessDialog(): void {
    this.dialogService.open(ShareReadAccessDynamicComponent, {
      header: this.translateService.instant('SHARE_READ_ACCESS'),
      width: '450px', modal: true, closable: true, closeOnEscape: true
    });
  }

  private openSharedViewersDialog(): void {
    this.dialogService.open(SharedViewersTableDialogComponent, {
      header: this.translateService.instant('SHARED_VIEWERS'),
      width: '700px', modal: true, closable: true, closeOnEscape: true
    });
  }

  /** Publishes a fresh array reference so the PrimeNG menubar picks up structural changes. */
  private refreshTopMenu(): void {
    this.menuItems = this.menuItems.slice();
    this.activePanelService.topMenuItems = this.menuItems;
  }

  private openCreateClientDialog(): void {
    this.dialogService.open(ClientCreateDynamicComponent, {
      header: this.translateService.instant('CREATE_CLIENT'),
      width: '450px', modal: true, closable: true, closeOnEscape: true
    });
  }

  private openManageClientsDialog(): void {
    this.dialogService.open(ManagedClientsTableDialogComponent, {
      header: this.translateService.instant('MANAGED_CLIENTS'),
      width: '700px', modal: true, closable: true, closeOnEscape: true
    });
  }

  private isAdvisor(): boolean {
    return this.gps.hasRole(BaseSettings.ROLE_ADMIN) || this.gps.hasRole(BaseSettings.ROLE_ALL_EDIT)
      || this.gps.hasRole(BaseSettings.ROLE_USER);
  }

  private isInManagedClient(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.MAIN_ID_TENANT) != null;
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

  /**
   * Starts the account-deletion flow. When the manage-client feature is enabled the user may still manage clients or
   * share their portfolio with viewers; deletion is then refused by the backend. To avoid asking the user to confirm a
   * deletion that would be rejected afterwards, a read-only pre-check is queried first and an explanatory warning is
   * shown up front. Only when deletion is actually possible (or the feature is off) is the confirmation dialog opened.
   */
  private deleteMyDataAndUserAccount(): void {
    if (!this.gps.useManageClient()) {
      this.confirmAndDelete();
      return;
    }
    this.userDataService.getAccountDeletionEligibility().subscribe(eligibility => {
      switch (eligibility.status) {
        case 'HAS_CLIENTS':
          this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'DELETE_MY_HAS_CLIENTS');
          break;
        case 'HAS_VIEWERS':
          this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'DELETE_MY_HAS_VIEWERS');
          break;
        default:
          this.confirmAndDelete();
      }
    });
  }

  private confirmAndDelete(): void {
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
