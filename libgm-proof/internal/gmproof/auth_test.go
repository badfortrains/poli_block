package gmproof

import (
	"os"
	"path/filepath"
	"testing"

	"go.mau.fi/mautrix-gmessages/pkg/libgm"
)

func TestLoadCookiesAcceptsWrappedPayload(t *testing.T) {
	path := filepath.Join(t.TempDir(), "cookies.json")
	data := `{"type":"gmessages-auth","version":1,"cookies":{"SID":"1","HSID":"2","SSID":"3","OSID":"4","APISID":"5","SAPISID":"6","__Secure-1PSIDTS":"7"}}`
	if err := os.WriteFile(path, []byte(data), 0o600); err != nil {
		t.Fatal(err)
	}
	cookies, err := loadCookies(path)
	if err != nil {
		t.Fatal(err)
	}
	if cookies["SAPISID"] != "6" || cookies["__Secure-1PSIDTS"] != "7" {
		t.Fatalf("unexpected cookies: %#v", cookies)
	}
}

func TestLoadCookiesRejectsMissingRequiredCookie(t *testing.T) {
	path := filepath.Join(t.TempDir(), "cookies.json")
	if err := os.WriteFile(path, []byte(`{"SID":"1"}`), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := loadCookies(path); err == nil {
		t.Fatal("expected missing-cookie error")
	}
}

func TestLoadCookiesRejectsBroadPermissions(t *testing.T) {
	path := filepath.Join(t.TempDir(), "cookies.json")
	data := `{"SID":"1","HSID":"2","SSID":"3","OSID":"4","APISID":"5","SAPISID":"6"}`
	if err := os.WriteFile(path, []byte(data), 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := loadCookies(path); err == nil {
		t.Fatal("expected broad-permissions error")
	}
}

func TestSessionRoundTripUsesPrivatePermissions(t *testing.T) {
	path := filepath.Join(t.TempDir(), "nested", "session.json")
	auth := libgm.NewAuthData()
	if err := saveSession(path, auth); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if got := info.Mode().Perm(); got != 0o600 {
		t.Fatalf("permissions = %#o, want 0600", got)
	}
	if _, err := loadSession(path); err != nil {
		t.Fatal(err)
	}
}
