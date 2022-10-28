import {Observable} from 'rxjs';
import {AppSettings} from '../app.settings';
import {BaseService} from '../login/service/base.service';
import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';


@Injectable()
export class ActuatorService extends BaseService {

  constructor(private httpClient: HttpClient) {
    super();
  }

  public applicationInfo(): Observable<ApplicationInfo> {
    return <Observable<ApplicationInfo>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.ACTUATOR}/info`,
      this.getHeaders());
  }

  public isServerRunning(): Observable<ActuatorHealth> {
    return <Observable<ActuatorHealth>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.ACTUATOR}/health`,
      this.getHeaders());
  }
}

export interface ActuatorHealth {
  status: 'UP' | 'DOWN';
}

export interface Users {
  allowed: number;
  active: number;
}


export interface ApplicationInfo {
  name: string;
  descriptiones: string;
  version: string;
  users: Users;
}

