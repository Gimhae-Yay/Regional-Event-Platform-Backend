#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIRECTORY/../../.." && pwd)"
readonly HTTP_DIRECTORY="$(cd "$SCRIPT_DIRECTORY/.." && pwd)"
readonly PASSWORD='Test!23456'

base_url='http://localhost:8080'
prepare_only=false
run_portone_fake_webhook=false
temporary_directory=''

print_usage() {
    cat <<'EOF'
사용법: bash http/p1/scripts/run.sh [--base-url URL] [--prepare-only] [--run-portone-fake-webhook]

P0 공통 시드와 P1 전용 시드를 적용한 뒤 P1 HTTP 시나리오 전체를 실행합니다.
PortOne fake 웹훅은 PORTONE_FAKE_ENABLED=true 전용 환경에서만 별도 실행합니다.
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

get_http_client_base_url() {
    local host_base_url="$1"

    if [[ "$host_base_url" =~ ^(https?)://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?(/.*)?$ ]]; then
        printf '%s://host.docker.internal%s%s' "${BASH_REMATCH[1]}" "${BASH_REMATCH[3]:-}" "${BASH_REMATCH[4]:-}"
        return
    fi

    printf '%s' "$host_base_url"
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
        --run-portone-fake-webhook)
            run_portone_fake_webhook=true
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
[[ -d "$HTTP_DIRECTORY" ]] || fail "P1 HTTP 디렉터리를 찾을 수 없습니다: $HTTP_DIRECTORY"

bash "$SCRIPT_DIRECTORY/prepare.sh"

if [[ "$prepare_only" == true ]]; then
    exit 0
fi

wait_for_application
http_client_base_url="$(get_http_client_base_url "$base_url")"

temporary_directory="$(mktemp -d)"
trap cleanup EXIT

region_admin_access_token="$(get_access_token 'p0-region-admin@example.test')"
operator_access_token="$(get_access_token 'p0-operator@example.test')"
visitor_access_token="$(get_access_token 'p0-visitor@example.test')"
other_visitor_access_token="$(get_access_token 'p0-other-visitor@example.test')"
platform_admin_access_token="$(get_access_token 'p1-platform-admin@example.test')"
super_admin_access_token="$(get_access_token 'p1-super-admin@example.test')"

http_files=()
if [[ "$run_portone_fake_webhook" == true ]]; then
    http_files=('http/p1/05-portone-webhook-fake.http')
else
    while IFS= read -r http_file; do
        [[ "$http_file" == '05-portone-webhook-fake.http' ]] && continue
        http_files+=("http/p1/$http_file")
    done < <(find "$HTTP_DIRECTORY" -maxdepth 1 -type f -name '*.http' -exec basename {} \; | sort)
fi

[[ ${#http_files[@]} -gt 0 ]] || fail "P1 HTTP 파일을 찾을 수 없습니다: $HTTP_DIRECTORY"

docker_arguments=(
    run --rm
    -v "$PROJECT_ROOT:/workdir"
    -w /workdir
    jetbrains/intellij-http-client
    -D
    -L BASIC
    --no-progress
    -V "baseUrl=$http_client_base_url"
    -V "p1RegionAdminAccessToken=$region_admin_access_token"
    -V "p1OperatorAccessToken=$operator_access_token"
    -V "p1VisitorAccessToken=$visitor_access_token"
    -V "p1OtherVisitorAccessToken=$other_visitor_access_token"
    -V "p1PlatformAdminAccessToken=$platform_admin_access_token"
    -V "p1SuperAdminAccessToken=$super_admin_access_token"
    -V 'p1RegionId=900001'
    -V 'p1PublishedContentId=900001'
    -V 'p1PublishedStampbookRewardCouponPolicyId=900101'
    -V 'p1PublishedStampbookId=900201'
    -V 'p1PendingStampbookId=900202'
    -V 'p1PublishedMissionRewardCouponPolicyId=900102'
    -V 'p1PublishedMissionId=900501'
    -V 'p1PendingMissionId=900502'
    -V 'p1MissionParticipationId=900601'
    -V 'p1VisitorVisitId=950001'
    -V 'p1VisitorCouponId=900701'
    -V 'p1PaymentHoldId=941001'
    -V 'p1PaymentId=961001'
    -V 'p1PaidReservationId=931001'
    -V 'p1RefundId=981001'
    -V 'p1PaymentDiscrepancyId=971001'
    -V 'p1RefundFailureId=981001'
    -V 'p1RegionAdminCandidateUserId=12'
)

docker_arguments+=("${http_files[@]}")

cd "$PROJECT_ROOT"
docker "${docker_arguments[@]}"
