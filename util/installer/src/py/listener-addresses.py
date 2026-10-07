import ipaddress, sys
for line in sys.stdin:
    address, port = line.strip().rsplit(":", 1)
    try:
        value = ipaddress.ip_address(address.strip("[]"))
        if isinstance(value, ipaddress.IPv6Address) and value.ipv4_mapped:
            address = str(value.ipv4_mapped)
    except ValueError:
        pass
    print(address + ":" + port)
