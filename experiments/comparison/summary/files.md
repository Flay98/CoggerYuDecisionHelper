# Созданные и изменённые файлы этапа 6

## Изменены

- `.gitignore`: исключены локальная Python-среда и кэш Python.
- `README.md`: добавлен запуск собственного исследования.
- `build.gradle.kts`: отдельные задачи профилей, построения графиков и аудита.

## Созданы: программный код и тесты

- [src/MCD/consistency/experiments/BenchmarkEnvironment.java](../../../src/MCD/consistency/experiments/BenchmarkEnvironment.java)
- [src/MCD/consistency/experiments/BenchmarkProfile.java](../../../src/MCD/consistency/experiments/BenchmarkProfile.java)
- [src/MCD/consistency/experiments/BenchmarkRunner.java](../../../src/MCD/consistency/experiments/BenchmarkRunner.java)
- [src/MCD/consistency/experiments/BenchmarkStatistics.java](../../../src/MCD/consistency/experiments/BenchmarkStatistics.java)
- [src/MCD/consistency/experiments/BenchmarkVerifier.java](../../../src/MCD/consistency/experiments/BenchmarkVerifier.java)
- [src/MCD/consistency/experiments/ExperimentFiles.java](../../../src/MCD/consistency/experiments/ExperimentFiles.java)
- [src/MCD/consistency/experiments/ExperimentPlan.java](../../../src/MCD/consistency/experiments/ExperimentPlan.java)
- [src/MCD/consistency/experiments/InputRepository.java](../../../src/MCD/consistency/experiments/InputRepository.java)
- [src/MCD/consistency/experiments/MatrixGenerator.java](../../../src/MCD/consistency/experiments/MatrixGenerator.java)
- [src/MCD/consistency/experiments/ResearchMetrics.java](../../../src/MCD/consistency/experiments/ResearchMetrics.java)
- [src/MCD/consistency/experiments/RunRecord.java](../../../src/MCD/consistency/experiments/RunRecord.java)
- [test/MCD/consistency/experiments/BenchmarkInfrastructureTest.java](../../../test/MCD/consistency/experiments/BenchmarkInfrastructureTest.java)
- [test/MCD/consistency/experiments/MatrixGeneratorTest.java](../../../test/MCD/consistency/experiments/MatrixGeneratorTest.java)
- [test/MCD/consistency/experiments/ResearchMetricsTest.java](../../../test/MCD/consistency/experiments/ResearchMetricsTest.java)

## Созданы: планы, документация, скрипты и результаты

- `experiments/comparison/README.md`: полная методика, схема и инструкции.
- `config/SMOKE.json`, `PILOT.json`, `REPORT.initial.json`, `REPORT.json`, `REPORT.decision.json`, `FULL.json`.
- `scripts/plot_results.py`, `requirements.txt`, `requirements-lock.txt`.
- `inputs/<PROFILE>`: CSV и метаданные всех входов, включая отдельный прогрев.
- `outputs/<PROFILE>/<planSha256-prefix>/<runId>`: исходные/итоговые CSV, result.json и run.json.
- `summary/<PROFILE>`: планы, среда, расписания, исходные показатели, каждый запуск, сводки, прогрев, manifest.
- `summary/report.md`: основной аналитический отчёт REPORT.
- `figures/REPORT`: 16 PNG, 16 SVG, manifest источников; `figures/SMOKE`: 14 PNG, 14 SVG и manifest.
- `summary/verification.json`: фактические результаты проверок.

Полный пофайловый перечень с размером и SHA-256: [artifacts.csv](artifacts.csv). Он включает код, тесты, новые артефакты и три изменённых файла; собственный файл индекса исключён из хеширования. Локальная .venv и кэши в него не входят.

Архив этапа 5 оставлен побайтно неизменным после успешного повторного запуска: все математические результаты совпали, обновления времени не заменяли сохранённый архив. Предоставленные пользователем PDF не изменялись. Коммит не создавался.
