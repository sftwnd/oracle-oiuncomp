# 0005. Непрерывная интеграция и анализ качества

[English version](0005-ci-and-quality-analysis.md)

## Контекст

Библиотека работает на Java 8+, а полный JAR с дескриптором модуля собирается на JDK 9+ (ADR 0002), как и релиз
(ADR 0004). Каждый push и pull request должен показывать, что проект собирается и проходит тесты на поддерживаемых
JDK и что JAR, собранный как релиз, работает на Java 8. Качество и покрытие кода тестами должны быть видны, в том
числе для нового кода.

## Решение

- Workflow GitHub Actions `.github/workflows/build.yml` запускается на push в `master` и `develop`, на pull request и
  вручную. Его токен — только на чтение (`contents: read`); новый push отменяет идущую проверку того же pull request.
- Задание `build` выполняет `./mvnw -B -ntp verify` на Temurin JDK 8, 11, 17, 21 и 25, каждое независимо от других
  (`fail-fast: false`).
- Задание `java8` собирает на JDK 21 и запускает тесты на Java 8 (`-Djvm` в surefire и failsafe): модульные тесты,
  `PackagingIT` (`java -jar`) и `DistributionIT` (launcher с `JAVA_HOME` тестов) выполняются на Java 8 с JAR, в
  котором есть дескриптор модуля.
- Задание `sonar` анализирует проект в SonarCloud (организация `sftwnd-github`, проект `sftwnd_oracle_oiuncomp`) на
  JDK 21 со всей историей Git. Токен — секрет репозитория `SONAR_TOKEN`; для pull request из форков, которым секреты
  недоступны, задание пропускается.
- Покрытие собирает `jacoco-maven-plugin`: `prepare-agent` для surefire и failsafe, `report` в фазе `verify` пишет
  `target/site/jacoco/jacoco.xml`, который читает сканер.
- Настройки Maven проекта — `.mvn/settings.xml`, их задаёт `.mvn/maven.config`: локальные сборки и CI получают
  зависимости из Maven Central и не зависят от `~/.m2/settings.xml`.
- В шапке README — бейджи: Maven Central, Java 8+, JPMS, OSGi, javadoc.io, quality gate и покрытие SonarCloud, статус
  сборки и лицензия.

## Обоснование

- javac, javadoc (`failOnWarnings`, ADR 0004) и bnd ведут себя на разных JDK по-разному; матрица покрывает
  LTS-версии от минимальной и новейший JDK.
- Сборка на JDK 8 даёт JAR без дескриптора, поэтому только JAR, собранный на JDK 9+, как и релиз, показывает, как
  Java 8 обходится с `META-INF/versions/9`. `-Djvm` использует существующие тесты JAR вместо отдельного скрипта.
- SonarCloud с покрытием JaCoCo даёт quality gate на новый код pull request; Maven-сканер берёт исходники, классы и
  библиотеки из POM. Полная история даёт данные blame, по которым отмечается новый код.
- Настройки, которые задаёт сам wrapper, не требуют аргумента в каждой команде, в IDE и в CI и не пускают в сборку
  проекта зеркала и учётные данные пользователя; секреты остаются в окружении (ADR 0004).

## Альтернативы

- **Один JDK в CI.** Пропускает различия javac и javadoc между версиями.
- **Shell-скрипт с потребителями на module path и class path Java 8.** Дублирует `PackagingIT`, который уже
  проверяет дескриптор и module path на каждом JDK матрицы.
- **Automatic Analysis в SonarCloud.** Не требует workflow, но анализирует без скомпилированных классов и без
  покрытия.
- **`settings.xml` в корне с `--settings` в каждой команде.** Легко забыть, и аргумент нужен каждому инструменту.

## Последствия

- В SonarCloud нужен проект `sftwnd_oracle_oiuncomp` в организации `sftwnd-github` с выключенным Automatic Analysis,
  а в репозитории — секрет `SONAR_TOKEN`.
- Репозиторий и проект SonarCloud публичные: приватные проекты SonarCloud анализирует только на платном тарифе, а
  бейджи GitHub, shields.io и SonarCloud показывают данные только публичных. Бейджи Maven Central и javadoc.io
  показывают данные после первого релиза.
- Каждый push запускает семь заданий.
- Сборка проекта не читает `~/.m2/settings.xml`: зеркало или прокси для неё задаются в `.mvn/settings.xml`.
