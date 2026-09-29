package main

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"embed"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
	"net/url"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"golang.org/x/crypto/argon2"
)

//go:embed migrations/*.sql
var migrationFiles embed.FS

var usernamePattern = regexp.MustCompile(`^[a-z][a-z0-9_]{2,19}$`)
var passwordUpper = regexp.MustCompile(`[A-Z]`)
var passwordLower = regexp.MustCompile(`[a-z]`)
var passwordDigit = regexp.MustCompile(`[0-9]`)
var passwordSpecial = regexp.MustCompile(`[^A-Za-z0-9]`)

type server struct{ db *pgxpool.Pool }
type contextKey string
const userKey contextKey = "user-id"

type registerRequest struct { Username string `json:"username"`; Password string `json:"password"`; DisplayName string `json:"display_name"` }
type loginRequest struct { Username, Password string }
type inviteRequest struct { Code string `json:"code"` }
type inviteCreateRequest struct { Initial int `json:"initial_score"`; Min *int `json:"min_score"`; Max *int `json:"max_score"`; AddMin int `json:"add_min"`; AddMax int `json:"add_max"`; SubtractMin int `json:"subtract_min"`; SubtractMax int `json:"subtract_max"` }
type scoreRequest struct { Delta int `json:"delta"`; Note string `json:"note"`; IdempotencyKey string `json:"idempotency_key"` }
type nicknameRequest struct { Nickname string `json:"nickname"` }

type userJSON struct { ID string `json:"id"`; Username string `json:"username"`; DisplayName string `json:"display_name"` }
type authResponse struct { Token string `json:"token"`; User userJSON `json:"user"` }

func main() {
	ctx := context.Background()
	dsn := os.Getenv("DATABASE_URL")
	if dsn == "" {
		if os.Getenv("DATABASE_HOST") == "" || os.Getenv("DATABASE_PORT") == "" || os.Getenv("DATABASE_NAME") == "" || os.Getenv("DATABASE_USER") == "" || os.Getenv("DATABASE_PASSWORD") == "" {
			log.Fatal("DATABASE_URL or complete DATABASE_* settings are required")
		}
		dsn = fmt.Sprintf("postgres://%s:%s@%s:%s/%s?sslmode=disable",
		url.QueryEscape(os.Getenv("DATABASE_USER")), url.QueryEscape(os.Getenv("DATABASE_PASSWORD")),
		os.Getenv("DATABASE_HOST"), os.Getenv("DATABASE_PORT"), os.Getenv("DATABASE_NAME"))
	}
	db, err := pgxpool.New(ctx, dsn)
	if err != nil { log.Fatal(err) }
	defer db.Close()
	if err := db.Ping(ctx); err != nil { log.Fatal(err) }
	if err := migrate(ctx, db); err != nil { log.Fatal(err) }

	s := &server{db: db}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/v1/health", s.health)
	mux.HandleFunc("POST /api/v1/auth/register", s.register)
	mux.HandleFunc("POST /api/v1/auth/login", s.login)
	mux.Handle("POST /api/v1/auth/logout", s.auth(http.HandlerFunc(s.logout)))
	mux.Handle("GET /api/v1/me", s.auth(http.HandlerFunc(s.me)))
	mux.Handle("POST /api/v1/invites", s.auth(http.HandlerFunc(s.createInvite)))
	mux.Handle("POST /api/v1/invites/accept", s.auth(http.HandlerFunc(s.acceptInvite)))
	mux.Handle("GET /api/v1/couple", s.auth(http.HandlerFunc(s.couple)))
	mux.Handle("POST /api/v1/scores/events", s.auth(http.HandlerFunc(s.addScore)))
	mux.Handle("POST /api/v1/score-settings/requests", s.auth(http.HandlerFunc(s.createRulesRequest)))
	mux.Handle("POST /api/v1/score-settings/requests/{id}/accept", s.auth(http.HandlerFunc(s.acceptRulesRequest)))
	mux.Handle("POST /api/v1/score-settings/requests/{id}/reject", s.auth(http.HandlerFunc(s.rejectRulesRequest)))
	mux.Handle("POST /api/v1/score-settings/requests/{id}/cancel", s.auth(http.HandlerFunc(s.cancelRulesRequest)))
	mux.Handle("GET /api/v1/score-events", s.auth(http.HandlerFunc(s.events)))
	mux.Handle("PUT /api/v1/couple/nickname", s.auth(http.HandlerFunc(s.updateNickname)))

	addr := os.Getenv("LISTEN_ADDR"); if addr == "" { addr = ":8080" }
	log.Printf("FavorApp API listening on %s", addr)
	server := &http.Server{
		Addr:              addr,
		Handler:           logging(mux),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	log.Fatal(server.ListenAndServe())
}

func migrate(ctx context.Context, db *pgxpool.Pool) error {
	if _, err := db.Exec(ctx, `create table if not exists schema_migrations (name text primary key, applied_at timestamptz not null default now())`); err != nil { return err }
	entries, err := fs.ReadDir(migrationFiles, "migrations"); if err != nil { return err }
	var names []string; for _, e := range entries { if !e.IsDir() { names = append(names, e.Name()) } }; sort.Strings(names)
	for _, name := range names {
		var applied bool; if err := db.QueryRow(ctx, `select exists(select 1 from schema_migrations where name=$1)`, name).Scan(&applied); err != nil { return err }; if applied { continue }
		data, err := migrationFiles.ReadFile(filepath.Join("migrations", name)); if err != nil { return err }
		tx, err := db.Begin(ctx); if err != nil { return err }
		if _, err = tx.Exec(ctx, string(data)); err == nil { _, err = tx.Exec(ctx, `insert into schema_migrations(name) values($1)`, name) }
		if err != nil { _ = tx.Rollback(ctx); return fmt.Errorf("migration %s: %w", name, err) }; if err = tx.Commit(ctx); err != nil { return err }
	}
	return nil
}

func (s *server) health(w http.ResponseWriter, r *http.Request) { writeJSON(w, http.StatusOK, map[string]string{"status":"ok"}) }
func (s *server) auth(next http.Handler) http.Handler { return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { raw := strings.TrimSpace(strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")); if raw == "" { errorJSON(w, 401, "unauthorized"); return }; hash := tokenHash(raw); var id uuid.UUID; err := s.db.QueryRow(r.Context(), `select user_id from sessions where token_hash=$1 and expires_at > now()`, hash).Scan(&id); if err != nil { errorJSON(w, 401, "unauthorized"); return }; next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), userKey, id))) }) }
func userID(r *http.Request) uuid.UUID { return r.Context().Value(userKey).(uuid.UUID) }

func (s *server) register(w http.ResponseWriter, r *http.Request) {
	var req registerRequest; if !decodeJSON(w, r, &req) { return }; username := strings.ToLower(strings.TrimSpace(req.Username)); if !usernamePattern.MatchString(username) { errorJSON(w, 400, "username_format"); return }; if !validPassword(req.Password) { errorJSON(w, 400, "password_format"); return }; display := strings.TrimSpace(req.DisplayName); if display == "" { display = username }; if len([]rune(display)) > 40 { errorJSON(w, 400, "display_name_too_long"); return }
	hash, err := hashPassword(req.Password); if err != nil { errorJSON(w, 500, "password_hash_failed"); return }; id := uuid.New(); _, err = s.db.Exec(r.Context(), `insert into app_users(id,username,display_name,password_hash) values($1,$2,$3,$4)`, id, username, display, hash); if err != nil { if strings.Contains(err.Error(), "duplicate") { errorJSON(w, 409, "username_taken") } else { errorJSON(w, 500, "database_error") }; return }; s.issueSession(w, r, id, userJSON{ID:id.String(),Username:username,DisplayName:display})
}
func (s *server) login(w http.ResponseWriter, r *http.Request) { var req loginRequest; if !decodeJSON(w,r,&req) { return }; var id uuid.UUID; var display, stored string; err := s.db.QueryRow(r.Context(), `select id,display_name,password_hash from app_users where username=$1`, strings.ToLower(strings.TrimSpace(req.Username))).Scan(&id,&display,&stored); if err != nil || !verifyPassword(req.Password,stored) { errorJSON(w,401,"invalid_credentials"); return }; s.issueSession(w,r,id,userJSON{ID:id.String(),Username:strings.ToLower(strings.TrimSpace(req.Username)),DisplayName:display}) }
func (s *server) issueSession(w http.ResponseWriter, r *http.Request, id uuid.UUID, user userJSON) { token,hash,err:=newSessionToken(); if err!=nil { errorJSON(w,500,"session_failed"); return }
	// Best-effort cleanup so expired sessions and invites do not accumulate; must not block login.
	_,_ = s.db.Exec(r.Context(), `delete from sessions where expires_at < now()`)
	_,_ = s.db.Exec(r.Context(), `delete from invites where expires_at < now()`)
	_,_ = s.db.Exec(r.Context(), `delete from rule_change_requests where status <> 'pending' and responded_at < now() - interval '30 days'`)
	_,err=s.db.Exec(r.Context(),`insert into sessions(id,user_id,token_hash,expires_at) values($1,$2,$3,$4)`,uuid.New(),id,hash,time.Now().Add(30*24*time.Hour)); if err!=nil { errorJSON(w,500,"session_failed"); return }; writeJSON(w,200,authResponse{Token:token,User:user}) }
func (s *server) logout(w http.ResponseWriter, r *http.Request) { raw:=strings.TrimSpace(strings.TrimPrefix(r.Header.Get("Authorization"),"Bearer ")); h:=tokenHash(raw); _,_ = s.db.Exec(r.Context(),`delete from sessions where token_hash=$1`,h); w.WriteHeader(http.StatusNoContent) }
func (s *server) me(w http.ResponseWriter, r *http.Request) { var u userJSON; err:=s.db.QueryRow(r.Context(),`select id,username,display_name from app_users where id=$1`,userID(r)).Scan(&u.ID,&u.Username,&u.DisplayName); if err!=nil { errorJSON(w,404,"user_not_found"); return }; writeJSON(w,200,u) }

func (s *server) createInvite(w http.ResponseWriter, r *http.Request) {
	var req inviteCreateRequest
	if !decodeJSON(w, r, &req) { return }
	addMin, addMax, subtractMin, subtractMax := req.AddMin, req.AddMax, req.SubtractMin, req.SubtractMax
	if addMin == 0 { addMin = 1 }; if addMax == 0 { addMax = 5 }; if subtractMin == 0 { subtractMin = 1 }; if subtractMax == 0 { subtractMax = 5 }
	if code := validateScoreRules(req.Initial, req.Min, req.Max, addMin, addMax, subtractMin, subtractMax); code != "" { errorJSON(w, 400, code); return }
	uid := userID(r); var exists bool
	_ = s.db.QueryRow(r.Context(), `select exists(select 1 from couples where status='active' and (member_a=$1 or member_b=$1))`, uid).Scan(&exists)
	if exists { errorJSON(w, 409, "already_matched"); return }
	raw := make([]byte, 5); if _, err := rand.Read(raw); err != nil { errorJSON(w, 500, "invite_failed"); return }
	code := strings.ToUpper(hex.EncodeToString(raw)); h := sha256.Sum256([]byte(code))
	_, err := s.db.Exec(r.Context(), `insert into invites(id,inviter_id,code_hash,expires_at,initial_score,min_score,max_score,add_min,add_max,subtract_min,subtract_max) values($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`, uuid.New(), uid, h[:], time.Now().Add(24*time.Hour), req.Initial, req.Min, req.Max, addMin, addMax, subtractMin, subtractMax)
	if err != nil { errorJSON(w, 500, "invite_failed"); return }; writeJSON(w, 200, map[string]string{"code": code})
}

func (s *server) acceptInvite(w http.ResponseWriter, r *http.Request) {
	var req inviteRequest; if !decodeJSON(w, r, &req) { return }; code := strings.ToUpper(strings.TrimSpace(req.Code)); if code == "" { errorJSON(w, 400, "invalid_code"); return }
	tx, err := s.db.Begin(r.Context()); if err != nil { errorJSON(w, 500, "database_error"); return }; defer tx.Rollback(r.Context()); uid := userID(r)
	var inviter, inviteID uuid.UUID; var initial int; var min, max *int; var addMin, addMax, subtractMin, subtractMax int
	err = tx.QueryRow(r.Context(), `select id,inviter_id,initial_score,min_score,max_score,add_min,add_max,subtract_min,subtract_max from invites where code_hash=$1 and used_at is null and expires_at>now() for update`, sha256Bytes(code)).Scan(&inviteID, &inviter, &initial, &min, &max, &addMin, &addMax, &subtractMin, &subtractMax)
	if err != nil { errorJSON(w, 400, "invalid_or_expired_code"); return }; if inviter == uid { errorJSON(w, 400, "cannot_match_self"); return }
	var matched bool; _ = tx.QueryRow(r.Context(), `select exists(select 1 from couples where status='active' and (member_a=$1 or member_b=$1 or member_a=$2 or member_b=$2))`, uid, inviter).Scan(&matched); if matched { errorJSON(w, 409, "already_matched"); return }
	cid := uuid.New(); if _, err = tx.Exec(r.Context(), `insert into couples(id,member_a,member_b) values($1,$2,$3)`, cid, inviter, uid); err != nil { errorJSON(w, 409, "already_matched"); return }
	if _, err = tx.Exec(r.Context(), `insert into score_settings(couple_id,initial_score,min_score,max_score,add_min,add_max,subtract_min,subtract_max) values($1,$2,$3,$4,$5,$6,$7,$8)`, cid, initial, min, max, addMin, addMax, subtractMin, subtractMax); err != nil { errorJSON(w, 500, "database_error"); return }
	if _, err = tx.Exec(r.Context(), `insert into couple_scores(couple_id,target_user_id,current_score) values($1,$2,$3),($1,$4,$3)`, cid, inviter, initial, uid); err != nil { errorJSON(w, 500, "database_error"); return }
	if _, err = tx.Exec(r.Context(), `update invites set used_at=now(),used_by=$1 where id=$2`, uid, inviteID); err != nil { errorJSON(w, 500, "database_error"); return }; if err = tx.Commit(r.Context()); err != nil { errorJSON(w, 500, "database_error"); return }
	writeJSON(w, 200, map[string]string{"couple_id": cid.String()})
}
func (s *server) couple(w http.ResponseWriter, r *http.Request) {
	uid := userID(r); var cid, a, b uuid.UUID; var nicknameA, nicknameB string
	err := s.db.QueryRow(r.Context(), `select id,member_a,member_b,member_a_nickname,member_b_nickname from couples where status='active' and (member_a=$1 or member_b=$1)`, uid).Scan(&cid, &a, &b, &nicknameA, &nicknameB)
	if errors.Is(err, pgx.ErrNoRows) { writeJSON(w, 200, nil); return }; if err != nil { errorJSON(w, 500, "database_error"); return }
	var settings struct { Initial int `json:"initial_score"`; Min *int `json:"min_score"`; Max *int `json:"max_score"`; AddMin int `json:"add_min"`; AddMax int `json:"add_max"`; SubtractMin int `json:"subtract_min"`; SubtractMax int `json:"subtract_max"` }
	err = s.db.QueryRow(r.Context(), `select initial_score,min_score,max_score,add_min,add_max,subtract_min,subtract_max from score_settings where couple_id=$1`, cid).Scan(&settings.Initial, &settings.Min, &settings.Max, &settings.AddMin, &settings.AddMax, &settings.SubtractMin, &settings.SubtractMax)
	if err != nil { errorJSON(w, 500, "database_error"); return }
	rows, err := s.db.Query(r.Context(), `select cs.target_user_id,u.username,u.display_name,cs.current_score from couple_scores cs join app_users u on u.id=cs.target_user_id where cs.couple_id=$1`, cid); if err != nil { errorJSON(w, 500, "database_error"); return }; defer rows.Close()
	type card struct { UserID string `json:"user_id"`; Name string `json:"name"`; Score int `json:"score"` }; cards := []card{}
	nickname := nicknameA; if uid == b { nickname = nicknameB }
	currentName, partnerName := "", ""; partner := other(uid, a, b)
	for rows.Next() { var id uuid.UUID; var username, name string; var score int; if err = rows.Scan(&id, &username, &name, &score); err != nil { errorJSON(w, 500, "database_error"); return }; if name == "" { name = username }; if id == uid { currentName = name } else if id == partner { partnerName = name }; cards = append(cards, card{id.String(), name, score}) }
	events := s.loadEvents(r.Context(), cid, r.URL.Query().Get("from"), r.URL.Query().Get("to"), r.URL.Query().Get("keyword"))
	var pending *pendingRulesJSON
	var pendingRow pendingRulesJSON
	if e := s.db.QueryRow(r.Context(), `select id,requester_id,initial_score,min_score,max_score,add_min,add_max,subtract_min,subtract_max,created_at from rule_change_requests where couple_id=$1 and status='pending' order by created_at desc limit 1`, cid).Scan(&pendingRow.ID, &pendingRow.RequesterID, &pendingRow.Initial, &pendingRow.Min, &pendingRow.Max, &pendingRow.AddMin, &pendingRow.AddMax, &pendingRow.SubtractMin, &pendingRow.SubtractMax, &pendingRow.CreatedAt); e == nil { pending = &pendingRow }
	var decision *ruleDecisionJSON
	var decisionRow ruleDecisionJSON
	if e := s.db.QueryRow(r.Context(), `select requester_id,status,responded_at from rule_change_requests where couple_id=$1 and status in ('accepted','rejected') and responded_at is not null order by responded_at desc limit 1`, cid).Scan(&decisionRow.RequesterID, &decisionRow.Status, &decisionRow.RespondedAt); e == nil { decision = &decisionRow }
	writeJSON(w, 200, map[string]any{"couple_id": cid.String(), "current_user_id": uid.String(), "current_user_name": currentName, "partner_name": partnerName, "partner_nickname": nickname, "cards": cards, "events": events, "settings": settings, "pending_rules": pending, "latest_rule_decision": decision})
}
type eventJSON struct{ID string `json:"id"`;ActorID string `json:"actor_id"`;ActorName string `json:"actor_name"`;TargetName string `json:"target_name"`;Delta int `json:"delta"`;ScoreAfter int `json:"score_after"`;Note *string `json:"note"`;CreatedAt time.Time `json:"created_at"`}
func (s *server) loadEvents(ctx context.Context,cid uuid.UUID, from, to, keyword string)[]eventJSON{from = strings.TrimSpace(from); to = strings.TrimSpace(to); keyword = strings.TrimSpace(keyword); if len([]rune(keyword)) > 80 { keyword = string([]rune(keyword)[:80]) }; args := []any{cid}; where := `e.couple_id=$1`; if from != "" { args = append(args, from); where += fmt.Sprintf(" and e.created_at >= ($%d::date::timestamp at time zone 'Asia/Shanghai')", len(args)) }; if to != "" { args = append(args, to); where += fmt.Sprintf(" and e.created_at < (($%d::date + interval '1 day')::timestamp at time zone 'Asia/Shanghai')", len(args)) }; if keyword != "" { args = append(args, "%"+keyword+"%"); where += fmt.Sprintf(" and e.note ilike $%d", len(args)) }; query := `select e.id,e.actor_id,au.display_name,tu.display_name,e.delta,e.score_after,e.note,e.created_at from score_events e join app_users au on au.id=e.actor_id join app_users tu on tu.id=e.target_user_id where `+where+` order by e.created_at desc limit 200`; rows,err:=s.db.Query(ctx,query,args...);if err!=nil{return []eventJSON{}};defer rows.Close();out:=[]eventJSON{};for rows.Next(){var id,actor uuid.UUID;var a,t string;var d,sa int;var note *string;var at time.Time;if rows.Scan(&id,&actor,&a,&t,&d,&sa,&note,&at)==nil{out=append(out,eventJSON{id.String(),actor.String(),a,t,d,sa,note,at})}};return out}
func (s *server) events(w http.ResponseWriter,r *http.Request){var cid uuid.UUID;err:=s.db.QueryRow(r.Context(),`select id from couples where status='active' and (member_a=$1 or member_b=$1)`,userID(r)).Scan(&cid);if err!=nil{writeJSON(w,200,[]eventJSON{});return};writeJSON(w,200,s.loadEvents(r.Context(),cid,r.URL.Query().Get("from"),r.URL.Query().Get("to"),r.URL.Query().Get("keyword")))}
func (s *server) updateNickname(w http.ResponseWriter, r *http.Request) { var req nicknameRequest; if !decodeJSON(w,r,&req) { return }; nickname := strings.TrimSpace(req.Nickname); if len([]rune(nickname)) > 8 { errorJSON(w,400,"nickname_too_long"); return }; var cid,a,b uuid.UUID; if err:=s.db.QueryRow(r.Context(),`select id,member_a,member_b from couples where status='active' and (member_a=$1 or member_b=$1)`,userID(r)).Scan(&cid,&a,&b); err!=nil { errorJSON(w,409,"not_matched"); return }; column := "member_a_nickname"; if userID(r)==b { column="member_b_nickname" }; if _,err:=s.db.Exec(r.Context(),`update couples set `+column+`=$1 where id=$2`,nickname,cid);err!=nil{errorJSON(w,500,"database_error");return};writeJSON(w,200,map[string]string{"partner_nickname":nickname}) }
func (s *server) addScore(w http.ResponseWriter, r *http.Request) {
	var req scoreRequest; if !decodeJSON(w, r, &req) { return }; if req.Delta == 0 || len(req.IdempotencyKey) < 8 || len(req.IdempotencyKey) > 80 { errorJSON(w, 400, "invalid_score_request"); return }; if len([]rune(req.Note)) > 200 { errorJSON(w, 400, "note_too_long"); return }
	uid := userID(r); var cid, target uuid.UUID; err := s.db.QueryRow(r.Context(), `select id,case when member_a=$1 then member_b else member_a end from couples where status='active' and (member_a=$1 or member_b=$1)`, uid).Scan(&cid, &target); if err != nil { errorJSON(w, 409, "not_matched"); return }
	var existing eventJSON; var existingID uuid.UUID; var actor, targetName string; var note *string; var at time.Time; var d, sa int
	err = s.db.QueryRow(r.Context(), `select e.id,au.display_name,tu.display_name,e.delta,e.score_after,e.note,e.created_at from score_events e join app_users au on au.id=e.actor_id join app_users tu on tu.id=e.target_user_id where e.actor_id=$1 and e.idempotency_key=$2`, uid, req.IdempotencyKey).Scan(&existingID, &actor, &targetName, &d, &sa, &note, &at); if err == nil { existing = eventJSON{existingID.String(), uid.String(), actor, targetName, d, sa, note, at}; writeJSON(w, 200, existing); return }
	tx, err := s.db.Begin(r.Context()); if err != nil { errorJSON(w, 500, "database_error"); return }; defer tx.Rollback(r.Context())
	var current int; var min, max *int; var addMin, addMax, subtractMin, subtractMax int
	err = tx.QueryRow(r.Context(), `select cs.current_score,ss.min_score,ss.max_score,ss.add_min,ss.add_max,ss.subtract_min,ss.subtract_max from couple_scores cs join score_settings ss on ss.couple_id=cs.couple_id where cs.couple_id=$1 and cs.target_user_id=$2 for update`, cid, target).Scan(&current, &min, &max, &addMin, &addMax, &subtractMin, &subtractMax); if err != nil { errorJSON(w, 500, "database_error"); return }
	amount := req.Delta; if amount < 0 { amount = -amount }; if req.Delta > 0 && (amount < addMin || amount > addMax) { errorJSON(w, 400, "add_delta_out_of_range"); return }; if req.Delta < 0 && (amount < subtractMin || amount > subtractMax) { errorJSON(w, 400, "subtract_delta_out_of_range"); return }
	next := current + req.Delta; if min != nil && next < *min { errorJSON(w, 400, "below_minimum"); return }; if max != nil && next > *max { errorJSON(w, 400, "above_maximum"); return }
	eid := uuid.New(); var created time.Time; if err = tx.QueryRow(r.Context(), `update couple_scores set current_score=$1,updated_at=now() where couple_id=$2 and target_user_id=$3 returning updated_at`, next, cid, target).Scan(&created); err != nil { errorJSON(w, 500, "database_error"); return }
	if _, err = tx.Exec(r.Context(), `insert into score_events(id,couple_id,actor_id,target_user_id,idempotency_key,delta,score_after,note) values($1,$2,$3,$4,$5,$6,$7,nullif(trim($8),''))`, eid, cid, uid, target, req.IdempotencyKey, req.Delta, next, req.Note); err != nil { errorJSON(w, 500, "database_error"); return }; if err = tx.Commit(r.Context()); err != nil { errorJSON(w, 500, "database_error"); return }
	var actorDisplayName, targetDisplayName string
	_ = s.db.QueryRow(r.Context(), `select (select display_name from app_users where id=$1), (select display_name from app_users where id=$2)`, uid, target).Scan(&actorDisplayName, &targetDisplayName)
	writeJSON(w, 200, eventJSON{eid.String(), uid.String(), actorDisplayName, targetDisplayName, req.Delta, next, optional(req.Note), created})
}
type ruleChangeRequestBody struct { Initial int `json:"initial_score"`; Min *int `json:"min_score"`; Max *int `json:"max_score"`; AddMin int `json:"add_min"`; AddMax int `json:"add_max"`; SubtractMin int `json:"subtract_min"`; SubtractMax int `json:"subtract_max"` }
type pendingRulesJSON struct { ID string `json:"id"`; RequesterID string `json:"requester_id"`; Initial int `json:"initial_score"`; Min *int `json:"min_score"`; Max *int `json:"max_score"`; AddMin int `json:"add_min"`; AddMax int `json:"add_max"`; SubtractMin int `json:"subtract_min"`; SubtractMax int `json:"subtract_max"`; CreatedAt time.Time `json:"created_at"` }
type ruleDecisionJSON struct { RequesterID string `json:"requester_id"`; Status string `json:"status"`; RespondedAt time.Time `json:"responded_at"` }

func (s *server) createRulesRequest(w http.ResponseWriter, r *http.Request) {
	var req ruleChangeRequestBody
	if !decodeJSON(w, r, &req) { return }
	addMin, addMax, subtractMin, subtractMax := req.AddMin, req.AddMax, req.SubtractMin, req.SubtractMax
	if addMin == 0 { addMin = 1 }; if addMax == 0 { addMax = 5 }; if subtractMin == 0 { subtractMin = 1 }; if subtractMax == 0 { subtractMax = 5 }
	if code := validateScoreRules(req.Initial, req.Min, req.Max, addMin, addMax, subtractMin, subtractMax); code != "" { errorJSON(w, 400, code); return }
	uid := userID(r); var cid uuid.UUID
	if err := s.db.QueryRow(r.Context(), `select id from couples where status='active' and (member_a=$1 or member_b=$1)`, uid).Scan(&cid); err != nil { errorJSON(w, 409, "not_matched"); return }
	var pendingID uuid.UUID
	if err := s.db.QueryRow(r.Context(), `select id from rule_change_requests where couple_id=$1 and status='pending'`, cid).Scan(&pendingID); err == nil { errorJSON(w, 409, "request_pending"); return }
	var bad bool; _ = s.db.QueryRow(r.Context(), `select exists(select 1 from couple_scores where couple_id=$1 and (($2::integer is not null and current_score<$2) or ($3::integer is not null and current_score>$3)))`, cid, req.Min, req.Max).Scan(&bad)
	if bad { errorJSON(w, 400, "range_does_not_include_current_score"); return }
	id := uuid.New()
	if _, err := s.db.Exec(r.Context(), `insert into rule_change_requests(id,couple_id,requester_id,initial_score,min_score,max_score,add_min,add_max,subtract_min,subtract_max) values($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`, id, cid, uid, req.Initial, req.Min, req.Max, addMin, addMax, subtractMin, subtractMax); err != nil {
		// The partial unique index guards against two simultaneous requests.
		if strings.Contains(err.Error(), "duplicate") { errorJSON(w, 409, "request_pending") } else { errorJSON(w, 500, "database_error") }
		return
	}
	writeJSON(w, 200, map[string]any{"id": id.String(), "status": "pending"})
}

func (s *server) acceptRulesRequest(w http.ResponseWriter, r *http.Request) { s.respondRules(w, r, true) }
func (s *server) rejectRulesRequest(w http.ResponseWriter, r *http.Request) { s.respondRules(w, r, false) }
func (s *server) respondRules(w http.ResponseWriter, r *http.Request, accept bool) {
	uid := userID(r); id, err := uuid.Parse(r.PathValue("id")); if err != nil { errorJSON(w, 400, "invalid_request"); return }
	tx, err := s.db.Begin(r.Context()); if err != nil { errorJSON(w, 500, "database_error"); return }; defer tx.Rollback(r.Context())
	var cid, requester uuid.UUID; var status string; var initial int; var min, max *int; var addMin, addMax, subtractMin, subtractMax int
	if err = tx.QueryRow(r.Context(), `select couple_id,requester_id,status,initial_score,min_score,max_score,add_min,add_max,subtract_min,subtract_max from rule_change_requests where id=$1 for update`, id).Scan(&cid, &requester, &status, &initial, &min, &max, &addMin, &addMax, &subtractMin, &subtractMax); err != nil { errorJSON(w, 404, "request_not_found"); return }
	var member bool; _ = tx.QueryRow(r.Context(), `select exists(select 1 from couples where id=$1 and status='active' and (member_a=$2 or member_b=$2))`, cid, uid).Scan(&member)
	if !member { errorJSON(w, 403, "forbidden"); return }
	if status != "pending" { errorJSON(w, 409, "request_already_handled"); return }
	if requester == uid { errorJSON(w, 403, "cannot_respond_own_request"); return }
	if accept {
		var has bool; _ = tx.QueryRow(r.Context(), `select exists(select 1 from score_events where couple_id=$1)`, cid).Scan(&has)
		if has { var bad bool; _ = tx.QueryRow(r.Context(), `select exists(select 1 from couple_scores where couple_id=$1 and (($2::integer is not null and current_score<$2) or ($3::integer is not null and current_score>$3)))`, cid, min, max).Scan(&bad); if bad { errorJSON(w, 400, "range_does_not_include_current_score"); return } }
		if _, err = tx.Exec(r.Context(), `update score_settings set initial_score=$1,min_score=$2,max_score=$3,add_min=$4,add_max=$5,subtract_min=$6,subtract_max=$7,updated_at=now() where couple_id=$8`, initial, min, max, addMin, addMax, subtractMin, subtractMax, cid); err != nil { errorJSON(w, 500, "database_error"); return }
		if !has { if _, err = tx.Exec(r.Context(), `update couple_scores set current_score=$1,updated_at=now() where couple_id=$2`, initial, cid); err != nil { errorJSON(w, 500, "database_error"); return } }
	}
	newStatus := "rejected"; if accept { newStatus = "accepted" }
	if _, err = tx.Exec(r.Context(), `update rule_change_requests set status=$1,responded_at=now() where id=$2`, newStatus, id); err != nil { errorJSON(w, 500, "database_error"); return }
	if err = tx.Commit(r.Context()); err != nil { errorJSON(w, 500, "database_error"); return }
	writeJSON(w, 200, map[string]string{"status": newStatus})
}
func (s *server) cancelRulesRequest(w http.ResponseWriter, r *http.Request) {
	uid := userID(r); id, err := uuid.Parse(r.PathValue("id")); if err != nil { errorJSON(w, 400, "invalid_request"); return }
	tag, err := s.db.Exec(r.Context(), `update rule_change_requests set status='cancelled',responded_at=now() where id=$1 and requester_id=$2 and status='pending'`, id, uid)
	if err != nil { errorJSON(w, 500, "database_error"); return }; if tag.RowsAffected() == 0 { errorJSON(w, 409, "request_not_pending"); return }
	writeJSON(w, 200, map[string]string{"status": "cancelled"})
}
func other(uid,a,b uuid.UUID)uuid.UUID{if uid==a{return b};return a};func optional(v string)*string{v=strings.TrimSpace(v);if v==""{return nil};return &v};func sha256Bytes(v string)[]byte{h:=sha256.Sum256([]byte(v));return h[:]};func tokenHash(token string)[]byte{return sha256Bytes(token)};func newSessionToken()(string,[]byte,error){raw:=make([]byte,32);if _,err:=rand.Read(raw);err!=nil{return "",nil,err};token:=base64.RawURLEncoding.EncodeToString(raw);return token,tokenHash(token),nil}
func validateScoreRules(initial int, min, max *int, addMin, addMax, subtractMin, subtractMax int) string { if min != nil && max != nil && *min > *max { return "invalid_score_range" }; if min != nil && initial < *min || max != nil && initial > *max { return "initial_score_outside_range" }; if addMin < 1 || addMax < addMin || addMax > 100 { return "invalid_add_range" }; if subtractMin < 1 || subtractMax < subtractMin || subtractMax > 100 { return "invalid_subtract_range" }; return "" }
func validPassword(v string)bool{return len([]rune(v))>=8&&len([]rune(v))<=64&&passwordUpper.MatchString(v)&&passwordLower.MatchString(v)&&passwordDigit.MatchString(v)&&passwordSpecial.MatchString(v)}
func hashPassword(password string)(string,error){salt:=make([]byte,16);if _,err:=rand.Read(salt);err!=nil{return "",err};hash:=argon2.IDKey([]byte(password),salt,3,64*1024,1,32);return fmt.Sprintf("$argon2id$v=19$m=65536,t=3,p=1$%s$%s",base64.RawStdEncoding.EncodeToString(salt),base64.RawStdEncoding.EncodeToString(hash)),nil}
func verifyPassword(password,encoded string)bool{parts:=strings.Split(encoded,"$");if len(parts)!=6{return false};params:=strings.Split(parts[3],",");var mem uint32;var iter uint32;var parallel uint8;for _,p:=range params{kv:=strings.SplitN(p,"=",2);if len(kv)!=2{continue};switch kv[0]{case "m":v,_:=strconv.ParseUint(kv[1],10,32);mem=uint32(v);case "t":v,_:=strconv.ParseUint(kv[1],10,32);iter=uint32(v);case "p":v,_:=strconv.ParseUint(kv[1],10,8);parallel=uint8(v)}};salt,err:=base64.RawStdEncoding.DecodeString(parts[4]);if err!=nil{return false};expected,err:=base64.RawStdEncoding.DecodeString(parts[5]);if err!=nil{return false};actual:=argon2.IDKey([]byte(password),salt,iter,mem,parallel,uint32(len(expected)));return subtle.ConstantTimeCompare(actual,expected)==1}
func decodeJSON(w http.ResponseWriter,r *http.Request,v any)bool{defer r.Body.Close();r.Body=http.MaxBytesReader(w,r.Body,1<<20);decoder:=json.NewDecoder(r.Body);if decoder.Decode(v)!=nil{errorJSON(w,400,"invalid_json");return false};return true};func writeJSON(w http.ResponseWriter,status int,v any){w.Header().Set("Content-Type","application/json");w.WriteHeader(status);_=json.NewEncoder(w).Encode(v)};func errorJSON(w http.ResponseWriter,status int,code string){writeJSON(w,status,map[string]string{"error":code})};func logging(next http.Handler)http.Handler{return http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){start:=time.Now();next.ServeHTTP(w,r);log.Printf("%s %s %s",r.Method,r.URL.Path,time.Since(start))})}
