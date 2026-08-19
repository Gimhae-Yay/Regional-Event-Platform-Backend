#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIRECTORY/../../.." && pwd)"
readonly HTTP_DIRECTORY="$(cd "$SCRIPT_DIRECTORY/.." && pwd)"
readonly PASSWORD='Test!23456'

base_url='http://localhost:8080'
prepare_only=false
temporary_directory=''

print_usage() {
    cat <<'EOF'
사용법: bash http/p0/scripts/run.sh [--base-url URL] [--prepare-only]

P0 전용 시드를 적용한 뒤 P0 HTTP 시나리오 전체를 실행합니다.
EOF
}

fail() {
    printf '오류: %s\n' "$1" >&2
    exit 1
}

cleanup() {
    if [[ -n "$temporary_directory" ]]; then
        rm -rf "$temporary_directory"
    fi
}

get_access_token() {
    local email="$1"
    local response_file="$temporary_directory/${email%%@*}.json"
    local access_token

    if ! curl --silent --show-error --fail \
        --output "$response_file" \
        --header 'Content-Type: application/json' \
        --data "{\"email\":\"$email\",\"password\":\"$PASSWORD\"}" \
        "$base_url/api/v1/auth/login"; then
        fail "로그인 요청에 실패했습니다: $email"
    fi

    access_token="$(sed -nE 's/.*"accessToken"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' "$response_file" | tail -n 1)"

    [[ -n "$access_token" ]] || fail "로그인 응답에서 accessToken을 찾지 못했습니다: $email"
    printf '%s' "$access_token"
}

wait_for_application() {
    for _ in $(seq 1 30); do
        if curl --silent --show-error --fail --output /dev/null "$base_url/api/v1/regions"; then
            return
        fi
        sleep 1
    done

    fail "로컬 애플리케이션 응답을 확인하지 못했습니다: $base_url/api/v1/regions"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --base-url)
            [[ $# -ge 2 ]] || fail '--base-url에는 URL 값이 필요합니다.'
            base_url="$2"
            shift 2
            ;;
        --prepare-only)
            prepare_only=true
            shift
            ;;
        --help|-h)
            print_usage
            exit 0
            ;;
        *)
            print_usage >&2
            fail "지원하지 않는 옵션입니다: $1"
            ;;
    esac
done

command -v docker >/dev/null 2>&1 || fail 'Docker CLI를 찾을 수 없습니다. Docker Desktop을 설치하고 실행하세요.'
command -v curl >/dev/null 2>&1 || fail 'curl을 찾을 수 없습니다.'
[[ -d "$HTTP_DIRECTORY" ]] || fail "P0 HTTP 디렉터리를 찾을 수 없습니다: $HTTP_DIRECTORY"

bash "$SCRIPT_DIRECTORY/prepare.sh"

if [[ "$prepare_only" == true ]]; then
    exit 0
fi

wait_for_application

temporary_directory="$(mktemp -d)"
trap cleanup EXIT

region_admin_access_token="$(get_access_token 'p0-region-admin@example.test')"
other_region_admin_access_token="$(get_access_token 'p0-other-region-admin@example.test')"
operator_access_token="$(get_access_token 'p0-operator@example.test')"
other_operator_access_token="$(get_access_token 'p0-other-operator@example.test')"
visitor_access_token="$(get_access_token 'p0-visitor@example.test')"
other_visitor_access_token="$(get_access_token 'p0-other-visitor@example.test')"

http_files=()
while IFS= read -r http_file; do
    http_files+=("http/p0/$http_file")
done < <(find "$HTTP_DIRECTORY" -maxdepth 1 -type f -name '*.http' -exec basename {} \; | sort)

[[ ${#http_files[@]} -gt 0 ]] || fail "P0 HTTP 파일을 찾을 수 없습니다: $HTTP_DIRECTORY"

docker_arguments=(
    run --rm
    -v "$PROJECT_ROOT:/workdir"
    -w /workdir
    jetbrains/intellij-http-client
    -D
    -L BASIC
    --no-progress
    -V "p0RegionAdminAccessToken=$region_admin_access_token"
    -V "p0OtherRegionAdminAccessToken=$other_region_admin_access_token"
    -V "p0OperatorAccessToken=$operator_access_token"
    -V "p0OtherOperatorAccessToken=$other_operator_access_token"
    -V "p0VisitorAccessToken=$visitor_access_token"
    -V "p0OtherVisitorAccessToken=$other_visitor_access_token"
    -V 'p0PublishedContentId=900001'
    -V 'p0ScheduledSessionId=910001'
    -V 'p0SoldOutContentId=900001'
    -V 'p0SoldOutSessionId=910002'
    -V 'p0StartedSessionId=910003'
    -V 'p0ExpiredHoldId=940002'
    -V 'p0ReservationId=930001'
    -V 'p0CheckedInReservationId=930003'
    -V 'p0QrReservationId=930006'
    -V 'p0CancelledReservationNo=RLOCALCANCEL'
    -V 'p0CompletedSessionReservationNo=RLOCALCOMPLETED'
    -V 'p0ManualReservationNo=RLOCALMANUAL2'
    -V 'p0VisitId=950001'
    -V 'p0OtherVisitId=950002'
    -V 'p0ReviewId=960004'
    -V 'p0OtherReviewId=960001'
    -V 'p0QrExceptionId=990001'
)

docker_arguments+=("${http_files[@]}")

cd "$PROJECT_ROOT"
docker "${docker_arguments[@]}"
