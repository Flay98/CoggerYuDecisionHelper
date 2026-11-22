# About

The repository presents calculation of optimal alternatives in multicriteria task via Cogger and Yu method [1].

The project is completed during the Descision Making course at SPbPU Institute of Computer Science and Cybersecurity (SPbPU ICSC).

# Persons
The main contributor of the project is [Artem A. Lazarenko](https://github.com/Th3Spac3), a student of SPbPU ICSC.

The advisor and minor contributor is [Vladimir A. Parkhomenko](https://github.com/ParkhomenkoV) a seniour lecturer of SPbPU ICSC.


# How to install?
См. инструкции по сборке и запуску ниже. Подготовка Gradle-сборки для лабораторной
работы выполнена 22.11.2026; исходные алгоритмы и интерфейс сохранены.

# How to use?
You can import your table with multiple criteria and alternatives, then you answer to questions about criteria dominance. Based on your table and answers program will estimate all alternatives.

# Used packages
[Jama](https://math.nist.gov/javanumerics/jama/#Package) package is used to calculate matrices.


# Warranty
The contributors give no warranty for the using of the software. The authors kindly asks 


[1] Cogger K. O., Yu P. L. Eigenweight vectors and least-distance approximation for revealed preference in pairwise weight ratios //Journal of optimization theory and applications. – 1985. – Vol. 46. – P. 483-491.

## Сборка и запуск на Ubuntu

Проект основан на [CoggerYuDecisionHelper Артёма Лазаренко](https://github.com/Th3Spac3/CoggerYuDecisionHelper).
Информация об авторах сохранена выше, лицензия — [GPL-3.0](LICENSE).

Требуется **JDK 17**, включающий `java` и `javac`. Например, на Ubuntu:

```bash
sudo apt install openjdk-17-jdk
java -version
javac -version
```

Если установлено несколько JDK, укажите JDK 17 через `JAVA_HOME` и добавьте
`$JAVA_HOME/bin` в `PATH`. IntelliJ IDEA и отдельная установка Gradle не нужны.
Gradle Wrapper фиксирует Gradle 8.14 и проверяет SHA-256 дистрибутива. При первом
запуске нужен доступ к интернету для его загрузки; библиотеки приложения и тестов
уже находятся в репозитории. После загрузки Gradle сборка доступна с `--offline`.

Выполняйте команды из корня проекта:

```bash
./gradlew clean build
./gradlew test
./gradlew run --console=plain
```

`build` компилирует проект, запускает существующие тесты и создаёт JAR и архивы
приложения в `build/`. HTML-отчёт тестов: `build/reports/tests/test/index.html`.
Если тесты уже выполнены и не изменялись, `test` может показать `UP-TO-DATE`;
для принудительного повторения используйте `./gradlew test --rerun-tasks`.

Задача `run` передаёт стандартный ввод консольному приложению. В главном меню
введите `1`, чтобы выйти. Для короткой проверки запуска:

```bash
printf '1\n' | ./gradlew run --console=plain
```

Для расчёта выберите `3`, укажите путь к CSV/TXT, затем ответьте на вопросы.
Формат исходной программы: первая строка — названия критериев через запятую,
каждая последующая — числовые значения одной альтернативы; десятичный разделитель —
точка. Это таблица альтернатив и критериев, а не импорт произвольной МПС.

## Структура и зависимости

Сохранены исходные каталоги, подключённые в Gradle через `sourceSets`:

```text
src/MCD/                  исходная логика и консольный интерфейс
src/MCD/Main.java         точка входа MCD.Main
src/Jama/                 включённые исходники библиотеки JAMA
test/MCD/RankerTest.java  пять существующих тестов JUnit 4
lib/                      JUnit 4.13.1 и Hamcrest Core 1.3 (для тестов)
gradle/wrapper/            фиксированная версия Gradle и Wrapper JAR
build.gradle.kts          сборка, JDK 17, sourceSets и стандартный ввод
settings.gradle.kts       имя проекта
build/                    генерируемые результаты; не добавляются в Git
```

Новые алгоритмы в дальнейшем предполагается размещать в отдельном пакете
`MCD.consistency` (`src/MCD/consistency/`), а тесты — в
`test/MCD/consistency/`. На подготовительном этапе эти алгоритмы не реализованы.
Новых библиотек не добавлено: JAMA компилируется из имеющихся исходников,
JUnit и Hamcrest подключены из `lib/`, без загрузки из Maven Central.

## Ограничения исходного проекта

На данном этапе они сохранены без исправлений:

- `Ranker.EigenValues()` вычисляет суммы строк, а не собственные значения;
  существующие сравнения критериев не являются вводом произвольной полной МПС.
- `CSVReader` не обрабатывает корректно отсутствующие и пустые файлы, не проверяет
  одинаковую длину строк и не поддерживает общий CSV-формат с кавычками.
  Консольный ввод не защищён от нечисловых значений или конца потока.
- При переключении метода в настройках отсутствует `break`, поэтому дополнительно
  выводится сообщение о неверных аргументах.
- В `RankerTest.EigenTest()` отсутствует итоговое утверждение. В `ProductionTest()`
  и `WeightTest()` флаг результата может скрыть отдельные ошибки;
  `HorizontalNorm()` случайно выбирает одну из двух ветвей нормализации.
  Успех пяти тестов не означает полной проверки математической корректности.
  Gradle явно включает Java assertions, используемые существующими тестами.
- В исходном репозитории уже отслеживаются файлы `.class` и каталог `out/`.
  Они сохранены, но Gradle компилирует `.java` заново в `build/` и не использует
  старые скомпилированные файлы. `clean` очищает только результаты Gradle.
