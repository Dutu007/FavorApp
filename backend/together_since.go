package main

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"time"

	"github.com/jackc/pgx/v5"
)

type togetherSinceResponse struct {
	TogetherSince *string `json:"together_since"`
}

func decodeTogetherSinceRequest(w http.ResponseWriter, r *http.Request) (*string, bool) {
	defer r.Body.Close()
	r.Body = http.MaxBytesReader(w, r.Body, 1024)
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	var request struct {
		TogetherSince json.RawMessage `json:"together_since"`
	}
	if decoder.Decode(&request) != nil || decoder.Decode(new(any)) != io.EOF || len(request.TogetherSince) == 0 {
		errorJSON(w, http.StatusBadRequest, "invalid_together_since_request")
		return nil, false
	}
	var value *string
	if json.Unmarshal(request.TogetherSince, &value) != nil {
		errorJSON(w, http.StatusBadRequest, "invalid_together_since_request")
		return nil, false
	}
	return value, true
}

func validTogetherSince(value *string, now time.Time) bool {
	if value == nil {
		return true
	}
	date, err := time.Parse("2006-01-02", *value)
	if err != nil || date.Year() < 1 || date.Format("2006-01-02") != *value {
		return false
	}
	// The device's calendar date may already be tomorrow in UTC+14.
	latestToday := now.UTC().Add(14 * time.Hour).Format("2006-01-02")
	return *value <= latestToday
}

func (s *server) updateTogetherSince(w http.ResponseWriter, r *http.Request) {
	value, ok := decodeTogetherSinceRequest(w, r)
	if !ok {
		return
	}
	if !validTogetherSince(value, time.Now()) {
		errorJSON(w, http.StatusBadRequest, "invalid_together_since")
		return
	}
	var saved *string
	err := s.db.QueryRow(r.Context(), `
		update couples set together_since=$1::text::date
		where status='active' and (member_a=$2 or member_b=$2)
		returning to_char(together_since, 'YYYY-MM-DD')`, value, userID(r)).Scan(&saved)
	if errors.Is(err, pgx.ErrNoRows) {
		errorJSON(w, http.StatusConflict, "not_matched")
		return
	}
	if err != nil {
		errorJSON(w, http.StatusInternalServerError, "database_error")
		return
	}
	writeJSON(w, http.StatusOK, togetherSinceResponse{TogetherSince: saved})
}
