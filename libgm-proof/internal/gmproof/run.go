package gmproof

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"text/tabwriter"
	"time"

	"github.com/rs/zerolog"
	"go.mau.fi/mautrix-gmessages/pkg/libgm"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
)

const defaultSessionPath = "session.json"

func Run(args []string, stdout, stderr io.Writer) error {
	if len(args) == 0 {
		printUsage(stderr)
		return errors.New("a command is required")
	}

	switch args[0] {
	case "help", "-h", "--help":
		printUsage(stdout)
		return nil
	case "pair":
		return runPair(args[1:], stdout, stderr)
	case "list":
		return runList(args[1:], stdout, stderr)
	case "find":
		return runFind(args[1:], stdout, stderr)
	case "delete":
		return runDelete(args[1:], stdout, stderr)
	default:
		printUsage(stderr)
		return fmt.Errorf("unknown command %q", args[0])
	}
}

func runPair(args []string, stdout, stderr io.Writer) error {
	flags := flag.NewFlagSet("pair", flag.ContinueOnError)
	flags.SetOutput(stderr)
	cookiesPath := flags.String("cookies", "cookies.json", "cookie JSON exported from the /web/config request")
	sessionPath := flags.String("session", defaultSessionPath, "session output path")
	force := flags.Bool("force", false, "replace an existing session")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if flags.NArg() != 0 {
		return errors.New("pair does not accept positional arguments")
	}
	if _, err := os.Lstat(*sessionPath); err == nil && !*force {
		return fmt.Errorf("session %q already exists; use --force to replace it", *sessionPath)
	} else if err != nil && !errors.Is(err, os.ErrNotExist) {
		return fmt.Errorf("inspect session: %w", err)
	}

	cookies, err := loadCookies(*cookiesPath)
	if err != nil {
		return err
	}
	auth := libgm.NewAuthData()
	auth.SetCookies(cookies)
	client := libgm.NewClient(auth, nil, zerolog.Nop())
	defer client.Disconnect()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	fmt.Fprintln(stdout, "Starting Google Messages account pairing…")
	emoji, pairing, err := client.StartGaiaPairing(ctx)
	if err != nil {
		return fmt.Errorf("start pairing: %w", err)
	}
	fmt.Fprintf(stdout, "Approve this emoji in Google Messages: %s\n", emoji)
	if _, err := client.FinishGaiaPairing(ctx, pairing); err != nil {
		return fmt.Errorf("finish pairing: %w", err)
	}
	if err := saveSession(*sessionPath, auth); err != nil {
		return err
	}
	fmt.Fprintf(stdout, "Pairing succeeded; session saved securely to %s\n", *sessionPath)
	return nil
}

func runList(args []string, stdout, stderr io.Writer) error {
	flags := flag.NewFlagSet("list", flag.ContinueOnError)
	flags.SetOutput(stderr)
	sessionPath := flags.String("session", defaultSessionPath, "session path")
	conversationCount := flags.Int("conversations", 10, "number of inbox conversations")
	messageCount := flags.Int64("messages", 10, "messages per conversation")
	showContent := flags.Bool("show-content", false, "print sender names and message text")
	connectWait := flags.Duration("connect-wait", 2*time.Second, "settling time after connection")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *conversationCount < 1 || *conversationCount > 100 || *messageCount < 1 || *messageCount > 100 {
		return errors.New("conversation and message counts must be between 1 and 100")
	}

	return withConnected(*sessionPath, *connectWait, func(client *libgm.Client) error {
		conversations, err := client.ListConversations(*conversationCount, gmproto.ListConversationsRequest_INBOX)
		if err != nil {
			return fmt.Errorf("list conversations: %w", err)
		}
		writer := tabwriter.NewWriter(stdout, 0, 4, 2, ' ', 0)
		defer writer.Flush()
		for _, conversation := range conversations.GetConversations() {
			if *showContent {
				fmt.Fprintf(writer, "CONVERSATION\t%s\t%s\t%s\n", conversation.GetConversationID(), formatTimestamp(conversation.GetLastMessageTimestamp()), conversation.GetName())
			} else {
				fmt.Fprintf(writer, "CONVERSATION\t%s\t%s\n", conversation.GetConversationID(), formatTimestamp(conversation.GetLastMessageTimestamp()))
			}
			messages, err := client.FetchMessages(conversation.GetConversationID(), *messageCount, nil)
			if err != nil {
				return fmt.Errorf("fetch messages for conversation %s: %w", conversation.GetConversationID(), err)
			}
			for _, message := range messages.GetMessages() {
				if *showContent {
					fmt.Fprintf(writer, "  MESSAGE\t%s\t%s\t%s\t%q\t%q\n", message.GetMessageID(), messageDirection(message), messageTime(message).Format(time.RFC3339), senderLabel(message), messageText(message))
				} else {
					fmt.Fprintf(writer, "  MESSAGE\t%s\t%s\t%s\n", message.GetMessageID(), messageDirection(message), messageTime(message).Format(time.RFC3339))
				}
			}
		}
		return nil
	})
}

func runFind(args []string, stdout, stderr io.Writer) error {
	flags := flag.NewFlagSet("find", flag.ContinueOnError)
	flags.SetOutput(stderr)
	sessionPath := flags.String("session", defaultSessionPath, "session path")
	textValue := flags.String("text", "", "exact incoming message text (may be stored in shell history)")
	textFile := flags.String("text-file", "", "file containing exact incoming message text")
	conversationCount := flags.Int("conversations", 20, "number of inbox conversations to search")
	messageCount := flags.Int64("messages", 20, "messages per conversation to search")
	connectWait := flags.Duration("connect-wait", 2*time.Second, "settling time after connection")
	if err := flags.Parse(args); err != nil {
		return err
	}
	target, err := targetText(*textValue, *textFile)
	if err != nil {
		return err
	}
	if *conversationCount < 1 || *conversationCount > 100 || *messageCount < 1 || *messageCount > 100 {
		return errors.New("conversation and message counts must be between 1 and 100")
	}

	return withConnected(*sessionPath, *connectWait, func(client *libgm.Client) error {
		matches, err := findExactIncoming(client, target, *conversationCount, *messageCount)
		if err != nil {
			return err
		}
		if len(matches) == 0 {
			return errors.New("no exact incoming match found; nothing is safe to delete")
		}
		if len(matches) > 1 {
			return fmt.Errorf("found %d exact incoming matches; result is ambiguous and nothing is safe to delete", len(matches))
		}
		match := matches[0]
		fmt.Fprintf(stdout, "Unique exact incoming match:\n  message ID: %s\n  conversation ID: %s\n  timestamp: %s\n", match.MessageID, match.ConversationID, match.Timestamp.Format(time.RFC3339))
		fmt.Fprintf(stdout, "To delete it explicitly, run:\n  gmproof delete --message-id %s --confirm %s\n", match.MessageID, match.MessageID)
		return nil
	})
}

func runDelete(args []string, stdout, stderr io.Writer) error {
	flags := flag.NewFlagSet("delete", flag.ContinueOnError)
	flags.SetOutput(stderr)
	sessionPath := flags.String("session", defaultSessionPath, "session path")
	messageID := flags.String("message-id", "", "exact message ID to delete")
	confirm := flags.String("confirm", "", "repeat the exact message ID to authorize deletion")
	connectWait := flags.Duration("connect-wait", 2*time.Second, "settling time after connection")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *messageID == "" {
		return errors.New("--message-id is required")
	}
	if *confirm != *messageID {
		return errors.New("--confirm must exactly match --message-id; no deletion attempted")
	}

	return withConnected(*sessionPath, *connectWait, func(client *libgm.Client) error {
		response, err := client.DeleteMessage(*messageID)
		if err != nil {
			return fmt.Errorf("delete message: %w", err)
		}
		if !response.GetSuccess() {
			return errors.New("Google Messages returned an unsuccessful delete response")
		}
		fmt.Fprintf(stdout, "DeleteMessage succeeded for %s\n", *messageID)
		return nil
	})
}

func withConnected(sessionPath string, connectWait time.Duration, action func(*libgm.Client) error) (resultErr error) {
	auth, err := loadSession(sessionPath)
	if err != nil {
		return err
	}
	client := libgm.NewClient(auth, nil, zerolog.Nop())
	defer func() {
		if err := saveSession(sessionPath, auth); err != nil {
			resultErr = errors.Join(resultErr, err)
		}
		client.Disconnect()
	}()
	if err := client.Connect(); err != nil {
		return fmt.Errorf("connect: %w", err)
	}
	if connectWait > 0 {
		time.Sleep(connectWait)
	}
	return action(client)
}

func findExactIncoming(client *libgm.Client, target string, conversationCount int, messageCount int64) ([]candidate, error) {
	conversations, err := client.ListConversations(conversationCount, gmproto.ListConversationsRequest_INBOX)
	if err != nil {
		return nil, fmt.Errorf("list conversations: %w", err)
	}
	var matches []candidate
	for _, conversation := range conversations.GetConversations() {
		messages, err := client.FetchMessages(conversation.GetConversationID(), messageCount, nil)
		if err != nil {
			return nil, fmt.Errorf("fetch messages for conversation %s: %w", conversation.GetConversationID(), err)
		}
		for _, message := range messages.GetMessages() {
			if match, ok := exactIncomingCandidate(message, target); ok {
				matches = append(matches, match)
			}
		}
	}
	return matches, nil
}

func targetText(value, path string) (string, error) {
	if (value == "") == (path == "") {
		return "", errors.New("provide exactly one of --text or --text-file")
	}
	if path == "" {
		return value, nil
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("read target text: %w", err)
	}
	return strings.TrimSuffix(string(data), "\n"), nil
}

func formatTimestamp(microseconds int64) string {
	return time.UnixMicro(microseconds).UTC().Format(time.RFC3339)
}

func printUsage(writer io.Writer) {
	fmt.Fprintln(writer, `gmproof: minimal libgm milestone-3 proof

Usage:
  gmproof pair   [--cookies cookies.json] [--session session.json]
  gmproof list   [--session session.json] [--show-content]
  gmproof find   [--session session.json] --text-file target-message.txt
  gmproof delete [--session session.json] --message-id ID --confirm ID

Every network command disconnects after it finishes. Deletion requires an exact,
repeated message ID. Use "find" first; it refuses ambiguous or outgoing matches.`)
}
