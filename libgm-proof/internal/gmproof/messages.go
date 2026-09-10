package gmproof

import (
	"strings"
	"time"

	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
)

func messageText(message *gmproto.Message) string {
	parts := make([]string, 0, len(message.GetMessageInfo()))
	for _, info := range message.GetMessageInfo() {
		if content := info.GetMessageContent(); content != nil && content.GetContent() != "" {
			parts = append(parts, content.GetContent())
		}
	}
	return strings.Join(parts, "\n")
}

func messageDirection(message *gmproto.Message) string {
	status := int32(message.GetMessageStatus().GetStatus())
	switch {
	case status >= 100 && status < 200:
		return "incoming"
	case status > 0 && status < 100:
		return "outgoing"
	default:
		return "other"
	}
}

func messageTime(message *gmproto.Message) time.Time {
	return time.UnixMicro(message.GetTimestamp()).UTC()
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

type candidate struct {
	MessageID      string
	ConversationID string
	Timestamp      time.Time
}

func exactIncomingCandidate(message *gmproto.Message, target string) (candidate, bool) {
	if messageDirection(message) != "incoming" || strings.TrimSpace(messageText(message)) != strings.TrimSpace(target) {
		return candidate{}, false
	}
	return candidate{
		MessageID:      message.GetMessageID(),
		ConversationID: message.GetConversationID(),
		Timestamp:      messageTime(message),
	}, true
}
