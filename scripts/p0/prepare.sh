#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIRECTORY/../.." && pwd)"
readonly SEED_FILE="$SCRIPT_DIRECTORY/seed.sql"

skip_compose=false

print_usage() {
    cat <<'EOF'
사용법: bash scripts/p0/prepare.sh [--skip-compose]

P0 전용 MySQL 시드와 Redis를 초기화합니다.
EOF
}

fail() {
    printf '오류: %s\n' "$1" >&2
    exit 1
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
[[ -f "$SEED_FILE" ]] || fail "P0 시드 파일을 찾을 수 없습니다: $SEED_FILE"

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

docker compose exec -T mysql mysql -uregional_event -pregional_event regional_event < "$SEED_FILE"
docker compose exec -T redis redis-cli FLUSHDB >/dev/null

printf 'P0 공통 시드와 Redis 초기화가 완료되었습니다.\n'
