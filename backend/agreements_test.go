package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"reflect"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

func TestAgreementValidation(t *testing.T) {
	validDate, invalidDate, looseDate, yearZero := "2028-02-29", "2026-02-29", "2026-1-01", "0000-01-01"
	cases := []struct {
		name, title, note string
		date              *string
		want              string
	}{
		{"multiline Unicode boundaries", strings.Repeat("海", 40), strings.Repeat("爱\n", 1000), &validDate, ""},
		{"empty title", "", "", nil, "invalid_agreement_title"},
		{"long Unicode title", strings.Repeat("海", 41), "", nil, "invalid_agreement_title"},
		{"long note", "看海", strings.Repeat("爱", 2001), nil, "agreement_note_too_long"},
		{"invalid calendar date", "看海", "", &invalidDate, "invalid_agreement_date"},
		{"non ISO date", "看海", "", &looseDate, "invalid_agreement_date"},
		{"year zero", "看海", "", &yearZero, "invalid_agreement_date"},
	}
	for _, test := range cases {
		t.Run(test.name, func(t *testing.T) {
			if got := validateAgreementFields(test.title, test.note, test.date); got != test.want {
				t.Fatalf("validation = %q, want %q", got, test.want)
			}
		})
	}
	for _, version := range []int{-1, 0} {
		if validAgreementVersion(version) {
			t.Fatalf("invalid version %d accepted", version)
		}
	}
}

func TestAgreementPagination(t *testing.T) {
	for _, query := range []string{"?completed=1", "?offset=-1", "?limit=0", "?limit=101", "?offset=2147483648", "?limit=oops"} {
		if _, _, _, ok := agreementPagination(httptest.NewRequest(http.MethodGet, "/"+query, nil)); ok {
			t.Fatalf("invalid query %q accepted", query)
		}
	}
	completed, offset, limit, ok := agreementPagination(httptest.NewRequest(http.MethodGet, "/?completed=true&offset=5&limit=2", nil))
	if !ok || !completed || offset != 5 || limit != 2 {
		t.Fatal("valid pagination was not parsed")
	}
	_, offset, limit, ok = agreementPagination(httptest.NewRequest(http.MethodGet, "/", nil))
	if !ok || offset != 0 || limit != 40 {
		t.Fatal("unexpected pagination defaults")
	}
}

func TestAgreementRequestRejectsMalformedJSON(t *testing.T) {
	for _, body := range []string{`{"title":"海"} {}`, `{"title":"海","extra":true}`, `{"completed":"true"}`, `{"version":1.5}`} {
		request := httptest.NewRequest(http.MethodPut, "/", strings.NewReader(body))
		response := httptest.NewRecorder()
		if decodeAgreementRequest(response, request, new(agreementUpdateRequest)) || response.Code != http.StatusBadRequest {
			t.Fatalf("invalid body %s accepted", body)
		}
	}
}

type agreementTestFixture struct {
	t      *testing.T
	db     *pgxpool.Pool
	mux    *http.ServeMux
	users  []uuid.UUID
	tokens []string
	couple uuid.UUID
}

// Set AGREEMENTS_TEST_DATABASE_URL to a disposable PostgreSQL database. Each
// fixture creates and removes its own schema; existing application tables are untouched.
func newAgreementTestFixture(t *testing.T) *agreementTestFixture {
	t.Helper()
	dsn := os.Getenv("AGREEMENTS_TEST_DATABASE_URL")
	if dsn == "" {
		t.Skip("AGREEMENTS_TEST_DATABASE_URL is not set")
	}
	ctx := context.Background()
	admin, err := pgxpool.New(ctx, dsn)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(admin.Close)
	schema := "agreements_test_" + strings.ReplaceAll(uuid.NewString(), "-", "")
	quotedSchema := pgx.Identifier{schema}.Sanitize()
	if _, err := admin.Exec(ctx, "create schema "+quotedSchema); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if _, err := admin.Exec(ctx, "drop schema "+quotedSchema+" cascade"); err != nil {
			t.Errorf("cleanup test schema: %v", err)
		}
	})
	config, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		t.Fatal(err)
	}
	config.ConnConfig.RuntimeParams["search_path"] = schema
	db, err := pgxpool.NewWithConfig(ctx, config)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)
	if err := migrate(ctx, db); err != nil {
		t.Fatal(err)
	}
	s := &server{db: db}
	fixture := &agreementTestFixture{t: t, db: db, mux: http.NewServeMux(), couple: uuid.New()}
	fixture.mux.Handle("GET /api/v1/agreements", s.auth(http.HandlerFunc(s.agreements)))
	fixture.mux.Handle("GET /api/v1/agreements/{id}", s.auth(http.HandlerFunc(s.agreement)))
	fixture.mux.Handle("POST /api/v1/agreements", s.auth(http.HandlerFunc(s.createAgreement)))
	fixture.mux.Handle("PUT /api/v1/agreements/{id}", s.auth(http.HandlerFunc(s.updateAgreement)))
	fixture.mux.Handle("DELETE /api/v1/agreements/{id}", s.auth(http.HandlerFunc(s.deleteAgreement)))
	for i := 0; i < 5; i++ {
		id, token := uuid.New(), fmt.Sprintf("agreement-test-token-%d", i)
		fixture.users, fixture.tokens = append(fixture.users, id), append(fixture.tokens, token)
		if _, err := db.Exec(ctx, `insert into app_users(id,username,display_name,password_hash) values($1,$2,$3,'unused')`, id, fmt.Sprintf("user%d", i), fmt.Sprintf("用户%d", i)); err != nil {
			t.Fatal(err)
		}
		if _, err := db.Exec(ctx, `insert into sessions(id,user_id,token_hash,expires_at) values($1,$2,$3,now()+interval '1 hour')`, uuid.New(), id, tokenHash(token)); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := db.Exec(ctx, `insert into couples(id,member_a,member_b) values($1,$2,$3),($4,$5,$6)`, fixture.couple, fixture.users[0], fixture.users[1], uuid.New(), fixture.users[2], fixture.users[3]); err != nil {
		t.Fatal(err)
	}
	return fixture
}

func (fixture *agreementTestFixture) request(user int, method, path string, body any) *httptest.ResponseRecorder {
	var payload []byte
	if body != nil {
		payload, _ = json.Marshal(body)
	}
	request := httptest.NewRequest(method, path, bytes.NewReader(payload))
	if user >= 0 {
		request.Header.Set("Authorization", "Bearer "+fixture.tokens[user])
	}
	response := httptest.NewRecorder()
	fixture.mux.ServeHTTP(response, request)
	return response
}

func (fixture *agreementTestFixture) item(response *httptest.ResponseRecorder) agreementJSON {
	fixture.t.Helper()
	if response.Code != http.StatusOK {
		fixture.t.Fatalf("status=%d, body=%s", response.Code, response.Body)
	}
	var item agreementJSON
	if err := json.Unmarshal(response.Body.Bytes(), &item); err != nil {
		fixture.t.Fatal(err)
	}
	return item
}

func (fixture *agreementTestFixture) list(user int, query string) agreementsResponse {
	fixture.t.Helper()
	response := fixture.request(user, http.MethodGet, "/api/v1/agreements"+query, nil)
	if response.Code != http.StatusOK {
		fixture.t.Fatalf("status=%d, body=%s", response.Code, response.Body)
	}
	var list agreementsResponse
	if err := json.Unmarshal(response.Body.Bytes(), &list); err != nil {
		fixture.t.Fatal(err)
	}
	return list
}

func assertAgreementError(t *testing.T, response *httptest.ResponseRecorder, status int, code string) {
	t.Helper()
	var body map[string]string
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatal(err)
	}
	if response.Code != status || body["error"] != code {
		t.Fatalf("got %d %s, want %d %s", response.Code, response.Body, status, code)
	}
}

func agreementUpdateBody(item agreementJSON, completed bool) map[string]any {
	return map[string]any{"title": item.Title, "note": item.Note, "due_date": item.DueDate, "completed": completed, "version": item.Version}
}

func TestAgreementIntegrationSharingAndLifecycle(t *testing.T) {
	fixture := newAgreementTestFixture(t)
	assertAgreementError(t, fixture.request(-1, http.MethodGet, "/api/v1/agreements", nil), 401, "unauthorized")
	assertAgreementError(t, fixture.request(4, http.MethodGet, "/api/v1/agreements", nil), 409, "not_matched")
	body := map[string]any{"idempotency_key": "first", "title": " 一起看海 ", "note": "第一行\n第二行", "due_date": "2027-01-01"}
	item := fixture.item(fixture.request(0, http.MethodPost, "/api/v1/agreements", body))
	if item.Title != "一起看海" || item.Note != "第一行\n第二行" || item.CreatorID != fixture.users[0].String() || item.CreatorName != "用户0" || item.Version != 1 {
		t.Fatalf("unexpected created item: %+v", item)
	}
	retry := fixture.item(fixture.request(0, http.MethodPost, "/api/v1/agreements", body))
	if !reflect.DeepEqual(retry, item) {
		t.Fatal("create retry changed the existing agreement")
	}
	list := fixture.list(1, "")
	if len(list.Items) != 1 || list.Items[0].ID != item.ID || list.PendingCount != 1 || list.CompletedCount != 0 || list.Total != 1 {
		t.Fatalf("partner cannot see shared item: %+v", list)
	}
	if got := fixture.list(2, ""); len(got.Items) != 0 || got.Total != 0 {
		t.Fatal("different couple can see agreement")
	}
	path := "/api/v1/agreements/" + item.ID
	assertAgreementError(t, fixture.request(2, http.MethodGet, path, nil), 404, "agreement_not_found")
	assertAgreementError(t, fixture.request(2, http.MethodPut, path, agreementUpdateBody(item, true)), 404, "agreement_not_found")
	assertAgreementError(t, fixture.request(2, http.MethodDelete, path+"?version=1", nil), 404, "agreement_not_found")
	updatedBody := agreementUpdateBody(item, false)
	updatedBody["note"] = "伴侣修改\n仍然保留多行"
	updated := fixture.item(fixture.request(1, http.MethodPut, path, updatedBody))
	if updated.Version != 2 || updated.CreatorID != item.CreatorID || updated.Note != updatedBody["note"] {
		t.Fatal("partner edit did not preserve creator or increment version")
	}
	assertAgreementError(t, fixture.request(0, http.MethodPut, path, agreementUpdateBody(item, true)), 409, "agreement_conflict")
	assertAgreementError(t, fixture.request(0, http.MethodDelete, path+"?version=1", nil), 409, "agreement_conflict")
	completed := fixture.item(fixture.request(0, http.MethodPut, path, agreementUpdateBody(updated, true)))
	if !completed.Completed || completed.CompletedAt == nil || completed.Version != 3 {
		t.Fatal("completion state missing")
	}
	if got := fixture.list(1, "?completed=true"); got.Total != 1 || got.CompletedCount != 1 || got.PendingCount != 0 {
		t.Fatalf("incorrect completion counts: %+v", got)
	}
	editedCompleted := fixture.item(fixture.request(1, http.MethodPut, path, agreementUpdateBody(completed, true)))
	if !editedCompleted.CompletedAt.Equal(*completed.CompletedAt) {
		t.Fatal("editing an already complete agreement changed completion time")
	}
	restored := fixture.item(fixture.request(1, http.MethodPut, path, agreementUpdateBody(editedCompleted, false)))
	if restored.Completed || restored.CompletedAt != nil || restored.Version != 5 {
		t.Fatal("restoration did not clear completion state")
	}
	latest := fixture.item(fixture.request(0, http.MethodGet, path, nil))
	if latest.Version != restored.Version {
		t.Fatal("get-by-id did not return latest version")
	}
	deleted := fixture.request(1, http.MethodDelete, path+"?version=5", nil)
	if deleted.Code != http.StatusNoContent {
		t.Fatalf("delete failed: %d %s", deleted.Code, deleted.Body)
	}
	assertAgreementError(t, fixture.request(0, http.MethodGet, path, nil), 404, "agreement_not_found")
	assertAgreementError(t, fixture.request(0, http.MethodPost, "/api/v1/agreements", body), 404, "agreement_not_found")
	if got := fixture.list(0, ""); got.Total != 0 || got.PendingCount != 0 || got.CompletedCount != 0 || len(got.Items) != 0 {
		t.Fatalf("deleted item still appears: %+v", got)
	}
}

func TestAgreementIntegrationConcurrentRetriesAndEdits(t *testing.T) {
	fixture := newAgreementTestFixture(t)
	var wait sync.WaitGroup
	responses := make([]*httptest.ResponseRecorder, 8)
	for i := range responses {
		wait.Add(1)
		go func(i int) {
			defer wait.Done()
			responses[i] = fixture.request(0, http.MethodPost, "/api/v1/agreements", map[string]any{"idempotency_key": "concurrent", "title": "一起旅行", "note": ""})
		}(i)
	}
	wait.Wait()
	item := fixture.item(responses[0])
	for _, response := range responses[1:] {
		if other := fixture.item(response); other.ID != item.ID || other.Version != 1 {
			t.Fatal("concurrent create produced a duplicate or changed version")
		}
	}
	if fixture.list(0, "").Total != 1 {
		t.Fatal("concurrent retries created more than one record")
	}
	responses = responses[:2]
	for i := range responses {
		wait.Add(1)
		go func(i int) {
			defer wait.Done()
			responses[i] = fixture.request(i, http.MethodPut, "/api/v1/agreements/"+item.ID, agreementUpdateBody(item, true))
		}(i)
	}
	wait.Wait()
	statuses := map[int]int{}
	for _, response := range responses {
		statuses[response.Code]++
	}
	if statuses[http.StatusOK] != 1 || statuses[http.StatusConflict] != 1 {
		t.Fatalf("concurrent stale edits were not rejected: %+v", statuses)
	}
}

func TestAgreementIntegrationRevisionTracksMutations(t *testing.T) {
	fixture := newAgreementTestFixture(t)
	assertRevision := func(want string) {
		t.Helper()
		for _, query := range []string{"", "?completed=true", "?offset=99"} {
			if got := fixture.list(1, query).Revision; got != want {
				t.Fatalf("revision for %q = %q, want %q", query, got, want)
			}
		}
	}
	assertRevision("0")
	body := map[string]any{"idempotency_key": "revision", "title": "一起看海", "note": ""}
	item := fixture.item(fixture.request(0, http.MethodPost, "/api/v1/agreements", body))
	assertRevision("1")
	fixture.item(fixture.request(0, http.MethodPost, "/api/v1/agreements", body))
	assertRevision("1")
	path := "/api/v1/agreements/" + item.ID
	edit := agreementUpdateBody(item, false)
	edit["note"] = "伴侣编辑备注"
	item = fixture.item(fixture.request(1, http.MethodPut, path, edit))
	assertRevision("2")
	item = fixture.item(fixture.request(0, http.MethodPut, path, agreementUpdateBody(item, true)))
	assertRevision("3")
	item = fixture.item(fixture.request(1, http.MethodPut, path, agreementUpdateBody(item, false)))
	assertRevision("4")
	response := fixture.request(0, http.MethodDelete, path+"?version=4", nil)
	if response.Code != http.StatusNoContent {
		t.Fatalf("delete failed: %d %s", response.Code, response.Body)
	}
	assertRevision("5")
	assertAgreementError(t, fixture.request(0, http.MethodPost, "/api/v1/agreements", body), 404, "agreement_not_found")
	assertRevision("5")
	// A second item adds its initial version to the tombstone's version.
	fixture.item(fixture.request(0, http.MethodPost, "/api/v1/agreements", map[string]any{"idempotency_key": "revision-2", "title": "一起旅行", "note": ""}))
	assertRevision("6")
}

func TestAgreementIntegrationSortingPaginationAndSnapshot(t *testing.T) {
	fixture := newAgreementTestFixture(t)
	items := make([]agreementJSON, 6)
	for i := range items {
		items[i] = fixture.item(fixture.request(0, http.MethodPost, "/api/v1/agreements", map[string]any{"idempotency_key": fmt.Sprintf("sort-%d", i), "title": fmt.Sprintf("约定%d", i), "note": ""}))
	}
	// Pending order: a near date, equal dates ordered by newest creation, then
	// undated records by newest creation. Two completed records sort by finish time.
	for i, due := range []any{nil, "2027-02-01", "2027-01-01", "2027-02-01"} {
		created := time.Date(2026, 1, 1+i, 0, 0, 0, 0, time.UTC)
		if _, err := fixture.db.Exec(context.Background(), `update agreements set due_date=$2::date,created_at=$3 where id=$1`, items[i].ID, due, created); err != nil {
			t.Fatal(err)
		}
	}
	for i := 4; i < 6; i++ {
		if _, err := fixture.db.Exec(context.Background(), `update agreements set completed=true,completed_at=$2 where id=$1`, items[i].ID, time.Date(2026, 1, i, 0, 0, 0, 0, time.UTC)); err != nil {
			t.Fatal(err)
		}
	}
	pending := fixture.list(1, "?limit=2&offset=0")
	if len(pending.Items) != 2 || pending.Items[0].ID != items[2].ID || pending.Items[1].ID != items[3].ID || pending.Total != 4 || pending.CompletedCount != 2 {
		t.Fatalf("incorrect first pending page: %+v", pending)
	}
	pending = fixture.list(1, "?limit=2&offset=2")
	if len(pending.Items) != 2 || pending.Items[0].ID != items[1].ID || pending.Items[1].ID != items[0].ID {
		t.Fatalf("incorrect next pending page: %+v", pending)
	}
	if empty := fixture.list(0, "?offset=99"); len(empty.Items) != 0 || empty.Total != 4 || empty.PendingCount != 4 || empty.CompletedCount != 2 {
		t.Fatalf("empty page lost totals: %+v", empty)
	}
	completed := fixture.list(0, "?completed=true")
	if len(completed.Items) != 2 || completed.Items[0].ID != items[5].ID || completed.Items[1].ID != items[4].ID {
		t.Fatalf("incorrect completed order: %+v", completed)
	}
	// Toggle rows while listing. Every complete page must still match its own
	// counts, proving the two statements observe the same snapshot.
	updatesDone := make(chan error, 1)
	go func() {
		for i := 0; i < 30; i++ {
			completed := i%2 == 0
			if _, err := fixture.db.Exec(context.Background(), `update agreements set completed=$2,completed_at=case when $2 then now() else null end where couple_id=$1 and deleted_at is null`, fixture.couple, completed); err != nil {
				updatesDone <- err
				return
			}
		}
		updatesDone <- nil
	}()
	for i := 0; i < 30; i++ {
		list := fixture.list(0, "?limit=100")
		if list.Total != len(list.Items) || list.PendingCount+list.CompletedCount != 6 {
			t.Fatalf("list and counts observed different snapshots: %+v", list)
		}
	}
	if err := <-updatesDone; err != nil {
		t.Fatal(err)
	}
}
