import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map, shareReplay, tap } from 'rxjs/operators';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { LoginService } from '../../login/service/log-in.service';
import { MessageToastService } from '../../message/message.toast.service';
import { BaseSettings } from '../../base.settings';
import { GTNetMessageCodeType } from '../model/gtnet.message';
import { GTNetProtocolDescriptor, messageCodeName } from '../model/gtnet.protocol';

/**
 * Holds the GTNet protocol the backend serves, and answers the questions the UI used to answer from its own tables.
 *
 * The descriptors change only when the server is restarted with different code, so they are fetched once and shared;
 * every component that needs them calls {@link load} in its own initialization and the request is made at most once.
 * The lookups are synchronous because they are called while a context menu is being built.
 */
@Injectable()
export class GTNetProtocolService extends AuthServiceWithLogout<any> {
  /** Descriptors by constant name, empty until the first load has come back. */
  private byName = new Map<string, GTNetProtocolDescriptor>();

  /** The in-flight or completed request, shared so that several components cause one call. */
  private loaded$: Observable<GTNetProtocolDescriptor[]>;

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Loads the protocol once and caches it.
   *
   * @returns the descriptors, from the cache after the first call
   */
  load(): Observable<GTNetProtocolDescriptor[]> {
    this.loaded$ ??= this.httpClient
      .get<GTNetProtocolDescriptor[]>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.GT_NET_MESSAGE_KEY}/protocol`,
        this.getHeaders()
      )
      .pipe(
        tap((descriptors) => (this.byName = new Map(descriptors.map((d) => [d.name, d])))),
        catchError(this.handleError.bind(this)),
        shareReplay(1)
      );
    return this.loaded$;
  }

  /**
   * The descriptor of a code.
   *
   * @param messageCode the code as a numeric value or as its constant name
   * @returns the descriptor, or undefined before the protocol is loaded or for an unknown code
   */
  getDescriptor(messageCode: GTNetMessageCodeType | number | string): GTNetProtocolDescriptor | undefined {
    const name = messageCodeName(messageCode);
    return name == null ? undefined : this.byName.get(name);
  }

  /**
   * The codes that legitimately answer the given one.
   *
   * @param messageCode the code being answered
   * @returns the answers, empty when the code is answered by nothing
   */
  getValidResponseCodes(messageCode: GTNetMessageCodeType | number | string): GTNetMessageCodeType[] {
    return (this.getDescriptor(messageCode)?.validResponses ?? []).map(
      (name) => GTNetMessageCodeType[name as keyof typeof GTNetMessageCodeType]
    );
  }

  /**
   * Whether the code may carry a replyTo without being an answer, which is what lets two administrators hold a
   * conversation in admin messages.
   *
   * @param messageCode the code to test
   * @returns true when the code threads
   */
  isThreadable(messageCode: GTNetMessageCodeType | number | string): boolean {
    return this.getDescriptor(messageCode)?.threadable ?? false;
  }

  /**
   * The codes an auto-answer rule may be written for.
   *
   * @returns the request codes, in protocol order
   */
  getAutoAnswerRequestCodes(): GTNetMessageCodeType[] {
    return this.descriptors()
      .filter((d) => d.autoAnswerRequest)
      .map((d) => GTNetMessageCodeType[d.name as keyof typeof GTNetMessageCodeType]);
  }

  /**
   * The answers a rule for the given request may send. A refusal the server issues on its own is left out, so it cannot
   * be configured as a rule.
   *
   * @param requestCode the request the rule is for
   * @returns the answers a rule may choose
   */
  getAutoAnswerResponseCodes(requestCode: GTNetMessageCodeType | number | string): GTNetMessageCodeType[] {
    return (this.getDescriptor(requestCode)?.validResponses ?? [])
      .filter((name) => this.byName.get(name)?.autoAnswerResponse)
      .map((name) => GTNetMessageCodeType[name as keyof typeof GTNetMessageCodeType]);
  }

  /**
   * Every loaded descriptor, in the order the backend serves them.
   *
   * @returns the descriptors, empty before the first load
   */
  descriptors(): GTNetProtocolDescriptor[] {
    return [...this.byName.values()];
  }

  /**
   * Resolves once the protocol is available, for a caller that only wants to wait for it.
   *
   * @returns an observable that completes when the descriptors are in place
   */
  whenLoaded(): Observable<void> {
    return this.byName.size > 0 ? of(undefined) : this.load().pipe(map(() => undefined));
  }
}
