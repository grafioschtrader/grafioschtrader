import { Component, DestroyRef, inject, Injector, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService, FilterService } from '@openng/optimus-ui/api';
import { ButtonModule } from '@openng/optimus-ui/button';
import { Subscription } from 'rxjs';
import { TableConfigBase } from '../../datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { DataType } from '../../dynamic-form/models/data.type';
import { TranslateValue } from '../../datashowbase/column.config';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { UserSettingsService } from '../../services/user.settings.service';
import { MessageToastService } from '../../message/message.toast.service';
import { InfoLevelType } from '../../message/info.leve.type';
import { AppHelper } from '../../helper/app.helper';
import { OAuthAuthorization } from '../model/user-access-token';
import { UserAccessTokenService } from '../service/user-access-token.service';

/**
 * The applications the user authorized by signing in through OAuth, one row per application. Revoking an application
 * deletes its authorization and thereby all its tokens at once; the application has to ask for a new consent.
 */
@Component({
  selector: 'oauth-authorization-table',
  standalone: true,
  imports: [ConfigurableTableComponent, TranslateModule, ButtonModule],
  template: `
    <h4>{{ 'ACCESS_TOKEN_OAUTH_CLIENTS' | translate }}</h4>
    @if (authorizations().length === 0) {
      <p>{{ 'ACCESS_TOKEN_OAUTH_NONE' | translate }}</p>
    } @else {
      @if (selected()) {
        <p-button
          [label]="'ACCESS_TOKEN_REVOKE' | translate"
          [style]="{ 'margin-bottom': '0.5rem' }"
          (onClick)="revoke(selected())" />
      }
      <configurable-table
        [data]="authorizations()"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        dataKey="idOAuthAuthorization"
        selectionMode="single"
        [(selection)]="selected"
        [multiSortMeta]="multiSortMeta"
        [customSortFn]="customSorter"
        [valueGetterFn]="valueGetter"
        [baseLocale]="baseLocale"
        [scrollable]="false" />
    }
  `
})
export class OAuthAuthorizationTableComponent extends TableConfigBase {
  readonly authorizations = signal<OAuthAuthorization[]>([]);
  readonly selected = signal<OAuthAuthorization>(null);
  private readonly destroyRef = inject(DestroyRef);
  private readDataSub?: Subscription;

  constructor(
    private userAccessTokenService: UserAccessTokenService,
    private confirmationService: ConfirmationService,
    private messageToastService: MessageToastService,
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.addColumn(DataType.String, 'clientName', 'ACCESS_TOKEN_OAUTH_CLIENT_NAME', true, false);
    this.addColumn(DataType.String, 'clientId', 'ACCESS_TOKEN_OAUTH_CLIENT_ID', true, false);
    this.addColumnFeqH(DataType.String, 'scope', true, false, { translateValues: TranslateValue.NORMAL });
    this.addColumnFeqH(DataType.DateTimeString, 'createdAt', true, false);
    this.addColumnFeqH(DataType.DateTimeString, 'lastRefreshAt', true, false);
    this.addColumnFeqH(DataType.DateTimeString, 'expiresAt', true, false);
    this.addColumnFeqH(DataType.Boolean, 'expired', true, false, { templateName: 'check' });
    this.multiSortMeta.push({ field: 'createdAt', order: -1 });
    this.prepareTableAndTranslate();
    this.readData();
  }

  readData(): void {
    this.readDataSub?.unsubscribe();
    this.readDataSub = this.userAccessTokenService
      .getOAuthAuthorizations()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((rows) => {
        this.authorizations.set(rows);
        this.selected.set(null);
        this.createTranslatedValueStoreAndFilterField(rows);
      });
  }

  revoke(authorization: OAuthAuthorization): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|OAUTH_AUTHORIZATION',
      () => {
        if (this.destroyRef.destroyed) return;
        this.userAccessTokenService
          .deleteOAuthAuthorization(authorization.idOAuthAuthorization)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe(() => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
              i18nRecord: 'OAUTH_AUTHORIZATION'
            });
            this.readData();
          });
      }
    );
  }
}
