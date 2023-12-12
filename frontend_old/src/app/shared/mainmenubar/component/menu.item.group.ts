import {MenuItem} from 'primeng/api';


export interface MenuItemGroup extends MenuItem {
  group: MenuGroup;
}

export enum MenuGroup {
  EOT
}
