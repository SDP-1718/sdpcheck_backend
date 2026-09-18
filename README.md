# sdpcheck

## 🛠 기술 스택

### Backend

![Java 21](https://img.shields.io/badge/Java_21-007396?style=flat&logo=openjdk&logoColor=white)
![Gradle 9.7.1](https://img.shields.io/badge/Gradle_9.7.1-02303A?style=flat&logo=gradle&logoColor=white)
![Spring Boot 4.1.1](https://img.shields.io/badge/Spring_Boot_4.1.1-6DB33F?style=flat&logo=springboot&logoColor=white)
![Spring MVC](https://img.shields.io/badge/Spring_MVC-6DB33F?style=flat&logo=spring&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=flat&logo=spring&logoColor=white)
![Validation](https://img.shields.io/badge/Bean_Validation-6DB33F?style=flat&logo=spring&logoColor=white)
![Lombok](https://img.shields.io/badge/Lombok-BC4521?style=flat&logoColor=white)

### Data

![PostgreSQL 17](https://img.shields.io/badge/PostgreSQL_17-4169E1?style=flat&logo=postgresql&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat&logo=flyway&logoColor=white)

### Test

![JUnit 5](https://img.shields.io/badge/JUnit_5-25A162?style=flat&logo=junit5&logoColor=white)
![Testcontainers](https://img.shields.io/badge/Testcontainers-2496ED?style=flat&logo=docker&logoColor=white)

### Infrastructure / CI

![Docker Compose](https://img.shields.io/badge/Docker_Compose-2496ED?style=flat&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat&logo=githubactions&logoColor=white)

> Docker Compose는 로컬 PostgreSQL 실행용이며, CI는 모든 PR 및 `main`·`develop` push에서 `./gradlew check`를 실행합니다.

## 🌿 브랜치 전략

```text
main ← develop ← feat/#{이슈번호}-{기능명}
```

| 브랜치 | 용도 |
|---|---|
| `main` | 운영 배포 브랜치. 직접 push 금지 |
| `develop` | 통합 개발 브랜치. 기능 브랜치가 병합되는 대상 |
| `feat/#이슈번호-기능명` | 새로운 기능 개발 |
| `fix/#이슈번호-기능명` | 버그 수정 |
| `hotfix/#이슈번호-내용` | 운영 환경 긴급 수정 |

## ✍️ 커밋 컨벤션

```text
[type]: #이슈번호 작업 내용
```

| 타입 | 설명 |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없이 코드 구조 개선 |
| `chore` | 빌드, 설정, 의존성 등 기타 작업 |
| `docs` | 문서 수정 |
| `test` | 테스트 코드 추가 또는 수정 |
| `hotfix` | 운영 환경 긴급 수정 |
