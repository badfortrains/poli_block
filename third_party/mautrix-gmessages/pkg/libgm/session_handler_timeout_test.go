package libgm

import (
	"strings"
	"testing"
	"time"
)

func TestAwaitResponseHasHardTimeout(t *testing.T) {
	handler := &SessionHandler{
		client:          &Client{pingShortCircuit: make(chan struct{}, 1)},
		responseWaiters: make(map[string]chan<- *IncomingRPCMessage),
	}
	const requestID = "request-id"
	response := handler.waitResponse(requestID)

	_, err := handler.awaitResponse(requestID, response, time.Millisecond, 10*time.Millisecond)

	if err == nil || !strings.Contains(err.Error(), "timed out waiting") {
		t.Fatalf("expected a response timeout, got %v", err)
	}
	if _, exists := handler.responseWaiters[requestID]; exists {
		t.Fatal("timed-out response waiter was not removed")
	}
	if _, open := <-response; open {
		t.Fatal("timed-out response channel was not closed")
	}
}

func TestAwaitResponseReturnsDeliveredMessage(t *testing.T) {
	handler := &SessionHandler{
		client:          &Client{pingShortCircuit: make(chan struct{}, 1)},
		responseWaiters: make(map[string]chan<- *IncomingRPCMessage),
	}
	const requestID = "request-id"
	response := handler.waitResponse(requestID)
	want := &IncomingRPCMessage{}
	response <- want

	got, err := handler.awaitResponse(requestID, response, time.Second, time.Second)

	if err != nil {
		t.Fatalf("unexpected response error: %v", err)
	}
	if got != want {
		t.Fatalf("got response %p, want %p", got, want)
	}
}
