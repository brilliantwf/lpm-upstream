package lpmtailnet

import (
	"net"
	"testing"
)

func TestParseInterfaces(t *testing.T) {
	raw := "wlan0 30 1500 true true false false true | fe80::2f60:2c82:4163:8389/64 10.1.10.131/24\n" +
		"lo 1 65536 true false true false false | ::1/128 127.0.0.1/8\n" +
		"rmnet_data0 21 1500 false false false true false | \n" +
		"garbage line without a bar\n" +
		"bad x 1500 true true false false true | 10.0.0.1/24\n"
	got := parseInterfaces(raw)
	if len(got) != 3 {
		t.Fatalf("got %d interfaces, want 3", len(got))
	}

	wlan := got[0]
	if wlan.Name != "wlan0" || wlan.Index != 30 || wlan.MTU != 1500 {
		t.Errorf("wlan0 = %+v", wlan.Interface)
	}
	if !wlan.IsUp() || wlan.IsLoopback() || wlan.Flags&net.FlagMulticast == 0 || wlan.Flags&net.FlagBroadcast == 0 {
		t.Errorf("wlan0 flags = %v", wlan.Flags)
	}
	addrs, _ := wlan.Addrs()
	if len(addrs) != 2 || addrs[1].String() != "10.1.10.131/24" || addrs[0].String() != "fe80::2f60:2c82:4163:8389/64" {
		t.Errorf("wlan0 addrs = %v", addrs)
	}

	if !got[1].IsLoopback() {
		t.Errorf("lo flags = %v", got[1].Flags)
	}

	cell := got[2]
	if cell.IsUp() || cell.Flags&net.FlagPointToPoint == 0 {
		t.Errorf("rmnet_data0 flags = %v", cell.Flags)
	}
	// An interface without addresses must not fall back to asking the OS.
	if cell.AltAddrs == nil {
		t.Error("rmnet_data0 AltAddrs is nil")
	}
}

func TestCurrentInterfacesFollowsTheLatestSnapshot(t *testing.T) {
	setInterfaces("wlan0 30 1500 true true false false true | 10.1.10.131/24")
	first, err := currentInterfaces()
	if err != nil || len(first) != 1 {
		t.Fatalf("got %v, %v", first, err)
	}
	setInterfaces("")
	empty, err := currentInterfaces()
	if err != nil || len(empty) != 0 {
		t.Fatalf("got %v, %v after an empty snapshot", empty, err)
	}
}
