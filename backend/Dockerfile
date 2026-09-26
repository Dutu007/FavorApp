FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/favorapp-api .

FROM alpine:3.21
RUN adduser -D -H -u 10001 app
COPY --from=build /out/favorapp-api /favorapp-api
USER app
EXPOSE 8080
ENTRYPOINT ["/favorapp-api"]
