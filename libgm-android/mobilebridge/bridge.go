// Package mobilebridge exposes the smallest libgm surface needed by the
// milestone-4 Android proof. The API is intentionally byte-oriented so auth
// JSON does not have to live in immutable Java strings.
package mobilebridge

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/rs/zerolog"
	"go.mau.fi/mautrix-gmessages/pkg/libgm"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
)

// Client wraps one short-lived libgm session. Its methods are serialized
// because gomobile may call exported methods from different Java threads.
type Client struct {
	mu        sync.Mutex
	auth      *libgm.AuthData
	native    *libgm.Client
	connected bool
}

type wireMessage struct {
	MessageID       string `json:"messageId"`
	ConversationID  string `json:"conversationId"`
	Sender          string `json:"sender,omitempty"`
	Text            string `json:"text"`
	TimestampMicros int64  `json:"timestampMicros"`
}

// NewClient decodes persisted libgm AuthData without connecting.
func NewClient(authJSON []byte) (*Client, error) {
	if len(authJSON) == 0 {
		return nil, errors.New("auth data is empty")
	}
	var auth libgm.AuthData
	if err := json.Unmarshal(authJSON, &auth); err != nil {
		return nil, fmt.Errorf("decode auth data: %w", err)
	}
	if auth.Browser == nil || len(auth.TachyonAuthToken) == 0 || auth.RequestCrypto == nil || auth.RefreshKey == nil {
		return nil, errors.New("auth data is incomplete; import a paired libgm session")
	}
	return &Client{auth: &auth}, nil
}

// Connect starts libgm's receive connection. Android must call this off its
// main thread and must eventually call Disconnect.
func (c *Client) Connect() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.connected {
		return errors.New("client is already connected")
	}
	c.native = libgm.NewClient(c.auth, nil, zerolog.Nop())
	if err := c.native.Connect(); err != nil {
		c.native.Disconnect()
		c.native = nil
		return fmt.Errorf("connect libgm: %w", err)
	}
	c.connected = true
	// The tagged libgm API does not expose a connection-ready callback. Its own
	// post-connect setup waits two seconds, so give the long poll the same small
	// settling window before the first request.
	time.Sleep(2 * time.Second)
	return nil
}

// FetchRecentIncomingMessages returns a JSON array containing only incoming
// messages from a small recent inbox window.
func (c *Client) FetchRecentIncomingMessages(conversationCount, messagesPerConversation int) ([]byte, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if err := c.requireConnected(); err != nil {
		return nil, err
	}
	if conversationCount < 1 || conversationCount > 50 || messagesPerConversation < 1 || messagesPerConversation > 50 {
		return nil, errors.New("fetch counts must be between 1 and 50")
	}

	conversations, err := c.native.ListConversations(conversationCount, gmproto.ListConversationsRequest_INBOX)
	if err != nil {
		return nil, fmt.Errorf("list conversations: %w", err)
	}
	result := make([]wireMessage, 0, conversationCount*messagesPerConversation)
	for _, conversation := range conversations.GetConversations() {
		messages, err := c.native.FetchMessages(conversation.GetConversationID(), int64(messagesPerConversation), nil)
		if err != nil {
			return nil, fmt.Errorf("fetch conversation: %w", err)
		}
		for _, message := range messages.GetMessages() {
			if !isIncoming(message) || message.GetMessageID() == "" {
				continue
			}
			result = append(result, wireMessage{
				MessageID:       message.GetMessageID(),
				ConversationID:  message.GetConversationID(),
				Sender:          senderLabel(message),
				Text:            messageText(message),
				TimestampMicros: message.GetTimestamp(),
			})
		}
	}
	return json.Marshal(result)
}

// DeleteMessage deletes exactly one caller-selected message.
func (c *Client) DeleteMessage(messageID string) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if err := c.requireConnected(); err != nil {
		return err
	}
	if strings.TrimSpace(messageID) == "" {
		return errors.New("message ID is empty")
	}
	response, err := c.native.DeleteMessage(messageID)
	if err != nil {
		return fmt.Errorf("delete message: %w", err)
	}
	if !response.GetSuccess() {
		return errors.New("Google Messages returned an unsuccessful delete response")
	}
	return nil
}

// UpdatedAuthData returns the newest AuthData so Android can encrypt and save
// it before disconnecting.
func (c *Client) UpdatedAuthData() ([]byte, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.auth == nil {
		return nil, errors.New("client has no auth data")
	}
	data, err := json.Marshal(c.auth)
	if err != nil {
		return nil, fmt.Errorf("encode updated auth data: %w", err)
	}
	return data, nil
}

// Disconnect tears down the receive connection and is safe to call more than
// once.
func (c *Client) Disconnect() {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.native != nil {
		c.native.Disconnect()
	}
	c.native = nil
	c.connected = false
}

func (c *Client) requireConnected() error {
	if !c.connected || c.native == nil {
		return errors.New("client is not connected")
	}
	return nil
}

func isIncoming(message *gmproto.Message) bool {
	status := int32(message.GetMessageStatus().GetStatus())
	return status >= 100 && status < 200
}

func messageText(message *gmproto.Message) string {
	parts := make([]string, 0, len(message.GetMessageInfo()))
	for _, info := range message.GetMessageInfo() {
		if content := info.GetMessageContent(); content != nil && content.GetContent() != "" {
			parts = append(parts, content.GetContent())
		}
	}
	return strings.Join(parts, "\n")
}

func senderLabel(message *gmproto.Message) string {
	sender := message.GetSenderParticipant()
	if sender == nil {
		return ""
	}
	for _, value := range []string{
		sender.GetFullName(),
		sender.GetFirstName(),
		sender.GetFormattedNumber(),
		sender.GetID().GetNumber(),
	} {
		if value != "" {
			return value
		}
	}
	return ""
}
