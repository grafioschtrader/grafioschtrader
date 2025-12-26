import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {GTNetMessageAnswer} from '../model/gtnet.message.answer';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {Observable} from 'rxjs/internal/Observable';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {BaseSettings} from '../../lib/base.settings';

/**
 * Service for managing GTNetMessageAnswer entities via REST API.
 */
@Injectable()
export class GTNetMessageAnswerService extends AuthServiceWithLogout<GTNetMessageAnswer>
  implements ServiceEntityUpdate<GTNetMessageAnswer>, DeleteService {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Retrieves all GTNetMessageAnswer entities from the server.
   */
  getAllGTNetMessageAnswers(): Observable<GTNetMessageAnswer[]> {
    return <Observable<GTNetMessageAnswer[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_MESSAGE_ANSWER_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Creates or updates a GTNetMessageAnswer entity.
   */
  update(gtNetMessageAnswer: GTNetMessageAnswer): Observable<GTNetMessageAnswer> {
    return this.updateEntity(gtNetMessageAnswer, gtNetMessageAnswer.idGtNetMessageAnswer,
      AppSettings.GT_NET_MESSAGE_ANSWER_KEY);
  }

  /**
   * Deletes a GTNetMessageAnswer entity by ID.
   */
  deleteEntity(idGtNetMessageAnswer: number): Observable<any> {
    return this.httpClient.delete(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_MESSAGE_ANSWER_KEY}/${idGtNetMessageAnswer}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
