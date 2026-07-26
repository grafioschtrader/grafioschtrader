import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {HttpClient} from '@angular/common/http';

import {AppSettings} from '../../shared/app.settings';
import {BaseSettings} from '../../lib/base.settings';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {LoginService} from '../../lib/login/service/log-in.service';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {TradingCalendarRuleSet} from '../../entities/trading.calendar.rule.set';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';

@Injectable()
export class TradingCalendarRuleSetService extends AuthServiceWithLogout<TradingCalendarRuleSet>
  implements ServiceEntityUpdate<TradingCalendarRuleSet>, DeleteService {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllRuleSets(): Observable<TradingCalendarRuleSet[]> {
    return <Observable<TradingCalendarRuleSet[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.TRADING_CALENDAR_RULE_SET_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Returns the rule sets as select options for the stock exchange dialog.
   */
  public getRuleSetOptions(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.TRADING_CALENDAR_RULE_SET_KEY}/options`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Returns the rule set id of every MIC that has one, so the stock exchange dialog can preselect the matching rule
   * set as soon as a MIC is chosen.
   */
  public getRuleSetIdByMic(): Observable<{ [mic: string]: number }> {
    return <Observable<{ [mic: string]: number }>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.TRADING_CALENDAR_RULE_SET_KEY}/micmapping`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Checks the YAML without saving it.
   *
   * @param ruleYaml the YAML text to validate
   * @returns the validation messages, empty when the YAML is valid
   */
  public validateYaml(ruleYaml: string): Observable<string[]> {
    return <Observable<string[]>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${AppSettings.TRADING_CALENDAR_RULE_SET_KEY}/validateyaml`,
      ruleYaml, {headers: this.getHeaders().headers, responseType: 'json'})
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Returns the closures of one year with the name of the rule that produced each date. Unsaved YAML may be passed to
   * preview a change before saving it.
   *
   * @param idTradingCalendarRuleSet the rule set to evaluate
   * @param year the year for which the closures are wanted
   * @param ruleYaml unsaved YAML replacing the stored text, may be null
   * @returns map from closure date to rule name
   */
  public previewClosures(idTradingCalendarRuleSet: number, year: number,
    ruleYaml: string): Observable<{ [date: string]: string }> {
    return <Observable<{ [date: string]: string }>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${AppSettings.TRADING_CALENDAR_RULE_SET_KEY}/${idTradingCalendarRuleSet}/preview?year=${year}`,
      ruleYaml, {headers: this.getHeaders().headers, responseType: 'json'})
      .pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idTradingCalendarRuleSet: number): Observable<any> {
    return this.httpClient.delete(
      `${BaseSettings.API_ENDPOINT}${AppSettings.TRADING_CALENDAR_RULE_SET_KEY}/${idTradingCalendarRuleSet}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(ruleSet: TradingCalendarRuleSet): Observable<TradingCalendarRuleSet> {
    return this.updateEntity(ruleSet, ruleSet.idTradingCalendarRuleSet, AppSettings.TRADING_CALENDAR_RULE_SET_KEY);
  }
}
