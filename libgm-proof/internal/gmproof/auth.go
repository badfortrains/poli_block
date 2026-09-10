package gmproof

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"go.mau.fi/mautrix-gmessages/pkg/libgm"
)

var requiredCookies = []string{"SID", "HSID", "SSID", "OSID", "APISID", "SAPISID"}

func loadCookies(path string) (map[string]string, error) {
	file, err := openPrivateFile(path, "cookies")
	if err != nil {
		return nil, err
	}
	defer file.Close()

	data, err := io.ReadAll(io.LimitReader(file, 1<<20))
	if err != nil {
		return nil, fmt.Errorf("read cookies: %w", err)
	}

	var wrapped struct {
		Cookies map[string]string `json:"cookies"`
	}
	if err := json.Unmarshal(data, &wrapped); err != nil {
		return nil, fmt.Errorf("decode cookies JSON: %w", err)
	}
	cookies := wrapped.Cookies
	if cookies == nil {
		if err := json.Unmarshal(data, &cookies); err != nil {
			return nil, fmt.Errorf("decode cookie map: %w", err)
		}
	}
	if err := validateCookies(cookies); err != nil {
		return nil, err
	}
	return cookies, nil
}

func validateCookies(cookies map[string]string) error {
	var missing []string
	for _, name := range requiredCookies {
		if cookies[name] == "" {
			missing = append(missing, name)
		}
	}
	if len(missing) != 0 {
		return fmt.Errorf("missing required cookies: %v", missing)
	}
	return nil
}

func loadSession(path string) (*libgm.AuthData, error) {
	file, err := openPrivateFile(path, "session")
	if err != nil {
		return nil, err
	}
	defer file.Close()

	var auth libgm.AuthData
	if err := json.NewDecoder(io.LimitReader(file, 8<<20)).Decode(&auth); err != nil {
		return nil, fmt.Errorf("decode session: %w", err)
	}
	return &auth, nil
}

func openPrivateFile(path, description string) (*os.File, error) {
	info, err := os.Lstat(path)
	if err != nil {
		return nil, fmt.Errorf("inspect %s: %w", description, err)
	}
	if info.Mode()&os.ModeSymlink != 0 {
		return nil, fmt.Errorf("refusing to read %s through a symbolic link", description)
	}
	if info.Mode().Perm()&0o077 != 0 {
		return nil, fmt.Errorf("%s permissions are too broad (%#o); run chmod 600 %q", description, info.Mode().Perm(), path)
	}

	file, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("open %s: %w", description, err)
	}
	return file, nil
}

func saveSession(path string, auth *libgm.AuthData) error {
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return fmt.Errorf("create session directory: %w", err)
	}
	if info, err := os.Lstat(path); err == nil && info.Mode()&os.ModeSymlink != 0 {
		return errors.New("refusing to replace a session through a symbolic link")
	} else if err != nil && !errors.Is(err, os.ErrNotExist) {
		return fmt.Errorf("inspect existing session: %w", err)
	}

	temp, err := os.CreateTemp(dir, ".gmproof-session-*.tmp")
	if err != nil {
		return fmt.Errorf("create temporary session: %w", err)
	}
	tempName := temp.Name()
	defer os.Remove(tempName)

	if err := temp.Chmod(0o600); err != nil {
		temp.Close()
		return fmt.Errorf("secure temporary session: %w", err)
	}
	encoder := json.NewEncoder(temp)
	if err := encoder.Encode(auth); err != nil {
		temp.Close()
		return fmt.Errorf("encode session: %w", err)
	}
	if err := temp.Sync(); err != nil {
		temp.Close()
		return fmt.Errorf("flush session: %w", err)
	}
	if err := temp.Close(); err != nil {
		return fmt.Errorf("close session: %w", err)
	}
	if err := os.Rename(tempName, path); err != nil {
		return fmt.Errorf("replace session: %w", err)
	}
	if err := os.Chmod(path, 0o600); err != nil {
		return fmt.Errorf("secure session: %w", err)
	}
	return nil
}
