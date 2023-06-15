import {Injectable} from '@angular/core';

@Injectable()
export class UserSettingsService {

  /// -----------------------------------------------------------
  /// For array
  /// -----------------------------------------------------------
  public saveSingleValue(propertyKey: string, value: any) {
    localStorage.setItem(propertyKey, JSON.stringify(value));
  }

  public readSingleValue(propertyKey: string): any {
    return JSON.parse(localStorage.getItem(propertyKey));
  }

  /// -----------------------------------------------------------
  /// For array
  /// -----------------------------------------------------------
  public saveArray(propertyKey: string, values: any[] = []) {
    localStorage.setItem(propertyKey, JSON.stringify(values));
  }

  public readArray(propertyKey: string): any[] {
    const value: string = localStorage.getItem(propertyKey);
    return value ? JSON.parse(value) : [];

  }


  /// -----------------------------------------------------------
  /// For objects
  /// -----------------------------------------------------------
  public saveObject(properpyKey: string, data: any): void {
    // Put the object into storage
    localStorage.setItem(properpyKey, JSON.stringify(data));

  }

  public retrieveObject(properpyKey: string): any {
    const retrievedObject = localStorage.getItem(properpyKey);
    return (retrievedObject) ? JSON.parse(retrievedObject) : null;
  }

}


