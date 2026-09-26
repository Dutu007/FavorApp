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
type inviteRequest struct { Code string }
type scoreRequest struct { Delta int `json:"delta"`; Note string `json:"note"`; IdempotencyKey string `json:"idempotency_key"` }
type settingsRequest struct { Initial int `json:"initial_score"`; Min *int `json:"min_score"`; Max *int `json:"max_score"` }

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
	mux.Handle("PUT /api/v1/score-settings", s.auth(http.HandlerFunc(s.updateSettings)))
	mux.Handle("GET /api/v1/score-events", s.auth(http.HandlerFunc(s.events)))

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
func (s *server) auth(next http.Handler) http.Handler { return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { raw := strings.TrimSpace(strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")); if raw == "" { errorJSON(w, 401, "unauthorized"); return }; hash := sha256.Sum256([]byte(raw)); var id uuid.UUID; err := s.db.QueryRow(r.Context(), `select user_id from sessions where token_hash=$1 and expires_at > now()`, hash[:]).Scan(&id); if err != nil { errorJSON(w, 401, "unauthorized"); return }; next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), userKey, id))) }) }
func userID(r *http.Request) uuid.UUID { return r.Context().Value(userKey).(uuid.UUID) }

func (s *server) register(w http.ResponseWriter, r *http.Request) {
	var req registerRequest; if !decodeJSON(w, r, &req) { return }; username := strings.ToLower(strings.TrimSpace(req.Username)); if !usernamePattern.MatchString(username) { errorJSON(w, 400, "username_format"); return }; if !validPassword(req.Password) { errorJSON(w, 400, "password_format"); return }; display := strings.TrimSpace(req.DisplayName); if display == "" { display = username }; if len([]rune(display)) > 40 { errorJSON(w, 400, "display_name_too_long"); return }
	hash, err := hashPassword(req.Password); if err != nil { errorJSON(w, 500, "password_hash_failed"); return }; id := uuid.New(); _, err = s.db.Exec(r.Context(), `insert into app_users(id,username,display_name,password_hash) values($1,$2,$3,$4)`, id, username, display, hash); if err != nil { if strings.Contains(err.Error(), "duplicate") { errorJSON(w, 409, "username_taken") } else { errorJSON(w, 500, "database_error") }; return }; s.issueSession(w, r, id, userJSON{ID:id.String(),Username:username,DisplayName:display})
}
func (s *server) login(w http.ResponseWriter, r *http.Request) { var req loginRequest; if !decodeJSON(w,r,&req) { return }; var id uuid.UUID; var display, stored string; err := s.db.QueryRow(r.Context(), `select id,display_name,password_hash from app_users where username=$1`, strings.ToLower(strings.TrimSpace(req.Username))).Scan(&id,&display,&stored); if err != nil || !verifyPassword(req.Password,stored) { errorJSON(w,401,"invalid_credentials"); return }; s.issueSession(w,r,id,userJSON{ID:id.String(),Username:strings.ToLower(strings.TrimSpace(req.Username)),DisplayName:display}) }
func (s *server) issueSession(w http.ResponseWriter, r *http.Request, id uuid.UUID, user userJSON) { raw := make([]byte,32); if _,err:=rand.Read(raw); err!=nil { errorJSON(w,500,"session_failed"); return }; hash:=sha256.Sum256(raw); token:=base64.RawURLEncoding.EncodeToString(raw); _,err:=s.db.Exec(r.Context(),`insert into sessions(id,user_id,token_hash,expires_at) values($1,$2,$3,$4)`,uuid.New(),id,hash[:],time.Now().Add(30*24*time.Hour)); if err!=nil { errorJSON(w,500,"session_failed"); return }; writeJSON(w,200,authResponse{Token:token,User:user}) }
func (s *server) logout(w http.ResponseWriter, r *http.Request) { raw:=strings.TrimSpace(strings.TrimPrefix(r.Header.Get("Authorization"),"Bearer ")); h:=sha256.Sum256([]byte(raw)); _,_ = s.db.Exec(r.Context(),`delete from sessions where token_hash=$1`,h[:]); w.WriteHeader(http.StatusNoContent) }
func (s *server) me(w http.ResponseWriter, r *http.Request) { var u userJSON; err:=s.db.QueryRow(r.Context(),`select id,username,display_name from app_users where id=$1`,userID(r)).Scan(&u.ID,&u.Username,&u.DisplayName); if err!=nil { errorJSON(w,404,"user_not_found"); return }; writeJSON(w,200,u) }

func (s *server) createInvite(w http.ResponseWriter, r *http.Request) { uid:=userID(r); var exists bool; _=s.db.QueryRow(r.Context(),`select exists(select 1 from couples where status='active' and (member_a=$1 or member_b=$1))`,uid).Scan(&exists); if exists { errorJSON(w,409,"already_matched"); return }; raw:=make([]byte,5); if _,err:=rand.Read(raw);err!=nil{errorJSON(w,500,"invite_failed");return}; code:=strings.ToUpper(hex.EncodeToString(raw)); h:=sha256.Sum256([]byte(code)); _,err:=s.db.Exec(r.Context(),`insert into invites(id,inviter_id,code_hash,expires_at) values($1,$2,$3,$4)`,uuid.New(),uid,h[:],time.Now().Add(24*time.Hour));if err!=nil{errorJSON(w,500,"invite_failed");return};writeJSON(w,200,map[string]string{"code":code}) }
func (s *server) acceptInvite(w http.ResponseWriter, r *http.Request) { var req inviteRequest;if !decodeJSON(w,r,&req){return}; code:=strings.ToUpper(strings.TrimSpace(req.Code)); if code==""{errorJSON(w,400,"invalid_code");return}; tx,err:=s.db.Begin(r.Context());if err!=nil{errorJSON(w,500,"database_error");return};defer tx.Rollback(r.Context());uid:=userID(r);var inviter uuid.UUID;var inviteID uuid.UUID;err=tx.QueryRow(r.Context(),`select id,inviter_id from invites where code_hash=$1 and used_at is null and expires_at>now() for update`,sha256Bytes(code)).Scan(&inviteID,&inviter);if err!=nil{errorJSON(w,400,"invalid_or_expired_code");return};if inviter==uid{errorJSON(w,400,"cannot_match_self");return};var matched bool;_=tx.QueryRow(r.Context(),`select exists(select 1 from couples where status='active' and (member_a=$1 or member_b=$1 or member_a=$2 or member_b=$2))`,uid,inviter).Scan(&matched);if matched{errorJSON(w,409,"already_matched");return};cid:=uuid.New();if _,err=tx.Exec(r.Context(),`insert into couples(id,member_a,member_b) values($1,$2,$3)`,cid,inviter,uid);err!=nil{errorJSON(w,409,"already_matched");return};if _,err=tx.Exec(r.Context(),`insert into score_settings(couple_id) values($1)`,cid);err!=nil{errorJSON(w,500,"database_error");return};if _,err=tx.Exec(r.Context(),`insert into couple_scores(couple_id,target_user_id,current_score) values($1,$2,0),($1,$3,0)`,cid,inviter,uid);err!=nil{errorJSON(w,500,"database_error");return};if _,err=tx.Exec(r.Context(),`update invites set used_at=now(),used_by=$1 where id=$2`,uid,inviteID);err!=nil{errorJSON(w,500,"database_error");return};if err=tx.Commit(r.Context());err!=nil{errorJSON(w,500,"database_error");return};writeJSON(w,200,map[string]string{"couple_id":cid.String()}) }

func (s *server) couple(w http.ResponseWriter,r *http.Request){uid:=userID(r);var cid,a,b uuid.UUID;err:=s.db.QueryRow(r.Context(),`select id,member_a,member_b from couples where status='active' and (member_a=$1 or member_b=$1)`,uid).Scan(&cid,&a,&b);if errors.Is(err,pgx.ErrNoRows){writeJSON(w,200,nil);return};if err!=nil{errorJSON(w,500,"database_error");return};var settings struct{Initial int `json:"initial_score"`;Min *int `json:"min_score"`;Max *int `json:"max_score"`};if err=s.db.QueryRow(r.Context(),`select initial_score,min_score,max_score from score_settings where couple_id=$1`,cid).Scan(&settings.Initial,&settings.Min,&settings.Max);err!=nil{errorJSON(w,500,"database_error");return};rows,err:=s.db.Query(r.Context(),`select cs.target_user_id,u.username,u.display_name,cs.current_score from couple_scores cs join app_users u on u.id=cs.target_user_id where cs.couple_id=$1`,cid);if err!=nil{errorJSON(w,500,"database_error");return};defer rows.Close();type card struct{UserID string `json:"user_id"`;Name string `json:"name"`;Score int `json:"score"`};cards:=[]card{};for rows.Next(){var id uuid.UUID;var username,name string;var score int;if err=rows.Scan(&id,&username,&name,&score);err!=nil{errorJSON(w,500,"database_error");return};if name==""{name=username};cards=append(cards,card{id.String(),name,score})};events:=s.loadEvents(r.Context(),cid);writeJSON(w,200,map[string]any{"couple_id":cid.String(),"current_user_id":uid.String(),"current_user_name":s.displayName(r.Context(),uid),"partner_name":s.displayName(r.Context(),other(uid,a,b)),"cards":cards,"events":events,"settings":settings})}
type eventJSON struct{ID string `json:"id"`;ActorName string `json:"actor_name"`;TargetName string `json:"target_name"`;Delta int `json:"delta"`;ScoreAfter int `json:"score_after"`;Note *string `json:"note"`;CreatedAt time.Time `json:"created_at"`}
func (s *server) loadEvents(ctx context.Context,cid uuid.UUID)[]eventJSON{rows,err:=s.db.Query(ctx,`select e.id,au.display_name,tu.display_name,e.delta,e.score_after,e.note,e.created_at from score_events e join app_users au on au.id=e.actor_id join app_users tu on tu.id=e.target_user_id where e.couple_id=$1 order by e.created_at desc limit 200`,cid);if err!=nil{return []eventJSON{}};defer rows.Close();out:=[]eventJSON{};for rows.Next(){var id uuid.UUID;var a,t string;var d,sa int;var note *string;var at time.Time;if rows.Scan(&id,&a,&t,&d,&sa,&note,&at)==nil{out=append(out,eventJSON{id.String(),a,t,d,sa,note,at})}};return out}
func (s *server) events(w http.ResponseWriter,r *http.Request){var cid uuid.UUID;err:=s.db.QueryRow(r.Context(),`select id from couples where status='active' and (member_a=$1 or member_b=$1)`,userID(r)).Scan(&cid);if err!=nil{writeJSON(w,200,[]eventJSON{});return};writeJSON(w,200,s.loadEvents(r.Context(),cid))}
func (s *server) addScore(w http.ResponseWriter,r *http.Request){var req scoreRequest;if !decodeJSON(w,r,&req){return};if req.Delta==0||len(req.IdempotencyKey)<8||len(req.IdempotencyKey)>80{errorJSON(w,400,"invalid_score_request");return};uid:=userID(r);var cid,target uuid.UUID;err:=s.db.QueryRow(r.Context(),`select id,case when member_a=$1 then member_b else member_a end from couples where status='active' and (member_a=$1 or member_b=$1)`,uid).Scan(&cid,&target);if err!=nil{errorJSON(w,409,"not_matched");return};var existing eventJSON;var existingID uuid.UUID;var actor,targetName string;var note *string;var at time.Time;var d,sa int;err=s.db.QueryRow(r.Context(),`select e.id,au.display_name,tu.display_name,e.delta,e.score_after,e.note,e.created_at from score_events e join app_users au on au.id=e.actor_id join app_users tu on tu.id=e.target_user_id where e.actor_id=$1 and e.idempotency_key=$2`,uid,req.IdempotencyKey).Scan(&existingID,&actor,&targetName,&d,&sa,&note,&at);if err==nil{existing=eventJSON{existingID.String(),actor,targetName,d,sa,note,at};writeJSON(w,200,existing);return};tx,err:=s.db.Begin(r.Context());if err!=nil{errorJSON(w,500,"database_error");return};defer tx.Rollback(r.Context());var current int;var min,max *int;err=tx.QueryRow(r.Context(),`select cs.current_score,ss.min_score,ss.max_score from couple_scores cs join score_settings ss on ss.couple_id=cs.couple_id where cs.couple_id=$1 and cs.target_user_id=$2 for update`,cid,target).Scan(&current,&min,&max);if err!=nil{errorJSON(w,500,"database_error");return};next:=current+req.Delta;if min!=nil&&next<*min{errorJSON(w,400,"below_minimum");return};if max!=nil&&next>*max{errorJSON(w,400,"above_maximum");return};eid:=uuid.New();var created time.Time;if err=tx.QueryRow(r.Context(),`update couple_scores set current_score=$1,updated_at=now() where couple_id=$2 and target_user_id=$3 returning updated_at`,next,cid,target).Scan(&created);err!=nil{errorJSON(w,500,"database_error");return};if _,err=tx.Exec(r.Context(),`insert into score_events(id,couple_id,actor_id,target_user_id,idempotency_key,delta,score_after,note) values($1,$2,$3,$4,$5,$6,$7,nullif(trim($8),''))`,eid,cid,uid,target,req.IdempotencyKey,req.Delta,next,req.Note);err!=nil{errorJSON(w,500,"database_error");return};if err=tx.Commit(r.Context());err!=nil{errorJSON(w,500,"database_error");return};writeJSON(w,200,eventJSON{eid.String(),s.displayName(r.Context(),uid),s.displayName(r.Context(),target),req.Delta,next,optional(req.Note),created})}
func (s *server) updateSettings(w http.ResponseWriter,r *http.Request){var req settingsRequest;if !decodeJSON(w,r,&req){return};uid:=userID(r);var cid uuid.UUID;if err:=s.db.QueryRow(r.Context(),`select id from couples where status='active' and (member_a=$1 or member_b=$1)`,uid).Scan(&cid);err!=nil{errorJSON(w,409,"not_matched");return};tx,err:=s.db.Begin(r.Context());if err!=nil{errorJSON(w,500,"database_error");return};defer tx.Rollback(r.Context());if req.Min!=nil&&req.Max!=nil&&*req.Min>*req.Max{errorJSON(w,400,"invalid_score_range");return};var has bool;_ = tx.QueryRow(r.Context(),`select exists(select 1 from score_events where couple_id=$1)`,cid).Scan(&has);if (!has&&req.Min!=nil&&req.Initial<*req.Min)||(!has&&req.Max!=nil&&req.Initial>*req.Max){errorJSON(w,400,"initial_score_outside_range");return};if has{var bad bool; _ = tx.QueryRow(r.Context(),`select exists(select 1 from couple_scores where couple_id=$1 and (($2::integer is not null and current_score<$2) or ($3::integer is not null and current_score>$3)))`,cid,req.Min,req.Max).Scan(&bad);if bad{errorJSON(w,400,"range_does_not_include_current_score");return}};if _,err=tx.Exec(r.Context(),`update score_settings set initial_score=$1,min_score=$2,max_score=$3,updated_at=now() where couple_id=$4`,req.Initial,req.Min,req.Max,cid);err!=nil{errorJSON(w,500,"database_error");return};if !has{if _,err=tx.Exec(r.Context(),`update couple_scores set current_score=$1,updated_at=now() where couple_id=$2`,req.Initial,cid);err!=nil{errorJSON(w,500,"database_error");return}};if err=tx.Commit(r.Context());err!=nil{errorJSON(w,500,"database_error");return};writeJSON(w,200,map[string]any{"initial_score":req.Initial,"min_score":req.Min,"max_score":req.Max})}

func (s *server) displayName(ctx context.Context,id uuid.UUID)string{var name string;if s.db.QueryRow(ctx,`select display_name from app_users where id=$1`,id).Scan(&name)!=nil{return ""};return name};func other(uid,a,b uuid.UUID)uuid.UUID{if uid==a{return b};return a};func optional(v string)*string{v=strings.TrimSpace(v);if v==""{return nil};return &v};func sha256Bytes(v string)[]byte{h:=sha256.Sum256([]byte(v));return h[:]}
func validPassword(v string)bool{return len([]rune(v))>=8&&len([]rune(v))<=64&&passwordUpper.MatchString(v)&&passwordLower.MatchString(v)&&passwordDigit.MatchString(v)&&passwordSpecial.MatchString(v)}
func hashPassword(password string)(string,error){salt:=make([]byte,16);if _,err:=rand.Read(salt);err!=nil{return "",err};hash:=argon2.IDKey([]byte(password),salt,3,64*1024,1,32);return fmt.Sprintf("$argon2id$v=19$m=65536,t=3,p=1$%s$%s",base64.RawStdEncoding.EncodeToString(salt),base64.RawStdEncoding.EncodeToString(hash)),nil}
func verifyPassword(password,encoded string)bool{parts:=strings.Split(encoded,"$");if len(parts)!=6{return false};params:=strings.Split(parts[3],",");var mem uint32;var iter uint32;var parallel uint8;for _,p:=range params{kv:=strings.SplitN(p,"=",2);if len(kv)!=2{continue};switch kv[0]{case "m":v,_:=strconv.ParseUint(kv[1],10,32);mem=uint32(v);case "t":v,_:=strconv.ParseUint(kv[1],10,32);iter=uint32(v);case "p":v,_:=strconv.ParseUint(kv[1],10,8);parallel=uint8(v)}};salt,err:=base64.RawStdEncoding.DecodeString(parts[4]);if err!=nil{return false};expected,err:=base64.RawStdEncoding.DecodeString(parts[5]);if err!=nil{return false};actual:=argon2.IDKey([]byte(password),salt,iter,mem,parallel,uint32(len(expected)));return subtle.ConstantTimeCompare(actual,expected)==1}
func decodeJSON(w http.ResponseWriter,r *http.Request,v any)bool{defer r.Body.Close();r.Body=http.MaxBytesReader(w,r.Body,1<<20);decoder:=json.NewDecoder(r.Body);if decoder.Decode(v)!=nil{errorJSON(w,400,"invalid_json");return false};return true};func writeJSON(w http.ResponseWriter,status int,v any){w.Header().Set("Content-Type","application/json");w.WriteHeader(status);_=json.NewEncoder(w).Encode(v)};func errorJSON(w http.ResponseWriter,status int,code string){writeJSON(w,status,map[string]string{"error":code})};func logging(next http.Handler)http.Handler{return http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){start:=time.Now();next.ServeHTTP(w,r);log.Printf("%s %s %s",r.Method,r.URL.Path,time.Since(start))})}
