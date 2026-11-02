# Oracle oiuncomp

[![Maven Central](https://img.shields.io/maven-central/v/com.github.sftwnd/oracle-oiuncomp)](https://central.sonatype.com/artifact/com.github.sftwnd/oracle-oiuncomp)
[![Java 8+](https://img.shields.io/badge/Java-8%2B-blue)](#модули-java-и-osgi)
[![JPMS](https://img.shields.io/badge/JPMS-named%20module-blue)](#модули-java-и-osgi)
[![OSGi](https://img.shields.io/badge/OSGi-bundle-blue)](#модули-java-и-osgi)
[![javadoc](https://javadoc.io/badge2/com.github.sftwnd/oracle-oiuncomp/javadoc.svg)](https://javadoc.io/doc/com.github.sftwnd/oracle-oiuncomp)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=sftwnd_oracle_oiuncomp&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=sftwnd_oracle_oiuncomp)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=sftwnd_oracle_oiuncomp&metric=coverage)](https://sonarcloud.io/summary/new_code?id=sftwnd_oracle_oiuncomp)
[![Build](https://github.com/sftwnd/oracle-oiuncomp/actions/workflows/build.yml/badge.svg)](https://github.com/sftwnd/oracle-oiuncomp/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/sftwnd/oracle-oiuncomp)](https://github.com/sftwnd/oracle-oiuncomp/blob/master/LICENSE)

[English version](README.md)

**Библиотека и утилита командной строки на Java 8+ без зависимостей, которые распаковывают файлы `*_`
Oracle Installer.**

Реализация на Java утилиты `oiuncomp`, которая распаковывает файлы, сжатые Oracle Installer. В дистрибутивах
Oracle 7.3 для Unix такие файлы помечены символом подчёркивания в конце имени, например `dbmsalrt.sql_`. Проект —
одновременно библиотека и утилита командной строки. Публичный API находится в пакете
`com.github.sftwnd.oracle.oiuncomp`.

Формат сжатия не документирован. Его описание и обоснование устройства декодера — в
[ADR 0001](docs/adr/0001-oicomp-format_ru.md), упаковка (Java 8, модуль Java 9+, OSGi-бандл) — в
[ADR 0002](docs/adr/0002-java8-module-osgi_ru.md), дистрибутив утилиты командной строки — в
[ADR 0003](docs/adr/0003-command-line-distribution_ru.md), публикация в Maven Central — в
[ADR 0004](docs/adr/0004-maven-central-publishing_ru.md), непрерывная интеграция и анализ качества — в
[ADR 0005](docs/adr/0005-ci-and-quality-analysis_ru.md).

## Требования

- Java 8 или новее для запуска.
- JDK 9 или новее для сборки полного JAR: сборка на JDK 8 даёт JAR без дескриптора модуля.
- Доступ в интернет при первой загрузке Maven и его плагинов.

Устанавливать Maven отдельно не нужно: проект содержит Maven Wrapper (Maven 3.10.0).

## Сборка

Из корня проекта выполните:

```bash
./mvnw clean package
```

В Windows:

```bat
mvnw.cmd clean package
```

Wrapper берёт настройки проекта, `.mvn/settings.xml`, из `.mvn/maven.config`, поэтому `~/.m2/settings.xml` не
используется.

После успешной сборки JAR, его исходники и Javadoc, а также дистрибутив утилиты командной строки находятся здесь:

```text
target/oracle-oiuncomp-1.0.0-SNAPSHOT.jar
target/oracle-oiuncomp-1.0.0-SNAPSHOT-sources.jar
target/oracle-oiuncomp-1.0.0-SNAPSHOT-javadoc.jar
target/oracle-oiuncomp-1.0.0-SNAPSHOT-bin.tar.gz
target/oracle-oiuncomp-1.0.0-SNAPSHOT-bin.zip
```

JAR исходников повторяет раскладку multi-release JAR: исходник дескриптора модуля — в `META-INF/versions/9`.

## Командная строка

```text
usage: oiuncomp [-c | -d <directory>] [-f] <file_>...
  -c              write the uncompressed data to the standard output
  -d <directory>  write the files to <directory> instead of the directory of each file
  -f              overwrite existing files
  -h, --help      print this help
```

Распакованный файл получает имя без подчёркивания в конце и создаётся рядом со сжатым или в каталоге `-d`. Сжатый
файл сохраняется. Существующий файл перезаписывается только с `-f`, недописанный файл удаляется. Ошибка в одном
файле не останавливает обработку остальных. Коды завершения: `0` — все файлы распакованы, `1` — часть файлов не
распакована, `2` — неверные аргументы.

### Дистрибутив

Дистрибутив утилиты, `oracle-oiuncomp-1.0.0-SNAPSHOT-bin.tar.gz` или `oracle-oiuncomp-1.0.0-SNAPSHOT-bin.zip`, содержит:

```text
oracle-oiuncomp-1.0.0-SNAPSHOT/
├── bin/oiuncomp              запуск в Unix
├── bin/oiuncomp.cmd          запуск в Windows
├── lib/oracle-oiuncomp.jar
├── LICENSE
├── README.md
└── README_ru.md
```

Распакуйте его в любой каталог, затем добавьте `bin` в `PATH` или сделайте ссылку на скрипт запуска из каталога,
входящего в `PATH`:

```bash
tar xzf oracle-oiuncomp-1.0.0-SNAPSHOT-bin.tar.gz -C /opt
ln -s /opt/oracle-oiuncomp-1.0.0-SNAPSHOT/bin/oiuncomp /usr/local/bin/oiuncomp

oiuncomp dbmsalrt.sql_
find . -name '*_' -exec oiuncomp {} +
```

Скрипты запуска берут Java 8 или новее из `JAVA_HOME`, иначе из `PATH`; в `JAVA_OPTS` можно передать опции JVM.
В Unix лучше брать архив tar.gz: он сохраняет право на исполнение скрипта при распаковке любым tar, а zip — только
некоторыми распаковщиками.

### Запуск JAR

```bash
java -jar oracle-oiuncomp-1.0.0-SNAPSHOT.jar dbmsalrt.sql_
```

На Java 9+ JAR запускается и как модуль; основной класс указан в дескрипторе модуля:

```bash
java -p oracle-oiuncomp-1.0.0-SNAPSHOT.jar -m com.github.sftwnd.oracle.oiuncomp dbmsalrt.sql_
```

## Библиотека

Чтение распакованных данных потоком:

```java
import com.github.sftwnd.oracle.oiuncomp.OiuncompInputStream;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

try (InputStream input = new OiuncompInputStream(Files.newInputStream(Paths.get("dbmsalrt.sql_")))) {
    // чтение распакованных данных
}
```

Распаковка данных целиком:

```java
import com.github.sftwnd.oracle.oiuncomp.Oiuncomp;

long length = Oiuncomp.uncompress(compressedInput, output); // потоки не закрываются
byte[] data = Oiuncomp.uncompress(compressedBytes);
```

Сжатые данные должны занимать источник до конца: в формате нет маркера конца, длины и контрольной суммы.
Повреждённые данные — пустой источник, недопустимый заголовок или код, оборванный последний код — сообщаются
исключением `OiuncompException`, наследником `IOException`. Обрыв точно на границе кода обнаружить нельзя.

Сигнатуры (magic number) у формата тоже нет: первый байт — наибольшая ширина кода (14 в файлах дистрибутивов
Oracle), второй зависит от исходных данных. Чтобы узнать, похожи ли данные на сжатые, `isCompressed` проверяет сам
поток строгим декодером — заголовок и первые 64 кода, а у более коротких данных все коды и выравнивание:

```java
try (InputStream input = Files.newInputStream(Paths.get("dbmspipe.sql_"))) {
    boolean compressed = Oiuncomp.isCompressed(input); // поток читается с опережением и не закрывается
}
```

## Проверка на настоящих файлах

Настоящих файлов Oracle в проекте нет: они принадлежат Oracle. Чтобы проверить библиотеку на своих сжатых файлах,
передайте файл или каталог с файлами `*_`:

```bash
./mvnw test -Dtest=SampleFilesTest -Doiuncomp.samples=/path/to/files
```

Каждый файл должен распознаваться как сжатый и распаковываться без ошибок, а эталонный упаковщик из тестов —
восстанавливать его из распакованных данных побайтно. Без свойства проверка пропускается.

## Модули Java и OSGi

Библиотека скомпилирована для Java 8. Этот же JAR является:

- именованным модулем `com.github.sftwnd.oracle.oiuncomp` на Java 9+: дескриптор лежит в `META-INF/versions/9`
  multi-release JAR, поэтому Java 8 его не видит;
- OSGi-бандлом `com.github.sftwnd.oracle.oiuncomp`, который экспортирует пакет `com.github.sftwnd.oracle.oiuncomp`
  и требует среду исполнения `JavaSE 1.8`.

Экспортируется только пакет API; остальная реализация видна лишь внутри пакета.
