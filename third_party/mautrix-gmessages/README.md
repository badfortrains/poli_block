# Local mautrix-gmessages fork

This directory contains the `pkg/libgm` package from
`go.mau.fi/mautrix-gmessages` v0.2605.0 under its upstream license.

The local delta adds a hard deadline to phone RPC response waits. Upstream
otherwise warns after five seconds and then waits forever, which can hold the
Android app's global libgm operation lock indefinitely.
