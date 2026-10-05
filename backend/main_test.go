package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/base64"
	"testing"
)

func TestPasswordRules(t *testing.T) {
	valid := "StrongPass1!"
	if !validPassword(valid) { t.Fatal("expected valid password") }
	for _, value := range []string{"short1!", "lowercase1!", "UPPERCASE1!", "NoDigits!", "NoSpecial1"} {
		if validPassword(value) { t.Fatalf("expected invalid password %q", value) }
	}
}

func TestPasswordHashRoundTrip(t *testing.T) {
	hash, err := hashPassword("StrongPass1!")
	if err != nil { t.Fatal(err) }
	if !verifyPassword("StrongPass1!", hash) { t.Fatal("expected password to verify") }
	if verifyPassword("WrongPass1!", hash) { t.Fatal("wrong password verified") }
}

func TestSessionTokenHashUsesReturnedToken(t *testing.T) {
	token, stored, err := newSessionToken()
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(stored, tokenHash(token)) {
		t.Fatal("session hash does not match the returned token")
	}
	raw, err := base64.RawURLEncoding.DecodeString(token)
	if err != nil {
		t.Fatal(err)
	}
	legacyHash := sha256.Sum256(raw)
	if bytes.Equal(stored, legacyHash[:]) {
		t.Fatal("session hash uses decoded token bytes instead of the returned token")
	}
}

func TestScoreRulesValidation(t *testing.T) {
	min, max := 0, 100
	if code := validateScoreRules(10, &min, &max, 1, 5, 1, 5); code != "" { t.Fatalf("valid rules rejected: %s", code) }
	if code := validateScoreRules(10, &min, &max, 0, 5, 1, 5); code != "invalid_add_range" { t.Fatalf("expected invalid_add_range, got %s", code) }
	if code := validateScoreRules(10, &min, &max, 1, 5, 6, 5); code != "invalid_subtract_range" { t.Fatalf("expected invalid_subtract_range, got %s", code) }
}

func TestMigrationNumberParsing(t *testing.T) {
	cases := map[string]int{"001_initial.sql": 1, "0001_initial.sql": 1, "010_gift_acceptance.sql": 10, "0010_gift_acceptance.sql": 10}
	for name, want := range cases {
		if got := migrationNumber(name); got != want { t.Fatalf("migrationNumber(%q) = %d, want %d", name, got, want) }
	}
	if got := migrationNumber("notes.txt"); got != -1 { t.Fatalf("expected -1 for unnumbered file, got %d", got) }
}
