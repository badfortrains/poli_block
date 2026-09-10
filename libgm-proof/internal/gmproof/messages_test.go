package gmproof

import (
	"testing"

	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
)

func TestExactIncomingCandidate(t *testing.T) {
	message := testMessage("known message", gmproto.MessageStatusType_INCOMING_COMPLETE)
	match, ok := exactIncomingCandidate(message, "known message")
	if !ok || match.MessageID != "message-1" {
		t.Fatalf("match = %#v, %v", match, ok)
	}
}

func TestExactIncomingCandidateRejectsOutgoing(t *testing.T) {
	message := testMessage("known message", gmproto.MessageStatusType_OUTGOING_COMPLETE)
	if _, ok := exactIncomingCandidate(message, "known message"); ok {
		t.Fatal("outgoing message must not match")
	}
}

func TestExactIncomingCandidateRequiresExactText(t *testing.T) {
	message := testMessage("known message plus extra", gmproto.MessageStatusType_INCOMING_COMPLETE)
	if _, ok := exactIncomingCandidate(message, "known message"); ok {
		t.Fatal("partial text must not match")
	}
}

func testMessage(text string, status gmproto.MessageStatusType) *gmproto.Message {
	return &gmproto.Message{
		MessageID:      "message-1",
		ConversationID: "conversation-1",
		Timestamp:      1_700_000_000_000_000,
		MessageStatus:  &gmproto.MessageStatus{Status: status},
		MessageInfo: []*gmproto.MessageInfo{{
			Data: &gmproto.MessageInfo_MessageContent{
				MessageContent: &gmproto.MessageContent{Content: text},
			},
		}},
	}
}
