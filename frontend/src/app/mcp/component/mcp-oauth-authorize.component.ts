import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonModule } from '@openng/optimus-ui/button';
import { MessageModule } from '@openng/optimus-ui/message';
import { GlobalSessionNames } from '../../lib/global.session.names';
import { BaseSettings } from '../../lib/base.settings';
import { McpOAuthService, OAuthAuthorizationRequest, OAuthValidationResponse } from '../service/mcp-oauth.service';

type PageState = 'checking' | 'consent' | 'error' | 'redirecting';

/**
 * The authorization endpoint of the MCP server's OAuth authorization, as a page. A client such as ChatGPT sends the
 * user here with its authorization request in the query. The page has the request validated, sends the user through
 * the login first when necessary and comes back afterwards, shows what the client asks for and, after the user's
 * decision, returns the browser to the client with a code or an error.
 *
 * <p>
 * A request whose client or redirect address cannot be verified is never redirected; the page shows the error itself,
 * so GT cannot be used to send someone to an arbitrary address.
 * </p>
 */
@Component({
  selector: 'mcp-oauth-authorize',
  standalone: true,
  imports: [TranslateModule, ButtonModule, MessageModule],
  template: `
    <div style="max-width: 640px; margin: 2rem auto; padding: 0 16px">
      <h2>{{ 'MCP_OAUTH_TITLE' | translate }}</h2>
      @switch (state()) {
        @case ('checking') {
          <p>{{ 'MCP_OAUTH_CHECKING' | translate }}</p>
        }
        @case ('redirecting') {
          <p>{{ 'MCP_OAUTH_REDIRECTING' | translate }}</p>
        }
        @case ('error') {
          <p-message severity="error" [text]="errorKey() | translate" />
        }
        @case ('consent') {
          @let request = validation();
          <p>
            <b>{{
              'MCP_OAUTH_QUESTION' | translate: { clientName: request.clientName, clientHost: request.clientHost }
            }}</b>
          </p>
          <p>{{ 'MCP_OAUTH_SCOPE_READ' | translate }}</p>
          <p>{{ 'MCP_OAUTH_DURATION' | translate }}</p>
          <ul>
            <li>{{ 'ACCESS_TOKEN_NOTICE_PROVIDER' | translate }}</li>
            <li>{{ 'ACCESS_TOKEN_NOTICE_LOCAL' | translate }}</li>
            <li>{{ 'ACCESS_TOKEN_NOTICE_REVOKE' | translate }}</li>
            <li>{{ 'ACCESS_TOKEN_NOTICE_SHARED' | translate }}</li>
            <li>{{ 'ACCESS_TOKEN_NOTICE_HOME_TENANT' | translate }}</li>
          </ul>
          <div style="display: flex; gap: 0.5rem">
            <p-button [label]="'MCP_OAUTH_APPROVE' | translate" [disabled]="deciding()" (onClick)="decide(true)" />
            <p-button
              [label]="'MCP_OAUTH_DENY' | translate"
              severity="secondary"
              [disabled]="deciding()"
              (onClick)="decide(false)" />
          </div>
        }
      }
    </div>
  `
})
export class McpOAuthAuthorizeComponent implements OnInit {
  readonly state = signal<PageState>('checking');
  readonly errorKey = signal<string>(null);
  readonly validation = signal<OAuthValidationResponse>(null);
  readonly deciding = signal(false);

  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private mcpOAuthService: McpOAuthService
  ) {}

  ngOnInit(): void {
    if (!sessionStorage.getItem(GlobalSessionNames.JWT)) {
      // The login comes back to this page with the same query
      sessionStorage.setItem(GlobalSessionNames.RETURN_AFTER_LOGIN, this.router.url);
      this.router.navigate(['/' + BaseSettings.LOGIN_KEY]);
      return;
    }
    const q = this.route.snapshot.queryParamMap;
    const request: OAuthAuthorizationRequest = {
      response_type: q.get('response_type'),
      client_id: q.get('client_id'),
      redirect_uri: q.get('redirect_uri'),
      state: q.get('state'),
      code_challenge: q.get('code_challenge'),
      code_challenge_method: q.get('code_challenge_method'),
      scope: q.get('scope'),
      resource: q.get('resource')
    };
    this.mcpOAuthService
      .validate(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => this.handleValidation(response),
        error: (error: HttpErrorResponse) => this.handleValidation(error.error, error.status)
      });
  }

  private handleValidation(response: OAuthValidationResponse, status?: number): void {
    if (response?.authorizationRequestId) {
      this.validation.set(response);
      this.state.set('consent');
    } else if (response?.redirect) {
      this.goTo(response.redirect);
    } else {
      this.showError(status, response?.error);
    }
  }

  private showError(status: number, error: string): void {
    if (status === 404) {
      this.errorKey.set('MCP_OAUTH_ERROR_NOT_AVAILABLE');
    } else if (status === 429 || status === 503) {
      this.errorKey.set('MCP_OAUTH_ERROR_UNAVAILABLE');
    } else {
      this.errorKey.set(error === 'invalid_redirect_uri' ? 'MCP_OAUTH_ERROR_REDIRECT' : 'MCP_OAUTH_ERROR_CLIENT');
    }
    this.state.set('error');
  }

  decide(approve: boolean): void {
    this.deciding.set(true);
    this.mcpOAuthService
      .consent(this.validation().authorizationRequestId, approve)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => this.goTo(response.redirect),
        error: () => this.deciding.set(false)
      });
  }

  private goTo(redirect: string): void {
    this.state.set('redirecting');
    window.location.assign(redirect);
  }
}
