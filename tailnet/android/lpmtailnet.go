// Package lpmtailnet is lpm Link's built-in Tailscale node on Android, bound to
// Kotlin with gomobile (`gomobile bind`, see :core:tailnet's build). It is the
// Android counterpart of tailnet/ios: the same node, called through Java.
package lpmtailnet

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"time"

	"lpm.cx/tailnet/node"
)

const (
	commandTimeout = 30 * time.Second
	bridgeWindow   = 30 * time.Second
)

var nd = node.New()

// Start brings the node up from the state kept in dir.
func Start(dir, hostname string) error {
	useLogsDir(dir)
	return nd.Start(dir, hostname)
}

// Stop takes the node offline; it stays signed in.
func Stop() {
	nd.Stop()
}

// Restart brings the node back with fresh connections, for an app whose
// sockets died while Android froze it.
func Restart(dir, hostname string) error {
	useLogsDir(dir)
	return nd.Restart(dir, hostname)
}

// useLogsDir points Tailscale's local log state at the node's own folder. An
// Android app has no home or cache directory in its environment, its working
// directory is / and the default temp directory isn't writable, so Tailscale
// would otherwise find nowhere to put it and panic.
func useLogsDir(dir string) {
	if os.Getenv("TS_LOGS_DIR") == "" {
		os.Setenv("TS_LOGS_DIR", dir)
	}
}

// Login asks for a fresh sign-in URL; it lands in the status.
func Login() error {
	ctx, cancel := context.WithTimeout(context.Background(), commandTimeout)
	defer cancel()
	return nd.Login(ctx)
}

// Logout signs the node out and removes it from the tailnet.
func Logout() error {
	ctx, cancel := context.WithTimeout(context.Background(), commandTimeout)
	defer cancel()
	return nd.Logout(ctx)
}

// WaitStatus blocks until the status changes from version since, or timeoutMs
// passes, and returns it as JSON with its version.
func WaitStatus(since int64, timeoutMs int32) string {
	st, version := nd.Wait(uint64(since), time.Duration(timeoutMs)*time.Millisecond)
	b, err := json.Marshal(struct {
		node.Status
		Version uint64 `json:"version"`
	}{st, version})
	if err != nil {
		return ""
	}
	return string(b)
}

// Bridge returns a loopback port that reaches host:port across the tailnet for
// the next 30 seconds, or -1.
func Bridge(host string, port int32) int32 {
	p, err := nd.Bridge(net.JoinHostPort(host, fmt.Sprint(port)), bridgeWindow)
	if err != nil {
		return -1
	}
	return int32(p)
}

// NetworkChanged hands the node what Android's ConnectivityManager sees: the
// interface the default network uses, its IPv4 gateway, and every interface
// (see parseInterfaces for the format). Android 11+ forbids apps the netlink
// queries Go would otherwise use to find them, so the node only learns about
// networks this way.
func NetworkChanged(defaultInterface, gateway, interfaces string) {
	setInterfaces(interfaces)
	setDefaultRoute(defaultInterface, gateway)
	nd.NetworkChanged()
}
