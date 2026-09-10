package main

import (
	"fmt"
	"os"

	"github.com/political-sms-filter/libgm-proof/internal/gmproof"
)

func main() {
	if err := gmproof.Run(os.Args[1:], os.Stdout, os.Stderr); err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}
}
