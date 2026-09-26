package main

import "testing"

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
