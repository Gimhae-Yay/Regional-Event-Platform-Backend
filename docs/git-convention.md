# Git Convention

## 브랜치명

- 형식: `<type>/<설명>` 또는 `<type>/<이슈번호>-<설명>`
- `type`은 작업 성격에 따라 `feature`, `fix`, `docs`, `refactor`, `test`, `chore` 중 하나를 사용한다.
- 연결된 GitHub Issue가 있으면 이슈 번호를 포함한다.
- 설명은 소문자 영어와 하이픈(`-`)만 사용하는 kebab-case로 작성한다.
- 예시:
    - `feature/reservation-api`
    - `feature/123-reservation-api`
    - `fix/payment-idempotency`
    - `docs/125-api-error-codes`