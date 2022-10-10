import {Injectable} from "@angular/core";
import {AuthServiceWithLogout} from "../../shared/login/service/base.auth.service.with.logout";
import {GTNet, GTNetWithMessages} from "../model/gtnet";
import {ServiceEntityUpdate} from "../../shared/edit/service.entity.update";
import {Observable} from "rxjs/internal/Observable";
import {AppSettings} from "../../shared/app.settings";
import {catchError} from "rxjs/operators";

@Injectable()
export class GTNwtService extends AuthServiceWithLogout<GTNet> implements ServiceEntityUpdate<GTNet> {

  getAllGTNetsWithMessages(): Observable<GTNetWithMessages> {
    return <Observable<GTNetWithMessages>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.GTNET_SETUP_KEY}`
      + `/gtnetwithmessage`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(gtNet: GTNet): Observable<GTNet> {
    return this.updateEntity(gtNet, gtNet.idGtNet, AppSettings.GTNET_SETUP_KEY);
  }

  deleteEntity(idGtNet: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.GTNET_SETUP_KEY}/${idGtNet}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
