import { HttpClient, provideHttpClient, withInterceptors, withInterceptorsFromDi, withXhr } from '@angular/common/http';
import { authInterceptor } from '../app/lib/login/service/auth.http.interceptor';
import { provideDialogCloseKeepsFocus } from '../app/lib/edit/dialog.close.focus';
import { inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { SETTINGS_MENU_EXTENSIONS } from '../app/lib/mainmenubar/service/settings-menu-extensions';
import { accessTokenSettingsMenu } from './access-token-settings-menu';
import { provideRouter, Routes, withNavigationErrorHandler, withPreloading } from '@angular/router';
import { AuthenticatedPreloadingStrategy } from '../app/lib/login/service/authenticated-preloading.strategy';
import { handleLazyLoadNavigationError } from '../app/lib/login/service/lazy-load-failure';
import { provideTranslateService, TranslateLoader, TranslateService } from '@ngx-translate/core';
import { provideAngularSvgIcon } from 'angular-svg-icon';
import { ConfirmationService, FilterService, MessageService } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { firstValueFrom } from 'rxjs';

import { BaseSettings } from '../app/lib/base.settings';
import { GlobalSessionNames } from '../app/lib/global.session.names';
import { AppHelper } from '../app/lib/helper/app.helper';
import { ConnectorApiKeyTableComponent } from '../app/lib/connectorapikey/component/connector.api.key.table.component';
import { SplitLayoutComponent } from '../app/lib/layout/component/split.layout.component';
import { LoginComponent } from '../app/lib/login/component/login.component';
import { RegisterComponent } from '../app/lib/login/component/register.component';
import { RegistrationTokenVerifyComponent } from '../app/lib/login/component/registration.token.verify.component';
import { AfterLoginHandler } from '../app/lib/login/service/after-login.handler';
import { GlobalSettingsTableComponent } from '../app/lib/globalsettings/global.settings.table.component';
import { GTNetAdminMessagesComponent } from '../app/lib/gnet/component/gtnet-admin-messages.component';
import { GTNetExchangeLogComponent } from '../app/lib/gnet/component/gtnet-exchange-log.component';
import { GTNetExchangeLogTabMenuComponent } from '../app/lib/gnet/component/gtnet-exchange-log-tabmenu.component';
import { GTNetMessageAnswerTableComponent } from '../app/lib/gnet/component/gtnet-message-answer-table.component';
import { GTNetSetupTableComponent } from '../app/lib/gnet/component/gtnet.setup.table.component';
import { GTNetTabMenuComponent } from '../app/lib/gnet/component/gtnet-tabmenu.component';
import { MailForwardSettingTableEditComponent } from '../app/lib/mail/component/mail.forward.setting.table.edit.component';
import { SendRecvForwardTabMenuComponent } from '../app/lib/mail/component/send.recv.forward.tab.menu.component';
import { SendRecvTreetableComponent } from '../app/lib/mail/component/send.recv.treetable.component';
import { MAIN_TREE_CONTRIBUTOR } from '../app/lib/maintree/contributor/main-tree-contributor.interface';
import { DashboardMainTreeContributor } from '../app/lib/maintree/contributor/dashboard-main-tree.contributor';
import { DIALOG_HANDLER } from '../app/lib/maintree/handler/dialog-handler.interface';
import { ProposeChangeTabMenuComponent } from '../app/lib/proposechange/component/propose.change.tab.menu.component';
import { RequestForYouTableComponent } from '../app/lib/proposechange/component/request.for.you.table.component';
import { YourProposalTableComponent } from '../app/lib/proposechange/component/your.proposal.table.component';
import { adminGuard, authGuard } from '../app/lib/services/guards.definition';
import { TabMenuService } from '../app/lib/tabmenu/service/tab.menu.service';
import { TaskDataChangeTableComponent } from '../app/lib/taskdatamonitor/component/task.data.change.table.component';
import { TaskDataChangeTabMenuComponent } from '../app/lib/taskdatamonitor/component/task-data-change-tab-menu.component';
import { TASK_EXTENDED_SERVICE } from '../app/lib/taskdatamonitor/service/task.extend.service.token';
import { TASK_TYPE_ENUM } from '../app/lib/taskdatamonitor/service/task.type.enum.token';
import { TaskTypeBase } from '../app/lib/taskdatamonitor/types/task.type.base';
import { MultiTranslateHttpLoader } from '../app/lib/translator/multi.translate.http.loader';
import { showNlsBootstrapFailure } from '../app/lib/translator/nls.bootstrap.failure';
import { UDFMetadataGeneralTableComponent } from '../app/lib/udfmeta/components/udf.metadata.general.table.component';
import { UserTableComponent } from '../app/lib/user/component/user.table.component';
import { EntityLimitTableComponent } from '../app/lib/entitylimit/component/entity.limit.table.component';

import { GrafioschAppComponent } from './app.component';
import { GrafioschAfterLoginHandler } from './grafiosch-after-login.handler';
import { GrafioschDialogHandler } from './grafiosch-dialog.handler';
import { GrafioschBaseDataMainTreeContributor } from './grafiosch-basedata-main-tree.contributor';
import { GrafioschMainTreeContributor } from './grafiosch-main-tree.contributor';
import { GrafioschSettings } from './grafiosch.settings';
import { GrafioschTenantEditComponent } from './grafiosch-tenant-edit.component';
import { DASHBOARD_ROUTES } from '../app/lib/dashboard/dashboard.routes';

/**
 * The route table mirrors the shape of Grafioschtrader's: the three public routes of the registration flow, the
 * one-off tenant page, and everything else below the split layout at {@code /mainview}. Every component below comes
 * from {@code src/app/lib} — the paths are the {@code BaseSettings} keys, so a browser spec can address them the same
 * way in both applications.
 */
const routes: Routes = [
  { path: BaseSettings.LOGIN_KEY, component: LoginComponent },
  { path: BaseSettings.REGISTER_KEY, component: RegisterComponent },
  {
    path: GrafioschSettings.TOKEN_VERIFY_KEY,
    component: RegistrationTokenVerifyComponent
  },
  {
    path: BaseSettings.TENANT_KEY,
    component: GrafioschTenantEditComponent,
    canActivate: [authGuard]
  },
  {
    path: BaseSettings.MAINVIEW_KEY,
    component: SplitLayoutComponent,
    canActivate: [authGuard],
    children: [
      ...DASHBOARD_ROUTES,
      {
        path: GrafioschSettings.USER_MESSAGE_KEY,
        component: SendRecvForwardTabMenuComponent,
        canActivate: [authGuard],
        children: [
          {
            path: BaseSettings.MAIL_SEND_RECV_KEY,
            component: SendRecvTreetableComponent,
            canActivate: [authGuard]
          },
          {
            path: BaseSettings.MAIL_SETTING_FORWARD_KEY,
            component: MailForwardSettingTableEditComponent,
            canActivate: [authGuard]
          }
        ]
      },
      {
        path: GrafioschSettings.PROPOSE_CHANGE_TAB_MENU_KEY,
        component: ProposeChangeTabMenuComponent,
        canActivate: [authGuard],
        children: [
          {
            path: BaseSettings.PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY,
            component: RequestForYouTableComponent,
            canActivate: [authGuard]
          },
          {
            path: BaseSettings.PROPOSE_CHANGE_YOUR_PROPOSAL_KEY,
            component: YourProposalTableComponent,
            canActivate: [authGuard]
          }
        ]
      },
      {
        path: BaseSettings.UDF_METADATA_GENERAL_KEY,
        component: UDFMetadataGeneralTableComponent,
        canActivate: [authGuard]
      },
      {
        path: BaseSettings.GLOBAL_SETTINGS_KEY,
        component: GlobalSettingsTableComponent,
        canActivate: [authGuard]
      },
      {
        path: GrafioschSettings.GT_NET_TAB_MENU_KEY,
        component: GTNetTabMenuComponent,
        canActivate: [authGuard],
        children: [
          {
            path: BaseSettings.GT_NET_SETUP_KEY,
            component: GTNetSetupTableComponent,
            canActivate: [authGuard]
          },
          {
            path: BaseSettings.GT_NET_ADMIN_MESSAGES_KEY,
            component: GTNetAdminMessagesComponent,
            canActivate: [authGuard]
          }
        ]
      },
      {
        path: BaseSettings.GT_NET_MESSAGE_ANSWER_KEY,
        component: GTNetMessageAnswerTableComponent,
        canActivate: [adminGuard]
      },
      {
        path: BaseSettings.GT_NET_EXCHANGE_LOG_KEY,
        component: GTNetExchangeLogTabMenuComponent,
        canActivate: [authGuard],
        children: [
          {
            path: ':entityKind',
            component: GTNetExchangeLogComponent,
            canActivate: [authGuard]
          }
        ]
      },
      {
        path: BaseSettings.TASK_DATA_CHANGE_MONITOR_KEY,
        component: TaskDataChangeTabMenuComponent,
        canActivate: [authGuard],
        children: [{ path: ':queue', component: TaskDataChangeTableComponent, canActivate: [authGuard] }]
      },
      {
        path: BaseSettings.CONNECTOR_API_KEY_KEY,
        component: ConnectorApiKeyTableComponent,
        canActivate: [adminGuard]
      },
      {
        path: BaseSettings.USER_ADMIN_KEY,
        component: UserTableComponent,
        canActivate: [adminGuard]
      },
      {
        path: BaseSettings.ENTITY_LIMIT_KEY,
        component: EntityLimitTableComponent,
        canActivate: [adminGuard]
      }
    ]
  },
  { path: '', pathMatch: 'full', redirectTo: BaseSettings.LOGIN_KEY }
];

// Same single source as the main application. proxy.grafiosch-host.conf.json forwards /api to the
// grafiosch-test-integration host on port 8081, which serves this endpoint from the grafiosch-base bundle alone --
// exactly the standalone-library case that the NLS module ownership guards exist for.
const translateLoader = (http: HttpClient) =>
  new MultiTranslateHttpLoader(http, [
    {
      prefix: '/api/globalparameters/properties/',
      suffix: '',
      mandatory: true
    }
  ]);

bootstrapApplication(GrafioschAppComponent, {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Angular 22 bootstraps zoneless by default; the library's OnPush components notify every asynchronous change.
    provideHttpClient(withXhr(), withInterceptors([authInterceptor]), withInterceptorsFromDi()),
    provideDialogCloseKeepsFocus(),
    provideRouter(
      routes,
      withPreloading(AuthenticatedPreloadingStrategy),
      withNavigationErrorHandler(handleLazyLoadNavigationError)
    ),
    provideAngularSvgIcon(),
    provideTranslateService({
      fallbackLang: GrafioschSettings.DEFAULT_LANGUAGE,
      loader: {
        provide: TranslateLoader,
        useFactory: translateLoader,
        deps: [HttpClient]
      }
    }),
    { provide: SETTINGS_MENU_EXTENSIONS, useValue: accessTokenSettingsMenu, multi: true },
    ConfirmationService,
    DialogService,
    FilterService,
    MessageService,
    TabMenuService,
    // The batch monitor lets an application register its own task types and a service that resolves the entity behind
    // a task. This host has neither, so the library enum is used and the extended service is left unbound.
    { provide: TASK_TYPE_ENUM, useValue: TaskTypeBase },
    { provide: TASK_EXTENDED_SERVICE, useValue: null },
    // The three extension points a host has to implement, see the interfaces in lib/maintree and lib/login.
    { provide: DIALOG_HANDLER, useClass: GrafioschDialogHandler },
    { provide: AfterLoginHandler, useClass: GrafioschAfterLoginHandler },
    {
      provide: MAIN_TREE_CONTRIBUTOR,
      useClass: DashboardMainTreeContributor,
      multi: true
    },
    {
      provide: MAIN_TREE_CONTRIBUTOR,
      useClass: GrafioschBaseDataMainTreeContributor,
      multi: true
    },
    {
      provide: MAIN_TREE_CONTRIBUTOR,
      useClass: GrafioschMainTreeContributor,
      multi: true
    },
    // Load the user interface texts before anything renders. The backend is their only source since issue #214, so a
    // failure here must stop the bootstrap: without it every component would paint raw translation keys and look
    // broken rather than disconnected.
    provideAppInitializer(() => {
      const translateService = inject(TranslateService);
      const language = AppHelper.getNonUserDefinedLanguage(
        sessionStorage.getItem(GlobalSessionNames.LANGUAGE) ?? translateService.getBrowserLang()
      );
      // The root configures Optimus in the session language. Resolve its fallback first as well: a fallback load
      // started after bootstrap makes translate.get asynchronous while the tree and menus are being assembled.
      return firstValueFrom(translateService.setFallbackLang(GrafioschSettings.DEFAULT_LANGUAGE))
        .then(() => firstValueFrom(translateService.use(language)))
        .catch(() => {
          showNlsBootstrapFailure();
          // Never resolves on purpose, so Angular does not continue and render the root behind the overlay.
          return new Promise<never>(() => {});
        });
    })
  ]
}).catch((error) => console.error(error));
