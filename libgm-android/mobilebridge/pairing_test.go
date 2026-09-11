package mobilebridge

import (
	"encoding/json"
	"testing"
)

func TestNewPairingClientValidatesCookies(t *testing.T) {
	if _, err := NewPairingClient(nil); err == nil {
		t.Fatal("empty cookie data unexpectedly succeeded")
	}
	if _, err := NewPairingClient([]byte(`{"cookies":{"SID":"one"}}`)); err == nil {
		t.Fatal("incomplete cookie data unexpectedly succeeded")
	}
}

func TestNewPairingClientKeepsOnlySupportedCookies(t *testing.T) {
	cookies := map[string]string{"EXTRA": "must-not-survive"}
	for _, name := range requiredCookies {
		cookies[name] = "value-" + name
	}
	cookies["__Secure-1PSIDTS"] = "optional"
	encoded, err := json.Marshal(map[string]any{"cookies": cookies})
	if err != nil {
		t.Fatal(err)
	}
	pairing, err := NewPairingClient(encoded)
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := pairing.auth.Cookies["EXTRA"]; ok {
		t.Fatal("unsupported cookie was retained")
	}
	if got := len(pairing.auth.Cookies); got != len(allowedCookies) {
		t.Fatalf("retained %d cookies, want %d", got, len(allowedCookies))
	}
}
