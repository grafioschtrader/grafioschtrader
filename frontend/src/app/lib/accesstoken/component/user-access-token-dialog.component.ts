import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { DynamicDialogConfig } from '@openng/optimus-ui/dynamicdialog';
import { UserAccessTokenService } from '../service/user-access-token.service';
import { AccessTokenClientInfo } from '../model/user-access-token';
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
    @if (clientInfo()) {
      @if (clientInfo().endpointUrl) {
        <p>
          <b>{{ 'ACCESS_TOKEN_ENDPOINT' | translate }}:</b> <code>{{ clientInfo().endpointUrl }}</code>
        </p>
        @if (configSnippet()) {
          <p>{{ 'ACCESS_TOKEN_CONFIG_EXAMPLE' | translate }}</p>
          <pre style="white-space: pre-wrap; word-break: break-all">{{ configSnippet() }}</pre>
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
export class UserAccessTokenDialogComponent implements OnInit {
  private dynamicDialogConfig = inject<DynamicDialogConfig<UserAccessTokenDialogData>>(DynamicDialogConfig);
  private userAccessTokenService = inject(UserAccessTokenService);

  introKey: string | undefined;
  readonly clientInfo = signal<AccessTokenClientInfo | null>(null);
  readonly configSnippet = computed(() =>
    this.clientInfo()?.configSnippet?.replace('${TIME_ZONE}', Intl.DateTimeFormat().resolvedOptions().timeZone)
  );
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.introKey = this.dynamicDialogConfig.data?.introKey;
    this.userAccessTokenService
      .getClientInfo()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((info) => this.clientInfo.set(info));
  }
}
