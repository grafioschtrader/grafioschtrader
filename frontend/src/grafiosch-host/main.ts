import {HttpClient, provideHttpClient, withInterceptorsFromDi} from '@angular/common/http';
import {importProvidersFrom, inject, provideAppInitializer, provideZoneChangeDetection} from '@angular/core';
import {bootstrapApplication} from '@angular/platform-browser';
import {provideAnimations} from '@angular/platform-browser/animations';
import {provideRouter, Routes} from '@angular/router';
import {TranslateLoader, TranslateModule, TranslateService} from '@ngx-translate/core';
import {AngularSvgIconModule} from 'angular-svg-icon';
import {ConfirmationService, FilterService} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
import {ToastrModule} from 'ngx-toastr';
import {firstValueFrom} from 'rxjs';

import {BaseSettings} from '../app/lib/base.settings';
import {GlobalSessionNames} from '../app/lib/global.session.names';
import {AppHelper} from '../app/lib/helper/app.helper';
import {ConnectorApiKeyTableComponent} from '../app/lib/connectorapikey/component/connector.api.key.table.component';
import {SplitLayoutComponent} from '../app/lib/layout/component/split.layout.component';
import {ViewSizeChangedService} from '../app/lib/layout/service/view.size.changed.service';
import {LoginComponent} from '../app/lib/login/component/login.component';
import {RegisterComponent} from '../app/lib/login/component/register.component';
import {RegistrationTokenVerifyComponent} from '../app/lib/login/component/registration.token.verify.component';
import {AfterLoginHandler} from '../app/lib/login/service/after-login.handler';
import {LoginService} from '../app/lib/login/service/log-in.service';
import {ReleaseNoteService} from '../app/lib/login/service/release.note.service';
import {GlobalSettingsTableComponent} from '../app/lib/globalsettings/global.settings.table.component';
import {MailForwardSettingTableEditComponent} from '../app/lib/mail/component/mail.forward.setting.table.edit.component';
import {SendRecvForwardTabMenuComponent} from '../app/lib/mail/component/send.recv.forward.tab.menu.component';
import {SendRecvTreetableComponent} from '../app/lib/mail/component/send.recv.treetable.component';
import {MailSendRecvService} from '../app/lib/mail/service/mail.send.recv.service';
import {MailSettingForwardService} from '../app/lib/mail/service/mail.setting.forward.service';
import {MainDialogService} from '../app/lib/mainmenubar/service/main.dialog.service';
import {ManageClientService} from '../app/lib/manageclient/service/manage-client.service';
import {ActivePanelService} from '../app/lib/mainmenubar/service/active.panel.service';
import {UserDataService} from '../app/lib/mainmenubar/service/user.data.service';
import {MAIN_TREE_CONTRIBUTOR} from '../app/lib/maintree/contributor/main-tree-contributor.interface';
import {MainTreeContributorManager} from '../app/lib/maintree/contributor/main-tree-contributor.manager';
import {DIALOG_HANDLER} from '../app/lib/maintree/handler/dialog-handler.interface';
import {DataChangedService} from '../app/lib/maintree/service/data.changed.service';
import {MainTreeService} from '../app/lib/maintree/service/main-tree.service';
import {TreeNavigationStateService} from '../app/lib/maintree/service/tree.navigation.state.service';
import {MessageToastService} from '../app/lib/message/message.toast.service';
import {ProposeChangeTabMenuComponent} from '../app/lib/proposechange/component/propose.change.tab.menu.component';
import {RequestForYouTableComponent} from '../app/lib/proposechange/component/request.for.you.table.component';
import {YourProposalTableComponent} from '../app/lib/proposechange/component/your.proposal.table.component';
import {ProposeChangeEntityService} from '../app/lib/proposechange/service/propose.change.entity.service';
import {ProposeUserTaskService} from '../app/lib/dynamicdialog/service/propose.user.task.service';
import {ActuatorService} from '../app/lib/services/actuator.service';
import {ConnectorApiKeyService} from '../app/lib/connectorapikey/service/connector.api.key.service';
import {GlobalparameterService} from '../app/lib/services/globalparameter.service';
import {adminGuard, authGuard} from '../app/lib/services/guards.definition';
import {UserSettingsService} from '../app/lib/services/user.settings.service';
import {TabMenuService} from '../app/lib/tabmenu/service/tab.menu.service';
import {TaskDataChangeTableComponent} from '../app/lib/taskdatamonitor/component/task.data.change.table.component';
import {TaskDataChangeService} from '../app/lib/taskdatamonitor/service/task.data.change.service';
import {TASK_EXTENDED_SERVICE} from '../app/lib/taskdatamonitor/service/task.extend.service.token';
import {TASK_TYPE_ENUM} from '../app/lib/taskdatamonitor/service/task.type.enum.token';
import {TaskTypeBase} from '../app/lib/taskdatamonitor/types/task.type.base';
import {MultiTranslateHttpLoader} from '../app/lib/translator/multi.translate.http.loader';
import {showNlsBootstrapFailure} from '../app/lib/translator/nls.bootstrap.failure';
import {UDFMetadataGeneralTableComponent} from '../app/lib/udfmeta/components/udf.metadata.general.table.component';
import {UDFMetadataGeneralService} from '../app/lib/udfmeta/service/udf.metadata.general.service';
import {UserAdminService} from '../app/lib/user/service/user.admin.service';
import {UserTableComponent} from '../app/lib/user/component/user.table.component';
import {UserEntityChangeLimitService} from '../app/lib/user/service/user.entity.change.limit.service';

import {GrafioschAppComponent} from './app.component';
import {GrafioschAfterLoginHandler} from './grafiosch-after-login.handler';
import {GrafioschDialogHandler} from './grafiosch-dialog.handler';
import {GrafioschMainTreeContributor} from './grafiosch-main-tree.contributor';
import {GrafioschSettings} from './grafiosch.settings';
import {GrafioschTenantEditComponent} from './grafiosch-tenant-edit.component';
import {GrafioschTenantService} from './grafiosch-tenant.service';

/**
 * The route table mirrors the shape of Grafioschtrader's: the three public routes of the registration flow, the
 * one-off tenant page, and everything else below the split layout at {@code /mainview}. Every component below comes
 * from {@code src/app/lib} — the paths are the {@code BaseSettings} keys, so a browser spec can address them the same
 * way in both applications.
 */
const routes: Routes = [
  {path: BaseSettings.LOGIN_KEY, component: LoginComponent},
  {path: BaseSettings.REGISTER_KEY, component: RegisterComponent},
  {path: GrafioschSettings.TOKEN_VERIFY_KEY, component: RegistrationTokenVerifyComponent},
  {path: BaseSettings.TENANT_KEY, component: GrafioschTenantEditComponent, canActivate: [authGuard]},
  {
    path: BaseSettings.MAINVIEW_KEY, component: SplitLayoutComponent, canActivate: [authGuard],
    children: [
      {
        path: GrafioschSettings.USER_MESSAGE_KEY, component: SendRecvForwardTabMenuComponent, canActivate: [authGuard],
        children: [
          {path: BaseSettings.MAIL_SEND_RECV_KEY, component: SendRecvTreetableComponent, canActivate: [authGuard]},
          {
            path: BaseSettings.MAIL_SETTING_FORWARD_KEY, component: MailForwardSettingTableEditComponent,
            canActivate: [authGuard]
          }
        ]
      },
      {
        path: GrafioschSettings.PROPOSE_CHANGE_TAB_MENU_KEY, component: ProposeChangeTabMenuComponent,
        canActivate: [authGuard],
        children: [
          {
            path: BaseSettings.PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY, component: RequestForYouTableComponent,
            canActivate: [authGuard]
          },
          {
            path: BaseSettings.PROPOSE_CHANGE_YOUR_PROPOSAL_KEY, component: YourProposalTableComponent,
            canActivate: [authGuard]
          }
        ]
      },
      {path: BaseSettings.UDF_METADATA_GENERAL_KEY, component: UDFMetadataGeneralTableComponent, canActivate: [authGuard]},
      {path: BaseSettings.GLOBAL_SETTINGS_KEY, component: GlobalSettingsTableComponent, canActivate: [authGuard]},
      {path: BaseSettings.TASK_DATA_CHANGE_MONITOR_KEY, component: TaskDataChangeTableComponent, canActivate: [authGuard]},
      {path: BaseSettings.CONNECTOR_API_KEY_KEY, component: ConnectorApiKeyTableComponent, canActivate: [adminGuard]},
      {path: BaseSettings.USER_ENTITY_LIMIT_KEY, component: UserTableComponent, canActivate: [adminGuard]}
    ]
  },
  {path: '', pathMatch: 'full', redirectTo: BaseSettings.LOGIN_KEY}
];

// Same single source as the main application. proxy.grafiosch-host.conf.json forwards /api to the
// grafiosch-test-integration host on port 8081, which serves this endpoint from the grafiosch-base bundle alone --
// exactly the standalone-library case that the NLS module ownership guards exist for.
const translateLoader = (http: HttpClient) => new MultiTranslateHttpLoader(http, [
  {prefix: '/api/globalparameters/properties/', suffix: '', mandatory: true},
]);

bootstrapApplication(GrafioschAppComponent, {
  providers: [
    // Angular 21 bootstraps zoneless unless this is provided, and the library components rely on zone change
    // detection: RegisterComponent assigns applicationInfo in an HTTP callback and expects the @if around
    // <dynamic-form> to have rendered by the time preparePasswordFields() reaches formControl. Without NgZone that
    // never happens, the resulting exception is routed to the subscribe error handler, applicationInfo is reset to
    // null and the page shows nothing but "server unavailable". The main application passes the same provider.
    provideZoneChangeDetection(),
    provideAnimations(),
    provideHttpClient(withInterceptorsFromDi()),
    provideRouter(routes),
    importProvidersFrom(
      AngularSvgIconModule.forRoot(),
      ToastrModule.forRoot({timeOut: 10000, positionClass: 'toast-top-right', preventDuplicates: true}),
      TranslateModule.forRoot({
        defaultLanguage: GrafioschSettings.DEFAULT_LANGUAGE,
        loader: {provide: TranslateLoader, useFactory: translateLoader, deps: [HttpClient]},
      }),
    ),

    // Library services. They are not providedIn: 'root', so every host lists the ones its routes reach - including
    // the ones only the shell needs: the menu bar of SplitLayoutComponent injects ManageClientService and
    // UserDataService, the main tree injects DataChangedService and TreeNavigationStateService. A missing entry
    // surfaces late and unhelpfully, as NG0201 while rendering /mainview.
    ActivePanelService, ActuatorService, ConfirmationService, ConnectorApiKeyService, DataChangedService, DialogService,
    FilterService, GlobalparameterService, LoginService, MailSendRecvService, MailSettingForwardService,
    MainDialogService, ManageClientService, MessageToastService, ProposeChangeEntityService, ProposeUserTaskService,
    ReleaseNoteService, TabMenuService, TaskDataChangeService, TreeNavigationStateService, UDFMetadataGeneralService,
    UserAdminService, UserDataService, UserEntityChangeLimitService, UserSettingsService, ViewSizeChangedService,

    // Host owned.
    GrafioschTenantService,

    // The batch monitor lets an application register its own task types and a service that resolves the entity behind
    // a task. This host has neither, so the library enum is used and the extended service is left unbound.
    {provide: TASK_TYPE_ENUM, useValue: TaskTypeBase},
    {provide: TASK_EXTENDED_SERVICE, useValue: null},

    // The three extension points a host has to implement, see the interfaces in lib/maintree and lib/login.
    {provide: DIALOG_HANDLER, useClass: GrafioschDialogHandler},
    {provide: AfterLoginHandler, useClass: GrafioschAfterLoginHandler},
    MainTreeContributorManager,
    MainTreeService,
    {provide: MAIN_TREE_CONTRIBUTOR, useClass: GrafioschMainTreeContributor, multi: true},

    // Load the user interface texts before anything renders. The backend is their only source since issue #214, so a
    // failure here must stop the bootstrap: without it every component would paint raw translation keys and look
    // broken rather than disconnected.
    provideAppInitializer(() => {
      const translateService = inject(TranslateService);
      const language = AppHelper.getNonUserDefinedLanguage(
        sessionStorage.getItem(GlobalSessionNames.LANGUAGE) ?? translateService.getBrowserLang());
      return firstValueFrom(translateService.use(language)).catch(() => {
        showNlsBootstrapFailure();
        // Never resolves on purpose, so Angular does not continue and render the root behind the overlay.
        return new Promise<never>(() => {});
      });
    }),
  ],
}).catch(error => console.error(error));
