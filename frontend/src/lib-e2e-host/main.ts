import {HttpClient, provideHttpClient} from '@angular/common/http';
import {importProvidersFrom} from '@angular/core';
import {bootstrapApplication} from '@angular/platform-browser';
import {provideAnimations} from '@angular/platform-browser/animations';
import {provideRouter, Routes} from '@angular/router';
import {TranslateLoader, TranslateModule, TranslateService} from '@ngx-translate/core';
import {AngularSvgIconModule} from 'angular-svg-icon';
import {ConfirmationService, FilterService} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
import {MultiTranslateHttpLoader} from '../app/lib/translator/multi.translate.http.loader';
import {MailForwardSettingTableEditComponent} from '../app/lib/mail/component/mail.forward.setting.table.edit.component';
import {SendRecvTreetableComponent} from '../app/lib/mail/component/send.recv.treetable.component';
import {MailSendRecvService} from '../app/lib/mail/service/mail.send.recv.service';
import {MailSettingForwardService} from '../app/lib/mail/service/mail.setting.forward.service';
import {LoginService} from '../app/lib/login/service/log-in.service';
import {ActivePanelService} from '../app/lib/mainmenubar/service/active.panel.service';
import {MessageToastService} from '../app/lib/message/message.toast.service';
import {GlobalparameterService} from '../app/lib/services/globalparameter.service';
import {UserSettingsService} from '../app/lib/services/user.settings.service';
import {UserAdminService} from '../app/lib/user/service/user.admin.service';
import {LibE2EAppComponent} from './app.component';
import {LibE2ELoginComponent} from './login.component';

const routes: Routes = [
  {path: 'login', component: LibE2ELoginComponent},
  {path: 'mail/mailsendrecv', component: SendRecvTreetableComponent},
  {path: 'mail/mailsettingforward', component: MailForwardSettingTableEditComponent},
  {path: '', pathMatch: 'full', redirectTo: 'login'},
];

// Same single source as the main application. proxy.lib-e2e.conf.json forwards /api to the grafiosch-test-integration
// host on port 8081, which serves this endpoint from the grafiosch-base bundle alone -- exactly the standalone-library
// case that LibraryOnlyNlsCompletenessTest guarantees is complete.
const translateLoader = (http: HttpClient) => new MultiTranslateHttpLoader(http, [
  {prefix: '/api/globalparameters/properties/', suffix: '', mandatory: true},
]);

bootstrapApplication(LibE2EAppComponent, {
  providers: [
    provideAnimations(),
    provideHttpClient(),
    provideRouter(routes),
    importProvidersFrom(
      AngularSvgIconModule.forRoot(),
      TranslateModule.forRoot({
        defaultLanguage: 'en',
        loader: {provide: TranslateLoader, useFactory: translateLoader, deps: [HttpClient]},
      }),
    ),
    ActivePanelService,
    ConfirmationService,
    DialogService,
    FilterService,
    GlobalparameterService,
    LoginService,
    MailSendRecvService,
    MailSettingForwardService,
    MessageToastService,
    UserAdminService,
    UserSettingsService,
  ],
}).then(application => application.injector.get(TranslateService).use('en'))
  .catch(error => console.error(error));
