# Демонстрация за 3–5 минут

Команды выполняются из корня проекта на JDK 17. Для основной демонстрации
нужны только Java и Gradle Wrapper; JAR зависимостей уже находятся в `lib`.
При первом запуске Wrapper загружает Gradle, время зависит от сети; сделайте
это до защиты. IntelliJ, Python и изменение исходников для этой части не нужны.

## 1. Сборка и сохранение исходной программы (около 30 с)

```bash
java -version
./gradlew clean build
printf '1\n' | ./gradlew run --console=plain
```

Показать успешные 208 тестов в `build/reports/tests/test/index.html` и
оригинальное меню. Режимы Simple/CoggerYu сохранены; новый сценарий изолирован.

## 2. Анализ противоречивой МПС (около 40 с)

```bash
./gradlew runConsistency --args="--input examples/decision/criteria_3x3.csv --method both --seed 42 --min-comparison 0.1111111111111111 --max-comparison 9 --output-dir build/results/consistency"
```

Объяснить: исходные CI≈0.183721, K≈0.833333. Косинусы ориентированы на CI≤0.1,
GA — на K<0.3, критерии различаются. Оба метода начинают с исходной матрицы.
CSV/JSON результатов можно открыть непосредственно из указанного каталога.

## 3. Влияние на решение (около 90 с)

```bash
./gradlew runDecision --args="--matrix examples/decision/criteria_3x3.csv --alternatives examples/decision/alternatives_3x3.csv --directions max,min,max --seed 42 --min-comparison 0.1111111111111111 --max-comparison 9 --output-dir build/results/decision"
```

Показать данные `examples/decision/alternatives_3x3.csv`: скорость/точность MAX,
стоимость MIN. Строки/столбцы МПС идут в этом же порядке.

- Исходные веса ≈`(0.463816,0.280936,0.255248)`, лидер C.
- Косинусы исправляют a13 с 1 на 6; веса `(2/3,2/9,1/9)`, лидер A.
- GA UPPER_ONLY, seed 42: лидер A, 3 поколения.
- GA BIDIRECTIONAL, тот же seed: лидер C, 1 поколение.

Рейтинг определяется вычисленными баллами, победители не заданы в коде.
Показать `criterion_weights.csv`, `alternative_scores.csv`,
`ranking_comparison.csv` и `result.json` в `build/results/decision`.
У A после косинусов место меняется 3→1: Δr=−2. У BIDIRECTIONAL баллы меняются,
но места сохраняются. Это нормальное различие между баллом и позицией.

Обязательно пояснить: новые веса — главный **правый** собственный вектор
полной МПС, затем нормализация столбцов и взвешенная сумма. Это отдельная
интеграционная процедура, не буквальная реализация оригинального Cogger–Yu.
Снижение несогласованности не гарантирует объективной правильности выбора.

## 4. Научная и экспериментальная проверка (около 40–60 с)

Воспроизведение в отдельном каталоге, без перезаписи архива этапа 5:

```bash
mkdir -p build/demo/reproduction
cp -r experiments/reproduction/inputs experiments/reproduction/reference build/demo/reproduction/
./gradlew reproduceArticles --args="build/demo/reproduction"
```

Показать `build/demo/reproduction/summary/report.md`: опубликованные эталоны
отделены от нашей реализации. Таблица 2018 года не воспроизводится буквально
из-за отсутствия исходной выборки; это явно отражено в документации.

Открыть уже сохранённые [таблицы/выводы REPORT](../experiments/comparison/summary/report.md)
и [графики REPORT](../experiments/comparison/figures/REPORT/manifest.json):
64 входа размерности 3–10, 448 запусков. В защите повторять REPORT не нужно.

## Дополнительная быстрая проверка экспериментов (по времени)

Java-профиль SMOKE можно запустить изолированно без Python:

```bash
mkdir -p build/demo/comparison/config
cp experiments/comparison/config/SMOKE.json build/demo/comparison/config/
./gradlew runBenchmarkSmoke --args="SMOKE build/demo/comparison"
```

Для проверки графиков используйте окружение по инструкции
`experiments/comparison/README.md`, затем:

```bash
experiments/comparison/.venv/bin/python experiments/comparison/scripts/plot_results.py --profile SMOKE --root build/demo/comparison
```

Либо `./gradlew benchmarkSmoke` для обычного каталога экспериментов.
Полный основной профиль требуется только для регенерации промежуточных данных:

```bash
./gradlew benchmarkReport
./gradlew verifyComparison
```

После свежего клонирования `outputs/REPORT` может отсутствовать, поскольку
промежуточные результаты исключены из Git. В таком случае не запускайте
`verifyComparison` до `benchmarkReport`. Отсутствующий архив выходов не означает
ошибку математического алгоритма. Ни REPORT, ни графики не входят в обычный build.
