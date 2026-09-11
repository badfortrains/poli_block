package mobilebridge

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/rs/zerolog"
	"go.mau.fi/mautrix-gmessages/pkg/libgm"
)

var requiredCookies = []string{"SID", "HSID", "SSID", "OSID", "APISID", "SAPISID"}

var allowedCookies = append(append([]string{}, requiredCookies...), "__Secure-1PSIDTS")

// PairingClient owns one bounded Google-account pairing attempt. Start returns
// the emoji to display, then Finish waits for the choice made in Messages.
type PairingClient struct {
	mu       sync.Mutex
	auth     *libgm.AuthData
	native   *libgm.Client
	session  *libgm.PairingSession
	ctx      context.Context
	cancel   context.CancelFunc
	started  bool
	finished bool
}

// NewPairingClient validates and minimizes the imported cookie set without
// making a network request.
func NewPairingClient(cookieJSON []byte) (*PairingClient, error) {
	if len(cookieJSON) == 0 {
		return nil, errors.New("cookie data is empty")
	}
	var wrapped struct {
		Cookies map[string]string `json:"cookies"`
	}
	if err := json.Unmarshal(cookieJSON, &wrapped); err != nil {
		return nil, fmt.Errorf("decode cookie data: %w", err)
	}
	cookies := wrapped.Cookies
	if cookies == nil {
		if err := json.Unmarshal(cookieJSON, &cookies); err != nil {
			return nil, fmt.Errorf("decode cookie map: %w", err)
		}
	}
	minimized := make(map[string]string, len(allowedCookies))
	for _, name := range allowedCookies {
		if value := cookies[name]; value != "" {
			minimized[name] = value
		}
	}
	for _, name := range requiredCookies {
		if minimized[name] == "" {
			return nil, fmt.Errorf("missing required cookie %s", name)
		}
	}
	auth := libgm.NewAuthData()
	auth.SetCookies(minimized)
	return &PairingClient{auth: auth}, nil
}

// Start begins pairing and returns the emoji that must be approved in Google
// Messages on the phone.
func (p *PairingClient) Start() (string, error) {
	p.mu.Lock()
	if p.started {
		p.mu.Unlock()
		return "", errors.New("pairing has already started")
	}
	p.ctx, p.cancel = context.WithTimeout(context.Background(), 2*time.Minute)
	p.native = libgm.NewClient(p.auth, nil, zerolog.Nop())
	p.started = true
	native, ctx := p.native, p.ctx
	p.mu.Unlock()

	emoji, session, err := native.StartGaiaPairing(ctx)
	if err != nil {
		p.mu.Lock()
		if p.cancel != nil {
			p.cancel()
		}
		p.native = nil
		p.mu.Unlock()
		native.Disconnect()
		return "", fmt.Errorf("start pairing: %w", err)
	}
	p.mu.Lock()
	p.session = session
	p.mu.Unlock()
	return emoji, nil
}

// Finish waits for the phone confirmation and returns complete AuthData JSON.
func (p *PairingClient) Finish() ([]byte, error) {
	p.mu.Lock()
	if !p.started || p.session == nil || p.native == nil {
		p.mu.Unlock()
		return nil, errors.New("pairing has not started")
	}
	if p.finished {
		p.mu.Unlock()
		return nil, errors.New("pairing has already finished")
	}
	native, session, ctx := p.native, p.session, p.ctx
	p.mu.Unlock()

	if _, err := native.FinishGaiaPairing(ctx, session); err != nil {
		return nil, fmt.Errorf("finish pairing: %w", err)
	}
	data, err := json.Marshal(p.auth)
	if err != nil {
		return nil, fmt.Errorf("encode paired auth data: %w", err)
	}
	p.mu.Lock()
	p.finished = true
	p.mu.Unlock()
	return data, nil
}

// Cancel asks an in-flight Finish call to stop.
func (p *PairingClient) Cancel() {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.cancel != nil {
		p.cancel()
	}
}

// Disconnect releases network resources after Finish returns.
func (p *PairingClient) Disconnect() {
	p.mu.Lock()
	if p.cancel != nil {
		p.cancel()
	}
	native := p.native
	p.native = nil
	p.session = nil
	p.auth = nil
	p.mu.Unlock()
	if native != nil {
		native.Disconnect()
	}
}
