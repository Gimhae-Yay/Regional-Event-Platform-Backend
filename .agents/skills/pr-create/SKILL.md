---
name: pr-create
description: 현재 브랜치의 커밋과 `dev` 기준 diff를 검증하고, PR 템플릿·제목·라벨·이슈 연결 규칙에 맞는 한국어 GitHub Pull Request를 생성한다.
  사용자가 "PR 만들어줘", "PR 올려줘", "풀 리퀘스트 생성해줘", "현재 브랜치를 dev에 올려줘"처럼 현재 변경의 PR 등록을 요청할 때 사용한다.
---

# Pull Request 생성

현재 브랜치의 커밋된 변경만 근거로 `Gimhae-Yay/Regional-Event-Platform-Backend`의 PR을 생성한다. 설명, 이슈 관계, 검증 결과를 추측하지 않는다.

## 저장소 규칙

- 일반 작업 PR의 base는 `dev`로 한다. 사용자가 다른 base를 명시한 경우에만 변경하고, 본문의 `/review base=<base>`도 일치시킨다.
- 현재 브랜치가 base이거나 base 대비 커밋이 없으면 PR을 만들지 않는다.
- 브랜치 이름만으로 작업 종류나 이슈를 추측하지 않는다.
- 제목은 `<type>: <한국어 요약>` 형식으로 작성한다. type은 이슈 완료 조건과 전체 diff의 주목적을 기준으로 고른다.
- `.github/pull_request_template.md`의 섹션 순서와 체크리스트를 모두 유지한다.
- 사용자가 명시한 경우에만 draft PR을 만든다.
- 코드, 테스트, 문서, 이슈 또는 커밋을 수정·생성하지 않는다. 검증 명령 실행, push, PR 생성만 수행한다.

## 커밋 type과 라벨

`origin/<base>..HEAD`의 병합 커밋을 제외한 모든 제목은 소문자 `<type>: <summary>` 형식이어야 한다. 각 type을 다음 저장소 라벨로 매핑하고 중복 없이 모두 적용한다.

| 커밋 type | PR 라벨 |
| --- | --- |
| `feat` | `New Feature` |
| `fix` | `bug` |
| `refactor` | `Refactor` |
| `docs` | `documentation` |
| `chore` | `Chore` |

- 형식에 맞지 않는 커밋, 대문자 type, 표에 없는 type이 하나라도 있으면 생성을 중단하고 해당 커밋을 보고한다.
- `test`, `perf`, `build`, `ci` 등의 라벨이 없다면 다른 라벨로 대체하거나 라벨 없이 생성하지 않는다.
- `gh label list --limit 200`으로 산정한 라벨이 현재도 존재하는지 확인한다. 하나라도 없으면 중단한다.
- PR 제목의 type도 표에 있어야 한다. 여러 type이면 전체 변경의 주목적 하나를 사용한다.

## 이슈 연결

- 요청, 브랜치 이름 또는 커밋 본문에서 번호가 명시적으로 확인될 때만 `gh issue view <번호>`로 이슈를 읽는다.
- 여러 번호가 있거나 관계가 불명확하면 PR을 만들기 전에 사용자에게 확인한다.
- diff가 완료 조건을 전부 충족할 때만 `Closes #<번호>`를 쓴다.
- 일부만 관련되면 `Related #<번호>`, 선행 조건이면 `Depends on #<번호>`를 사용한다.
- 연결할 이슈가 없으면 `## 이슈` 아래에 `- 없음`이라고 적고 빈 `Closes #`를 남기지 않는다.
- 이슈 TODO가 실제로 모두 완료됐을 때만 해당 검증 항목을 완료 처리한다. 이 스킬이 이슈를 수정하지 않는다.

## 생성 절차

1. 가장 가까운 `AGENTS.md`, `.github/pull_request_template.md`, 추가 PR 규칙을 읽는다.
2. `git status --short --branch`를 확인한다. staged, unstaged, untracked 변경이 하나라도 있으면 PR을 만들지 않는다.
3. `gh auth status`와 `git remote -v`로 인증 및 대상 저장소를 확인한다. `origin`이 예상 저장소가 아니면 중단한다.
4. `git fetch origin`으로 갱신한다. 기본 base는 `dev`다. 저장소 기본 브랜치와 템플릿이 충돌하면 추측하지 않고 보고한다.
5. `git merge-base --is-ancestor origin/<base> HEAD`와 커밋 수로 base에서 분기했고 고유 커밋이 있는지 확인한다.
6. `git log --format=fuller origin/<base>..HEAD`, `git diff --stat origin/<base>...HEAD`, `git diff origin/<base>...HEAD`를 읽는다.
7. 일반 커밋 제목을 검사하고 라벨을 산정한다. 커밋 형식이나 라벨 검증에 실패하면 중단한다.
8. 확인된 이슈의 완료 조건, TODO, 연결 방식을 diff와 대조한다. 번호나 완료 여부를 추측하지 않는다.
9. `git diff --check origin/<base>...HEAD`를 실행한다. Java, Gradle, 설정, 리소스 변경이면 Windows에서 `.\gradlew.bat build`, 그 밖에서는 `./gradlew build`를 실행한다. 변경된 동작에 맞는 관련 테스트가 있으면 함께 실행한다.
10. 필수 검증이 실패하면 기본적으로 중단한다. 사용자가 실패 상태로도 생성을 명시하면 결과와 위험을 적고 체크박스를 완료 처리하지 않는다.
11. `gh pr list --head <branch> --state all`로 중복을 확인한다. 열린 PR이 있으면 새로 만들지 않고 기존 PR을 검증해 보고한다. 닫힌 PR만 있으면 새 PR 필요 여부를 확인한다.
12. 제목과 본문을 자체 점검한 뒤 `git push -u origin <branch>`를 실행한다. 실패하면 재시도 전에 원격 브랜치와 PR을 다시 조회한다.
13. 저장소 밖의 임시 파일에 본문을 저장하고 `gh pr create --base <base> --head <branch> --title <제목> --body-file <파일>`을 실행한다. 라벨별 `--label`과 요청된 경우에만 `--draft`를 추가한다.
14. `gh pr view --json number,title,url,state,isDraft,baseRefName,headRefName,body,labels`로 결과를 확인하고 임시 파일을 제거한다.
15. base, head, 제목, 템플릿 섹션, 이슈 관계, draft 상태, 라벨이 다르면 숨기지 말고 차이를 보고한다.

## PR 본문 작성

- `요약`: 사용자 관점의 결과와 PR 경계를 1~3개 항목으로 적는다.
- `이슈`: `Closes`, `Related`, `Depends on` 또는 `없음`을 명시한다.
- `변경 내용`: 커밋 제목을 복사하지 말고 실제 구현·설정·문서 변경을 구체적으로 적는다.
- `리뷰 포인트`: 실제 diff에서 실패 비용이 크거나 검증하기 어려운 경계와 리뷰어가 확인할 조건을 적는다.
- `검증`: 실행한 명령과 결과만 기록한다. 미실행·실패 항목은 체크하지 않는다. JaCoCo가 없으면 미도입이라고 덧붙인다.
- `리뷰`: `/review base=<base>`를 실제 완료했을 때만 첫 항목을 체크한다. P0·P1이 없거나 모두 해결됐을 때만 두 번째 항목을 체크한다.

구체적인 결과가 필요하면 기존 체크리스트를 삭제하지 말고 아래에 명령, 성공·실패, 미실행 사유를 추가한다.

## 완료 보고

- PR 번호, 제목, URL, base/head, draft 여부, 적용 라벨을 보고한다.
- 실행한 검증과 결과, 미실행 검증, 이슈 관계, 의존성, 실패와 남은 위험을 밝힌다.
- 만들지 못했다면 중단 단계와 증거, 사용자가 해결할 최소 조치를 설명한다.
