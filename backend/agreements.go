package main

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
)

type agreementJSON struct {
	ID          string     `json:"id"`
	CreatorID   string     `json:"creator_id"`
	CreatorName string     `json:"creator_name"`
	Title       string     `json:"title"`
	Note        string     `json:"note"`
	DueDate     *string    `json:"due_date"`
	Completed   bool       `json:"completed"`
	CompletedAt *time.Time `json:"completed_at"`
	CreatedAt   time.Time  `json:"created_at"`
	UpdatedAt   time.Time  `json:"updated_at"`
	Version     int        `json:"version"`
}

type agreementsResponse struct {
	Items          []agreementJSON `json:"items"`
	PendingCount   int             `json:"pending_count"`
	CompletedCount int             `json:"completed_count"`
	Total          int             `json:"total"`
	Revision       string          `json:"revision"`
}

type agreementCreateRequest struct {
	IdempotencyKey string  `json:"idempotency_key"`
	Title          string  `json:"title"`
	Note           string  `json:"note"`
	DueDate        *string `json:"due_date"`
}

type agreementUpdateRequest struct {
	Title     string  `json:"title"`
	Note      string  `json:"note"`
	DueDate   *string `json:"due_date"`
	Completed *bool   `json:"completed"`
	Version   int     `json:"version"`
}

const agreementColumns = `a.id, a.creator_id, coalesce(nullif(u.display_name, ''), u.username),
 a.title, a.note, to_char(a.due_date, 'YYYY-MM-DD'), a.completed, a.completed_at,
 a.created_at, a.updated_at, a.version`

type agreementQuerier interface {
	QueryRow(context.Context, string, ...any) pgx.Row
}

func scanAgreement(row pgx.Row) (agreementJSON, error) {
	var item agreementJSON
	err := row.Scan(&item.ID, &item.CreatorID, &item.CreatorName, &item.Title, &item.Note,
		&item.DueDate, &item.Completed, &item.CompletedAt, &item.CreatedAt, &item.UpdatedAt, &item.Version)
	return item, err
}

func validateAgreementFields(title, note string, dueDate *string) string {
	if title == "" || !utf8.ValidString(title) || utf8.RuneCountInString(title) > 40 {
		return "invalid_agreement_title"
	}
	if !utf8.ValidString(note) || utf8.RuneCountInString(note) > 2000 {
		return "agreement_note_too_long"
	}
	if dueDate != nil {
		date, err := time.Parse("2006-01-02", *dueDate)
		if err != nil || date.Format("2006-01-02") != *dueDate || date.Year() < 1 {
			return "invalid_agreement_date"
		}
	}
	return ""
}

func validAgreementVersion(version int) bool { return version >= 1 && version <= 2147483647 }

func agreementPagination(r *http.Request) (completed bool, offset, limit int, valid bool) {
	limit = 40
	query := r.URL.Query()
	if value := query.Get("completed"); value != "" {
		if value != "true" && value != "false" {
			return false, 0, 0, false
		}
		completed = value == "true"
	}
	var err error
	if value := query.Get("offset"); value != "" {
		offset, err = strconv.Atoi(value)
		if err != nil || offset < 0 || offset > 2147483647 {
			return false, 0, 0, false
		}
	}
	if value := query.Get("limit"); value != "" {
		limit, err = strconv.Atoi(value)
		if err != nil || limit < 1 || limit > 100 {
			return false, 0, 0, false
		}
	}
	return completed, offset, limit, true
}

func decodeAgreementRequest(w http.ResponseWriter, r *http.Request, value any) bool {
	defer r.Body.Close()
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(value); err != nil {
		errorJSON(w, http.StatusBadRequest, "invalid_agreement_request")
		return false
	}
	if err := decoder.Decode(new(any)); !errors.Is(err, io.EOF) {
		errorJSON(w, http.StatusBadRequest, "invalid_agreement_request")
		return false
	}
	return true
}

func agreementDatabaseError(w http.ResponseWriter, operation string, err error) {
	log.Printf("agreements %s: %v", operation, err)
	errorJSON(w, http.StatusInternalServerError, "database_error")
}

func agreementCoupleID(w http.ResponseWriter, r *http.Request, db agreementQuerier) (uuid.UUID, bool) {
	var id uuid.UUID
	err := db.QueryRow(r.Context(), `select id from couples where status='active' and (member_a=$1 or member_b=$1)`, userID(r)).Scan(&id)
	if errors.Is(err, pgx.ErrNoRows) {
		errorJSON(w, http.StatusConflict, "not_matched")
		return uuid.Nil, false
	}
	if err != nil {
		agreementDatabaseError(w, "find couple", err)
		return uuid.Nil, false
	}
	return id, true
}

func rollbackAgreementTx(ctx context.Context, tx pgx.Tx) {
	if err := tx.Rollback(ctx); err != nil && !errors.Is(err, pgx.ErrTxClosed) {
		log.Printf("agreements rollback: %v", err)
	}
}

func (s *server) agreements(w http.ResponseWriter, r *http.Request) {
	completed, offset, limit, valid := agreementPagination(r)
	if !valid {
		errorJSON(w, http.StatusBadRequest, "invalid_agreement_request")
		return
	}
	// Both counts and the page use the same database snapshot, including empty pages.
	tx, err := s.db.BeginTx(r.Context(), pgx.TxOptions{IsoLevel: pgx.RepeatableRead, AccessMode: pgx.ReadOnly})
	if err != nil {
		agreementDatabaseError(w, "begin list", err)
		return
	}
	defer rollbackAgreementTx(r.Context(), tx)
	coupleID, ok := agreementCoupleID(w, r, tx)
	if !ok {
		return
	}
	response := agreementsResponse{Items: []agreementJSON{}}
	// Include tombstones in the revision: deletes must invalidate pages too. Each
	// mutation increments version; a repeated idempotent create leaves it unchanged.
	err = tx.QueryRow(r.Context(), `select count(*) filter (where not completed and deleted_at is null),
 count(*) filter (where completed and deleted_at is null), coalesce(sum(version),0)::text
 from agreements where couple_id=$1`, coupleID).Scan(&response.PendingCount, &response.CompletedCount, &response.Revision)
	if err != nil {
		agreementDatabaseError(w, "count", err)
		return
	}
	order, predicate := `a.due_date asc nulls last, a.created_at desc, a.id`, `not a.completed`
	response.Total = response.PendingCount
	if completed {
		order, predicate = `a.completed_at desc, a.id`, `a.completed`
		response.Total = response.CompletedCount
	}
	rows, err := tx.Query(r.Context(), `select `+agreementColumns+` from agreements a join app_users u on u.id=a.creator_id
 where a.couple_id=$1 and a.deleted_at is null and `+predicate+` order by `+order+` offset $2 limit $3`, coupleID, offset, limit)
	if err != nil {
		agreementDatabaseError(w, "list", err)
		return
	}
	for rows.Next() {
		item, scanErr := scanAgreement(rows)
		if scanErr != nil {
			rows.Close()
			agreementDatabaseError(w, "scan list", scanErr)
			return
		}
		response.Items = append(response.Items, item)
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		agreementDatabaseError(w, "read list", err)
		return
	}
	if err = tx.Commit(r.Context()); err != nil {
		agreementDatabaseError(w, "commit list", err)
		return
	}
	writeJSON(w, http.StatusOK, response)
}

func (s *server) createAgreement(w http.ResponseWriter, r *http.Request) {
	var request agreementCreateRequest
	if !decodeAgreementRequest(w, r, &request) {
		return
	}
	request.Title = strings.TrimSpace(request.Title)
	request.IdempotencyKey = strings.TrimSpace(request.IdempotencyKey)
	if code := validateAgreementFields(request.Title, request.Note, request.DueDate); code != "" {
		errorJSON(w, http.StatusBadRequest, code)
		return
	}
	if request.IdempotencyKey == "" || utf8.RuneCountInString(request.IdempotencyKey) > 128 {
		errorJSON(w, http.StatusBadRequest, "invalid_agreement_request")
		return
	}
	coupleID, ok := agreementCoupleID(w, r, s.db)
	if !ok {
		return
	}
	// The unique constraint serializes simultaneous retries; the no-op update returns
	// the existing record without changing its contents, version or timestamps.
	item, err := scanAgreement(s.db.QueryRow(r.Context(), `with saved as (
 insert into agreements(id,couple_id,creator_id,idempotency_key,title,note,due_date)
 values($1,$2,$3,$4,$5,$6,$7::date)
 on conflict (couple_id,creator_id,idempotency_key) do update set idempotency_key=excluded.idempotency_key
 returning *) select `+agreementColumns+` from saved a join app_users u on u.id=a.creator_id where a.deleted_at is null`,
		uuid.New(), coupleID, userID(r), request.IdempotencyKey, request.Title, request.Note, request.DueDate))
	if errors.Is(err, pgx.ErrNoRows) {
		errorJSON(w, http.StatusNotFound, "agreement_not_found")
		return
	}
	if err != nil {
		agreementDatabaseError(w, "create", err)
		return
	}
	writeJSON(w, http.StatusOK, item)
}

func (s *server) agreement(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(r.PathValue("id"))
	if err != nil {
		errorJSON(w, http.StatusNotFound, "agreement_not_found")
		return
	}
	coupleID, ok := agreementCoupleID(w, r, s.db)
	if !ok {
		return
	}
	item, err := scanAgreement(s.db.QueryRow(r.Context(), `select `+agreementColumns+` from agreements a
 join app_users u on u.id=a.creator_id where a.id=$1 and a.couple_id=$2 and a.deleted_at is null`, id, coupleID))
	if errors.Is(err, pgx.ErrNoRows) {
		errorJSON(w, http.StatusNotFound, "agreement_not_found")
		return
	}
	if err != nil {
		agreementDatabaseError(w, "get", err)
		return
	}
	writeJSON(w, http.StatusOK, item)
}

func (s *server) updateAgreement(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(r.PathValue("id"))
	if err != nil {
		errorJSON(w, http.StatusNotFound, "agreement_not_found")
		return
	}
	var request agreementUpdateRequest
	if !decodeAgreementRequest(w, r, &request) {
		return
	}
	request.Title = strings.TrimSpace(request.Title)
	if code := validateAgreementFields(request.Title, request.Note, request.DueDate); code != "" {
		errorJSON(w, http.StatusBadRequest, code)
		return
	}
	if !validAgreementVersion(request.Version) || request.Completed == nil {
		errorJSON(w, http.StatusBadRequest, "invalid_agreement_request")
		return
	}
	coupleID, ok := agreementCoupleID(w, r, s.db)
	if !ok {
		return
	}
	item, err := scanAgreement(s.db.QueryRow(r.Context(), `with saved as (
 update agreements set title=$3,note=$4,due_date=$5::date,completed=$6,
 completed_at=case when $6 and not completed then now() when $6 then completed_at else null end,
 updated_at=now(),version=version+1 where id=$1 and couple_id=$2 and version=$7 and deleted_at is null
 returning *) select `+agreementColumns+` from saved a join app_users u on u.id=a.creator_id`,
		id, coupleID, request.Title, request.Note, request.DueDate, *request.Completed, request.Version))
	if errors.Is(err, pgx.ErrNoRows) {
		s.agreementMissingOrConflict(w, r, coupleID, id)
		return
	}
	if err != nil {
		agreementDatabaseError(w, "update", err)
		return
	}
	writeJSON(w, http.StatusOK, item)
}

func (s *server) deleteAgreement(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(r.PathValue("id"))
	if err != nil {
		errorJSON(w, http.StatusNotFound, "agreement_not_found")
		return
	}
	version, err := strconv.Atoi(r.URL.Query().Get("version"))
	if err != nil || !validAgreementVersion(version) {
		errorJSON(w, http.StatusBadRequest, "invalid_agreement_request")
		return
	}
	coupleID, ok := agreementCoupleID(w, r, s.db)
	if !ok {
		return
	}
	result, err := s.db.Exec(r.Context(), `update agreements set deleted_at=now(),updated_at=now(),version=version+1
 where id=$1 and couple_id=$2 and version=$3 and deleted_at is null`, id, coupleID, version)
	if err != nil {
		agreementDatabaseError(w, "delete", err)
		return
	}
	if result.RowsAffected() == 0 {
		s.agreementMissingOrConflict(w, r, coupleID, id)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *server) agreementMissingOrConflict(w http.ResponseWriter, r *http.Request, coupleID, id uuid.UUID) {
	var exists bool
	err := s.db.QueryRow(r.Context(), `select exists(select 1 from agreements where id=$1 and couple_id=$2 and deleted_at is null)`, id, coupleID).Scan(&exists)
	if err != nil {
		agreementDatabaseError(w, "check version", err)
		return
	}
	if !exists {
		errorJSON(w, http.StatusNotFound, "agreement_not_found")
		return
	}
	errorJSON(w, http.StatusConflict, "agreement_conflict")
}
