# 0004. Публикация в Maven Central

[English version](0004-maven-central-publishing.md)

## Контекст

Библиотека должна подключаться как обычная зависимость сборок Maven и Gradle, а дистрибутив утилиты (ADR 0003) —
скачиваться из общеизвестного места. Maven Central — репозиторий по умолчанию обоих инструментов. Он принимает релиз
только с полными метаданными POM (имя, описание, URL, лицензия, разработчики, SCM), JAR исходников, JAR Javadoc и
PGP-подписями каждого файла. Релизы загружаются через Central Publisher Portal.

JAR — multi-release JAR с дескриптором модуля в `META-INF/versions/9`, который компилируется только на JDK 9+
(ADR 0002): релиз, собранный на JDK 8, молча потерял бы модуль.

## Решение

- Версии — `MAJOR.MINOR.PATCH`, версии разработки оканчиваются на `-SNAPSHOT`; первый релиз — `1.0.0`.
- В POM есть `url`, `licenses` (BSD-3-Clause), `developers` и `scm` (GitHub `sftwnd/oracle-oiuncomp`).
- Публикуемые артефакты: JAR, `-sources.jar`, `-javadoc.jar`, дистрибутив `-bin.tar.gz` и `-bin.zip`, POM.
- JAR исходников повторяет multi-release JAR. `maven-resources-plugin` копирует `src/main/java` в `target/sources`,
  а на JDK 9+ — `src/main/java9` в `target/sources/META-INF/versions/9`; `maven-jar-plugin` (выполнение
  `attach-sources`) упаковывает каталог с классификатором `sources`. `maven-source-plugin` пропускается
  (`maven.source.skip`).
- JAR Javadoc собирает `maven-javadoc-plugin` с `failOnWarnings`: недокументированный public- или protected-член
  ломает сборку.
- Релиз выполняет `maven-release-plugin` (`release:prepare`, `release:perform`) с исполнителем `forked-path`.
  `release:prepare` записывает в `project.build.outputTimestamp` время релиза, ставит тег `oracle-oiuncomp-<версия>`
  и переходит к следующей snapshot-версии.
- Профиль `sign-artifacts` включается плагином релиза (`releaseProfiles`) или `-DperformRelease=true`:
  - `maven-enforcer-plugin` требует JDK 9+;
  - `maven-gpg-plugin` подписывает все артефакты в фазе `verify` ключом `gpg.keyname` из `.mvn/settings.xml`;
    пароль ключа берётся из gpg-agent (`gpg.bestPractices`).
- `deploy` публикует через `central-publishing-maven-plugin` — расширение сборки, которое заменяет
  `maven-deploy-plugin`. Плагин аутентифицируется пользовательским токеном Portal из сервера `central` в
  `.mvn/settings.xml`.
- В `.mvn/settings.xml`, который wrapper берёт из `.mvn/maven.config`, нет секретов: токен берётся из переменных
  окружения `CENTRAL_TOKEN_USERNAME` и `CENTRAL_TOKEN_PASSWORD` (`~/.mavenrc`), пароль зашифрован
  `mvn --encrypt-password` мастер-паролем из `~/.m2/settings-security.xml`.
- `PackagingIT` проверяет раскладку JAR исходников.

## Обоснование

- Отладчик на Java 9+ ищет исходник класса по тому же пути в JAR исходников, поэтому исходнику дескриптора место в
  `META-INF/versions/9`. `maven-source-plugin` упаковывает только корни исходников проекта, а `src/main/java9` к ним
  не относится: его компилирует отдельное выполнение (ADR 0002). Как корень исходников он компилировался бы основным
  выполнением с `--release 8`.
- Wrapper запускает Maven 3.10, в супер-POM которого нет профиля release, но Maven 3.9 (например, Maven из IDE) с
  `-DperformRelease=true` запускает `maven-source-plugin` из своего профиля release. Пропущенный плагин не
  прикрепляет второй JAR исходников, который столкнулся бы с первым при загрузке.
- Пароль в gpg-agent не хранится ни в файле, ни в окружении каждой сборки, как рекомендуют best practices
  `maven-gpg-plugin` 3.2. В пакетном режиме заблокированный ключ сразу даёт ошибку, а не зависает на запросе пароля.
- Enforcer превращает молчаливую потерю дескриптора модуля в упавшую сборку.
- Central Publisher Portal — поддерживаемый способ публикации в Maven Central, и его плагину не нужна настройка
  staging в POM.

## Альтернативы

- **`maven-source-plugin` с `src/main/java9` как корнем исходников** (`build-helper-maven-plugin`). Исходник
  дескриптора попадает в корень JAR исходников, а основная компиляция с `--release 8` пытается его скомпилировать.
- **Пароль ключа из `.mvn/settings.xml`** (`-Dgpg.bestPractices=false` с `GPG_SIGNING_PASSPHRASE`). Оставлен запасным
  вариантом; по умолчанию секрет остаётся в gpg-agent.
- **`maven-deploy-plugin` в OSSRH.** Сервис закрыт.
- **JReleaser.** Ещё один инструмент со своей конфигурацией; для одного модуля хватает плагинов Maven.
- **Только GitHub Packages или GitHub Releases.** GitHub Packages требуют аутентификации даже для скачивания, и ни
  один из них не является репозиторием Maven или Gradle по умолчанию.

## Последствия

- Для релиза нужны JDK 9+, ключ подписи, разблокированный в gpg-agent (любая подпись `gpg` вне пакетного режима
  кэширует пароль), переменные токена в окружении и мастер-пароль в `~/.m2/settings-security.xml`.
- `groupId` `com.github.sftwnd` должен быть подтверждённым пространством имён в Central Publisher Portal для учётной
  записи токена.
- `release:perform` загружает deployment, который Portal проверяет; публикует его кнопка Publish в Portal
  (`autoPublish` выключен).
- Дистрибутив утилиты тоже публикуется в Maven Central, подписанный, с классификатором `bin`.
- Недокументированный публичный API ломает сборку.
- Сборка на JDK 8 даёт JAR исходников без исходника дескриптора, как и JAR без дескриптора.
