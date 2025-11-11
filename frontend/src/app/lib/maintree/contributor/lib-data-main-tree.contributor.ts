import {TreeNode} from 'primeng/api';
import {TypeNodeData} from '../types/type.node.data';
import {AppHelper} from '../../helper/app.helper';
import {BaseSettings} from '../../base.settings';
import {LibTreeNodeType} from '../types/lib.tree.node.type';

/**
 * Library contributor that provides factory methods for creating reusable tree nodes.
 * These nodes represent common functionality that may be needed across different applications.
 *
 * This class does not extend MainTreeContributor - it only provides static factory methods
 * for creating node definitions that can be used by application-specific contributors.
 *
 * Factory methods that create nodes with application-specific behavior accept the tree node type
 * as a parameter, allowing the calling application to specify the appropriate type constant.
 */
export class LibDataMainTreeContributor {

  /**
   * Creates the UDF Metadata General node without children.
   * This node provides access to user-defined field metadata configuration.
   * Application-specific children (like UDF Metadata Security) should be added by the consuming contributor.
   *
   * @param nodeType - Application-specific tree node type identifier (e.g., TreeNodeType.UDFMetadataSecurity)
   * @returns TreeNode configured for UDF metadata management
   */
  static createUdfMetadataGeneralNode(nodeType: string): TreeNode {
    return {
      label: AppHelper.toUpperCaseWithUnderscore(BaseSettings.UDF_METADATA_GENERAL),
      children: [],
      data: new TypeNodeData(
        nodeType,
        LibDataMainTreeContributor.addMainRoute(BaseSettings.UDF_METADATA_GENERAL_KEY),
        null,
        null,
        null
      )
    };
  }

  /**
   * Creates the Global Settings node.
   * This node provides access to system-wide configuration settings.
   * Uses NO_MENU type as it's an informational node without context menu actions.
   *
   * @returns TreeNode configured for global settings access
   */
  static createGlobalSettingsNode(): TreeNode {
    return {
      label: 'GLOBAL_SETTINGS',
      data: new TypeNodeData(
        LibTreeNodeType.NO_MENU,
        LibDataMainTreeContributor.addMainRoute(BaseSettings.GLOBAL_SETTINGS_KEY),
        null,
        null,
        null
      )
    };
  }

  /**
   * Creates the Task Data Monitor node.
   * This node provides access to background task monitoring.
   * Uses NO_MENU type as it's an informational node without context menu actions.
   *
   * @returns TreeNode configured for task monitoring access
   */
  static createTaskDataMonitorNode(): TreeNode {
    return {
      label: 'TASK_DATA_MONITOR',
      data: new TypeNodeData(
        LibTreeNodeType.NO_MENU,
        LibDataMainTreeContributor.addMainRoute(BaseSettings.TASK_DATA_CHANGE_MONITOR_KEY),
        null,
        null,
        null
      )
    };
  }

  /**
   * Creates the Connector API Key node (admin only).
   * This node provides access to API key configuration for external connectors.
   * Uses NO_MENU type as it's an informational node without context menu actions.
   *
   * @returns TreeNode configured for connector API key management
   */
  static createConnectorApiKeyNode(): TreeNode {
    return {
      label: 'CONNECTOR_API_KEY',
      data: new TypeNodeData(
        LibTreeNodeType.NO_MENU,
        LibDataMainTreeContributor.addMainRoute(BaseSettings.CONNECTOR_API_KEY_KEY),
        null,
        null,
        null
      )
    };
  }

  /**
   * Creates the User Settings node (admin only).
   * This node provides access to user entity change limits and permissions.
   * Uses NO_MENU type as it's an informational node without context menu actions.
   *
   * @returns TreeNode configured for user settings management
   */
  static createUserSettingsNode(): TreeNode {
    return {
      label: 'USER_SETTINGS',
      data: new TypeNodeData(
        LibTreeNodeType.NO_MENU,
        LibDataMainTreeContributor.addMainRoute(BaseSettings.USER_ENTITY_LIMIT_KEY),
        null,
        null,
        null
      )
    };
  }

  /**
   * Helper method to construct the full route path.
   */
  private static addMainRoute(suffix: string): string {
    return BaseSettings.MAINVIEW_KEY + '/' + suffix;
  }
}
