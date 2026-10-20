-- Mirrors the section mcp_global_parameters of V0_39_0: the administrator's switch of the MCP server and the public
-- base URL. Both stay closed and unset here; the MCP tests open them for their own run.
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.mcp.use', 0, 0, 'enum:0,1')
  ON DUPLICATE KEY UPDATE input_rule = VALUES(input_rule);
INSERT INTO globalparameters (property_name, property_string, changed_by_system, input_rule)
  VALUES ('gt.mcp.public.base.url', 'none', 0,
  'pattern:^(none|https://[^ ?#]+|http://(localhost|127[.]0[.]0[.]1)(:[0-9]+)?(/[^ ?#]*)?)$')
  ON DUPLICATE KEY UPDATE input_rule = VALUES(input_rule);
