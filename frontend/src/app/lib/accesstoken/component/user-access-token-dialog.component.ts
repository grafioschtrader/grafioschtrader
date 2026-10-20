import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { DynamicDialogConfig } from '@openng/optimus-ui/dynamicdialog';
import { Subscription } from 'rxjs';
import { UserAccessTokenService } from '../service/user-access-token.service';
import { UserAccessTokenTableComponent } from './user-access-token-table.component';
import { OAuthAuthorizationTableComponent } from './oauth-authorization-table.component';

/** Data an application passes when it opens the dialog through DialogService. */
export interface UserAccessTokenDialogData {
  /** NLS key of an introduction that names the application's use of the tokens */
  introKey?: string;
}

/**
 * Management of the personal access tokens. It explains what a token gives away, shows where a client connects and
 * holds the table of the user's tokens. The application opens it from its settings menu and may add an introduction.
 */
@Component({
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslateModule, UserAccessTokenTableComponent, OAuthAuthorizationTableComponent],
  template: `
    @if (introKey) {
      <p>{{ introKey | translate }}</p>
    }
    <ul>
      <li>{{ 'ACCESS_TOKEN_NOTICE_PROVIDER' | translate }}</li>
      <li>{{ 'ACCESS_TOKEN_NOTICE_LOCAL' | translate }}</li>
      <li>{{ 'ACCESS_TOKEN_NOTICE_REVOKE' | translate }}</li>
      <li>{{ 'ACCESS_TOKEN_NOTICE_SHARED' | translate }}</li>
      <li>{{ 'ACCESS_TOKEN_NOTICE_HOME_TENANT' | translate }}</li>
    </ul>
    @if (clientInfoLoaded) {
      @if (endpointUrl) {
        <p>
          <b>{{ 'ACCESS_TOKEN_ENDPOINT' | translate }}:</b> <code>{{ endpointUrl }}</code>
        </p>
        @if (configSnippet) {
          <p>{{ 'ACCESS_TOKEN_CONFIG_EXAMPLE' | translate }}</p>
          <pre style="white-space: pre-wrap; word-break: break-all">{{ configSnippet }}</pre>
        }
      } @else {
        <p>{{ 'ACCESS_TOKEN_NO_ENDPOINT' | translate }}</p>
      }
    }
    <h4>{{ 'ACCESS_TOKEN_PERSONAL' | translate }}</h4>
    <user-access-token-table />
    <oauth-authorization-table />
  `
})
export class UserAccessTokenDialogComponent implements OnInit, OnDestroy {
  introKey: string;
  endpointUrl: string;
  configSnippet: string;
  clientInfoLoaded = false;
  private subscription: Subscription;

  constructor(
    private dynamicDialogConfig: DynamicDialogConfig<UserAccessTokenDialogData>,
    private userAccessTokenService: UserAccessTokenService
  ) {}

  ngOnInit(): void {
    this.introKey = this.dynamicDialogConfig.data?.introKey;
    this.subscription = this.userAccessTokenService.getClientInfo().subscribe((info) => {
      this.endpointUrl = info.endpointUrl;
      // The example carries a placeholder for the token; the time zone is the browser's, as the tools expect it
      this.configSnippet = info.configSnippet?.replace(
        '${TIME_ZONE}',
        Intl.DateTimeFormat().resolvedOptions().timeZone
      );
      this.clientInfoLoaded = true;
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
