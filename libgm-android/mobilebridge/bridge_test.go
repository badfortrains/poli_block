package mobilebridge

import (
	"testing"

	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
)

func TestNewClientRejectsInvalidAndIncompleteAuth(t *testing.T) {
	for _, data := range [][]byte{nil, []byte("not json"), []byte(`{}`)} {
		if _, err := NewClient(data); err == nil {
			t.Fatalf("NewClient(%q) unexpectedly succeeded", data)
		}
	}
}

func TestMessageProjection(t *testing.T) {
	message := &gmproto.Message{
		MessageID:      "message-1",
		ConversationID: "conversation-1",
		Timestamp:      1_700_000_000_000_000,
		MessageStatus:  &gmproto.MessageStatus{Status: gmproto.MessageStatusType_INCOMING_COMPLETE},
		SenderParticipant: &gmproto.Participant{
			FullName: "Test Sender",
		},
		MessageInfo: []*gmproto.MessageInfo{{
			Data: &gmproto.MessageInfo_MessageContent{
				MessageContent: &gmproto.MessageContent{Content: "first"},
			},
		}, {
			Data: &gmproto.MessageInfo_MessageContent{
				MessageContent: &gmproto.MessageContent{Content: "second"},
			},
		}},
	}
	if !isIncoming(message) {
		t.Fatal("incoming message was not recognized")
	}
	if got := messageText(message); got != "first\nsecond" {
		t.Fatalf("messageText = %q", got)
	}
	if got := senderLabel(message); got != "Test Sender" {
		t.Fatalf("senderLabel = %q", got)
	}
}

func TestOutgoingMessageIsNotIncoming(t *testing.T) {
	message := &gmproto.Message{
		MessageStatus: &gmproto.MessageStatus{Status: gmproto.MessageStatusType_OUTGOING_COMPLETE},
	}
	if isIncoming(message) {
		t.Fatal("outgoing message was treated as incoming")
	}
}
