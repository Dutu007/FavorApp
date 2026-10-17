package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestTogetherSinceValidation(t *testing.T) {
	now := time.Date(2026, 10, 17, 12, 0, 0, 0, time.UTC)
	if !validTogetherSince(nil, now) {
		t.Fatal("clearing the date was rejected")
	}
	for _, value := range []string{"2024-02-29", "2026-10-17", "2026-10-18", "0001-01-01"} {
		if !validTogetherSince(&value, now) {
			t.Errorf("valid calendar date %q was rejected", value)
		}
	}
	for _, value := range []string{"", "2026-02-29", "2026-1-01", "0000-01-01", "2026-10-19", "2026-10-17T00:00:00Z", "infinity"} {
		if validTogetherSince(&value, now) {
			t.Errorf("invalid date %q was accepted", value)
		}
	}
	// Tomorrow in UTC is already today at the easternmost civil time zone.
	tomorrow := "2026-10-18"
	if validTogetherSince(&tomorrow, now.Add(-3*time.Hour)) {
		t.Fatal("future date was accepted before it is today anywhere")
	}
}

func TestTogetherSinceRequestRequiresAnExplicitDateOrNull(t *testing.T) {
	for _, body := range []string{
		`{}`, `null`, `{"together_since":42}`, `{"together_since":false}`,
		`{"together_since":"2024-02-29"} {}`, `{"together_since":null,"couple_id":"other"}`,
		`{"together_since":"` + strings.Repeat("x", 1024) + `"}`,
	} {
		request := httptest.NewRequest(http.MethodPut, "/", strings.NewReader(body))
		response := httptest.NewRecorder()
		if _, ok := decodeTogetherSinceRequest(response, request); ok || response.Code != http.StatusBadRequest {
			t.Errorf("invalid request %s was accepted", body)
		}
	}
	for _, body := range []string{`{"together_since":null}`, `{"together_since":"2024-02-29"}`} {
		request := httptest.NewRequest(http.MethodPut, "/", strings.NewReader(body))
		response := httptest.NewRecorder()
		value, ok := decodeTogetherSinceRequest(response, request)
		if !ok || (body != `{"together_since":null}` && (value == nil || *value != "2024-02-29")) {
			t.Errorf("valid request %s was rejected or misread", body)
		}
	}
}

func TestTogetherSinceSharedLifecycle(t *testing.T) {
	fixture := newAgreementTestFixture(t)
	s := &server{db: fixture.db}
	fixture.mux.Handle("PUT /api/v1/couple/together-since", s.auth(http.HandlerFunc(s.updateTogetherSince)))
	fixture.mux.Handle("GET /api/v1/couple", s.auth(http.HandlerFunc(s.couple)))
	ctx := context.Background()
	if _, err := fixture.db.Exec(ctx, `insert into score_settings(couple_id) select id from couples`); err != nil {
		t.Fatal(err)
	}
	if _, err := fixture.db.Exec(ctx, `
		insert into couple_scores(couple_id,target_user_id,current_score)
		select id,member_a,0 from couples union all select id,member_b,0 from couples`); err != nil {
		t.Fatal(err)
	}
	read := func(user int, want *string) {
		t.Helper()
		response := fixture.request(user, http.MethodGet, "/api/v1/couple", nil)
		var snapshot togetherSinceResponse
		if response.Code != http.StatusOK || json.Unmarshal(response.Body.Bytes(), &snapshot) != nil {
			t.Fatalf("read status=%d, body=%s", response.Code, response.Body)
		}
		if (snapshot.TogetherSince == nil) != (want == nil) || (want != nil && *snapshot.TogetherSince != *want) {
			t.Fatalf("user %d read date %v, want %v", user, snapshot.TogetherSince, want)
		}
	}
	write := func(user int, date *string, wantStatus int) {
		t.Helper()
		response := fixture.request(user, http.MethodPut, "/api/v1/couple/together-since", togetherSinceResponse{date})
		if response.Code != wantStatus {
			t.Fatalf("write status=%d, body=%s", response.Code, response.Body)
		}
	}
	for _, user := range []int{0, 1, 2, 3} {
		read(user, nil)
	}
	date := "2024-02-29"
	write(-1, &date, http.StatusUnauthorized)
	write(4, &date, http.StatusConflict)
	write(0, &date, http.StatusOK)
	read(0, &date)
	read(1, &date)
	read(2, nil)
	read(3, nil)
	newDate := "2025-01-01"
	write(1, &newDate, http.StatusOK)
	read(0, &newDate)
	read(1, &newDate)
	invalid := "2026-02-29"
	write(0, &invalid, http.StatusBadRequest)
	read(0, &newDate)
	write(1, nil, http.StatusOK)
	read(0, nil)
	read(1, nil)
}
