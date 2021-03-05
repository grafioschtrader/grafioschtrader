export class ChangePasswordDTO {
  public oldPassword: string = null;
  public newPassword: string = null;
  public passwordChanged: boolean;
  public message: string;
}
