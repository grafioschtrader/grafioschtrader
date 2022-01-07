export class TwoKeyMap<T> {
  private map: object;

  constructor() {
    this.map = new Object();
  }

  public get(key: string, nestedKey: string): T {
    if (!this.map[key] || this.map[key] && !this.map[key][nestedKey]) {
      return;
    }
    return this.map[key][nestedKey];
  }

  public set(key: string, nestedKey: string, value: T): void {
    if (!this.map[key]) {
      this.map[key] = new Object();
    }
    Object.defineProperty(this.map[key], nestedKey, {value, configurable: true, enumerable: true});
  }

  public remove(key, nestedKey): void {
    if (!this.map[key]) {
      return;
    }
    delete this.map[key][nestedKey];
  }

  public getValues(): T[] {
    return [].concat.apply([], Object.getOwnPropertyNames(this.map).map(key => Object.values(this.map[key])));
  }

  public keys(): string[] {
    return Object.getOwnPropertyNames(this.map);
  }

  public nestedKeys(): Array<string[]> {
    return Object.getOwnPropertyNames(this.map).map(key => Object.keys(this.map[key]));
  }

  public clear(): void {
    Object.getOwnPropertyNames(this.map).forEach(property => {
      delete this.map[property];
    });
  }
}
