import json,sys; d=json.load(sys.stdin); sys.exit(not isinstance(d,dict) or d.get("databaseName") != "grafioschtrader" or d.get("activeProfile") not in ("", "production"))
