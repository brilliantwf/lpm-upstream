package lpmtailnet

import (
	"fmt"
	"net"
	"net/netip"
	"strings"
	"sync"

	"tailscale.com/net/netmon"
)

var (
	ifaceMu  sync.Mutex
	ifaceRaw string
)

func init() {
	netmon.RegisterInterfaceGetter(currentInterfaces)
}

func setInterfaces(raw string) {
	ifaceMu.Lock()
	defer ifaceMu.Unlock()
	ifaceRaw = raw
}

func currentInterfaces() ([]netmon.Interface, error) {
	ifaceMu.Lock()
	raw := ifaceRaw
	ifaceMu.Unlock()
	return parseInterfaces(raw), nil
}

// parseInterfaces reads the snapshot the app sends, one interface per line in
// the format Tailscale's own Android app uses:
//
//	wlan0 30 1500 true true false false true | fe80::2f60:2c82:4163:8389/64 10.1.10.131/24
//
// that is name, index, MTU, then up, broadcast, loopback, point-to-point and
// multicast, a bar, and the addresses as prefixes. A line that doesn't parse is
// skipped rather than failing the whole list.
func parseInterfaces(raw string) []netmon.Interface {
	var out []netmon.Interface
	for _, line := range strings.Split(raw, "\n") {
		head, addrs, ok := strings.Cut(line, "|")
		if !ok {
			continue
		}
		var name string
		var index, mtu int
		var up, broadcast, loopback, pointToPoint, multicast bool
		if _, err := fmt.Sscanf(strings.TrimSpace(head), "%s %d %d %t %t %t %t %t",
			&name, &index, &mtu, &up, &broadcast, &loopback, &pointToPoint, &multicast); err != nil {
			continue
		}
		ni := &net.Interface{Name: name, Index: index, MTU: mtu}
		for flag, set := range map[net.Flags]bool{
			net.FlagUp:           up,
			net.FlagRunning:      up,
			net.FlagBroadcast:    broadcast,
			net.FlagLoopback:     loopback,
			net.FlagPointToPoint: pointToPoint,
			net.FlagMulticast:    multicast,
		} {
			if set {
				ni.Flags |= flag
			}
		}
		// Non-nil even when empty: a nil list makes netmon ask the OS, which is
		// the netlink query Android refuses.
		alt := []net.Addr{}
		for _, field := range strings.Fields(addrs) {
			p, err := netip.ParsePrefix(field)
			if err != nil {
				continue
			}
			alt = append(alt, &net.IPNet{
				IP:   p.Addr().AsSlice(),
				Mask: net.CIDRMask(p.Bits(), p.Addr().BitLen()),
			})
		}
		out = append(out, netmon.Interface{Interface: ni, AltAddrs: alt})
	}
	return out
}
