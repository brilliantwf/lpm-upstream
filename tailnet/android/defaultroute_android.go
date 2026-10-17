package lpmtailnet

import "tailscale.com/net/netmon"

func setDefaultRoute(iface, gateway string) {
	netmon.UpdateLastKnownDefaultRouteInterface(iface)
	netmon.UpdateLastKnownDefaultGateway(gateway)
}
