import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {LoginService} from '../service/log-in.service';
import {TranslateService} from '@ngx-translate/core';
import {AppSettings} from '../../app.settings';
import {DynamicFormComponent} from '../../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {FormBase} from '../../edit/form.base';
import {DynamicDialogHelper} from '../../dynamicdialog/component/dynamic.dialog.helper';
import {DialogService} from 'primeng/dynamicdialog';
import {ActuatorService, ApplicationInfo} from '../../service/actuator.service';
import {BusinessHelper} from '../../helper/business.helper';
import {HelpIds} from '../../help/help.ids';
import {combineLatest} from 'rxjs';
import {FieldDescriptorInputAndShow} from '../../dynamicfield/field.descriptor.input.and.show';
import {GlobalSessionNames} from '../../global.session.names';
import {DynamicFieldModelHelper} from '../../helper/dynamic.field.model.helper';
import {AppHelper} from '../../helper/app.helper';

/**
 * Shows the login form
 */
@Component({
    template: `
    <div class="container">
      <div class="login jumbotron center-block">
        <div class="alert alert-success" role="alert" *ngIf="successLastRegistration">
          {{ successLastRegistration | translate }}
        </div>
        <application-info [applicationInfo]="applicationInfo"></application-info>
        <ng-container *ngIf="formConfig">
          <h2>{{ 'SIGN_IN' | translate }}</h2>
          <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                        #form="dynamicForm"
                        (submitBt)="submit($event)">
          </dynamic-form>
        </ng-container>
        <p-card header="{{'RELEASE_NOTE' | translate}}">
          <h4>0.33.0</h4>
          {{ 'V_0_33_0' | translate }}
          <h4>0.32.0</h4>
          {{ 'V_0_32_0' | translate }}
          <h4>0.31.0</h4>
          {{ 'V_0_31_0' | translate }}
        </p-card>
      </div>
    </div>
    <password-edit *ngIf="visiblePasswordDialog" [forcePasswordChange]="true"
                   [visibleDialog]="visiblePasswordDialog">
    </password-edit>
  `,
    providers: [DialogService],
    standalone: false
})
export class LoginComponent extends FormBase implements OnInit, OnDestroy {
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;
  applicationInfo: ApplicationInfo;
  queryParams: any;
  successLastRegistration: string;
  visiblePasswordDialog = false;


  constructor(private router: Router,
    private activatedRoute: ActivatedRoute,
    private loginService: LoginService,
    private actuatorService: ActuatorService,
    public translateService: TranslateService,
    private dialogService: DialogService,
    private gps: GlobalparameterService) {
    super();
  }

  ngOnInit(): void {
    combineLatest([this.actuatorService.applicationInfo(), this.gps.getUserFormDefinitions()]).subscribe({
        next: (data: [applicationInfo: ApplicationInfo, fdias: FieldDescriptorInputAndShow[]]) => {
          this.applicationInfo = data[0];
          sessionStorage.setItem(GlobalSessionNames.USER_FORM_DEFINITION, JSON.stringify(data[1]));
          this.loginFormDefinition(data[1]);
        }, error: err => this.applicationInfo = null
      }
    );
  }

  submit(value: { [name: string]: any }): void {
    this.loginService.login(value.email, value.password)
      .subscribe({
        next: (response: Response) => {
          const passwordRegexOk: boolean = this.loginService.afterSuccessfulLogin(response.headers.get('x-auth-token'),
            (response as any).body);
          if (this.gps.getIdTenant()) {
            this.navigateToMainView(passwordRegexOk);
          } else {
            // It is a new tenant -> setup is required
            this.router.navigate([`/${AppSettings.TENANT_KEY}`]);
          }
        }, error: (errorBackend) => {
          this.configObject.submit.disabled = false;

          if (errorBackend.bringUpDialog) {
            DynamicDialogHelper.getOpenedLogoutReleaseRequestDynamicComponent(
              this.translateService, this.dialogService, value.email, value.password);
          }
        }
      });
  }

  private navigateToMainView(passwordRegexOk: boolean): void {
    if (!passwordRegexOk) {
      this.visiblePasswordDialog = true;
      this.configObject.submit.disabled = false;
      this.configObject.password.formControl.setValue('');
    } else {
      this.router.navigate([`/${AppSettings.MAINVIEW_KEY}`]);
    }
  }

  ngOnDestroy(): void {
    this.queryParams.unsubscribe();
  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.translateService.currentLang, HelpIds.HELP_INTRO);
  }

  private loginFormDefinition(fdias: FieldDescriptorInputAndShow[]): void {
    this.formConfig = {
      labelColumns: 2, helpLinkFN: this.helpLink.bind(this), nonModal: true,
      language: AppHelper.getNonUserDefinedLanguage(this.translateService.currentLang)
    };
    this.applicationInfo.users;
    this.config = [];
    this.config.push(DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF('email', fdias));
    this.config.push(DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF('password', fdias));

    if (this.applicationInfo.users.active < this.applicationInfo.users.allowed) {
      this.config.push(DynamicFieldHelper.createFunctionButton('REGISTRATION',
        (e) => this.router.navigate([`/${AppSettings.REGISTER_KEY}`])));
    }
    this.config.push(DynamicFieldHelper.createSubmitButton('SIGN_IN'));
    this.queryParams = this.activatedRoute.params.subscribe(params => this.successLastRegistration = params['success']);
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }
}

export interface ConfigurationWithLogin {
  useFeatures: FeatureType[];
  entityNameWithKeyNameList: EntityNameWithKeyName[];
  cryptocurrencies: string[];
  standardPrecision: { [typename: string]: number };
  currencyPrecision: { [currency: string]: number };
  fieldSize: { [fieldSize: string]: number };
  uiShowMyProperty: boolean;
  mostPrivilegedRole: string;
  passwordRegexOk: boolean;
  udfConfig: UDFConfig
}

export interface EntityNameWithKeyName {
  entityName: string;
  keyName: string;
}

export interface UDFConfig {
  udfGeneralSupportedEntities: string[];
  uDFPrefixSuffixMap: { [udfDatatype: string]: UDFPrefixSuffix };
}

export interface UDFPrefixSuffix {
  prefix: number;
  suffix: number;
  together: number;
}

export enum FeatureType {
  WEBSOCKET,
  ALGO,
  ALERT
}
