#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIRECTORY/../../.." && pwd)"
readonly CLEANUP_FILE="$SCRIPT_DIRECTORY/cleanup.sql"
readonly SEED_FILE="$SCRIPT_DIRECTORY/seed.sql"
readonly P0_PREPARE_SCRIPT="$PROJECT_ROOT/http/p0/scripts/prepare.sh"

skip_compose=false

print_usage() {
    cat <<'EOF'
사용법: bash http/p1/scripts/prepare.sh [--skip-compose]

P1 실행 데이터를 정리한 뒤 P0 공통 시드와 P1 전용 시드를 적용합니다.
EOF
}

fail() {
    printf '오류: %s\n' "$1" >&2
    exit 1
}

apply_mysql_file() {
    local file="$1"
    local description="$2"

    if ! docker compose exec -T mysql mysql -uregional_event -pregional_event regional_event < "$file"; then
        fail "$description 적용에 실패했습니다."
    fi
}

for argument in "$@"; do
    case "$argument" in
        --skip-compose)
            skip_compose=true
            ;;
        --help|-h)
            print_usage
            exit 0
            ;;
        *)
            print_usage >&2
            fail "지원하지 않는 옵션입니다: $argument"
            ;;
    esac
done

command -v docker >/dev/null 2>&1 || fail 'Docker CLI를 찾을 수 없습니다. Docker Desktop을 설치하고 실행하세요.'
[[ -f "$CLEANUP_FILE" ]] || fail "P1 정리 파일을 찾을 수 없습니다: $CLEANUP_FILE"
[[ -f "$SEED_FILE" ]] || fail "P1 시드 파일을 찾을 수 없습니다: $SEED_FILE"
[[ -f "$P0_PREPARE_SCRIPT" ]] || fail "P0 준비 스크립트를 찾을 수 없습니다: $P0_PREPARE_SCRIPT"

cd "$PROJECT_ROOT"

if [[ "$skip_compose" != true ]]; then
    docker compose up -d mysql redis
fi

mysql_ready=false
for _ in $(seq 1 30); do
    if docker compose exec -T -e MYSQL_PWD=regional_event mysql \
        mysqladmin ping -h localhost -uregional_event --silent >/dev/null 2>&1; then
        mysql_ready=true
        break
    fi
    sleep 1
done

[[ "$mysql_ready" == true ]] || fail 'MySQL이 30초 이내에 준비 상태가 되지 않았습니다.'

apply_mysql_file "$CLEANUP_FILE" 'P1 MySQL 정리'
bash "$P0_PREPARE_SCRIPT" --skip-compose
apply_mysql_file "$SEED_FILE" 'P1 MySQL 시드'

printf 'P1 공통 시드와 Redis 초기화가 완료되었습니다.\n'
