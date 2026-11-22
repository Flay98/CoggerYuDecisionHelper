#!/usr/bin/env python3
"""Validate saved tables, plot them and write a factual report. No optimization in Python."""
import argparse
import csv
import hashlib
import json
import math
import os
from collections import Counter, defaultdict
from pathlib import Path

os.environ.setdefault("MPLCONFIGDIR", "/tmp/mpr-comparison-matplotlib")
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

CONFIGS = ["COSINE", "GA_UPPER_ONLY", "GA_BIDIRECTIONAL"]
LABELS = {"COSINE": "Косинусы 2018", "GA_UPPER_ONLY": "GA UPPER_ONLY", "GA_BIDIRECTIONAL": "GA BIDIRECTIONAL"}
TYPES = {"DISCRETE_EXPERT": "A: случайные оценки", "PERTURBED_CONSISTENT": "B: возмущённые согласованные"}
COLORS = ["#28669c", "#b66219", "#267748"]
MARKERS = ["o", "s", "^"]
CAT_LABELS = {"BOTH_ACCEPTABLE": "Оба допустимы", "CI_ONLY_VIOLATION": "Нарушен CI",
              "K_ONLY_VIOLATION": "Нарушен K", "BOTH_VIOLATED": "Нарушены оба"}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def read_csv(path):
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def close(a, b):
    if not math.isclose(float(a), float(b), rel_tol=2e-12, abs_tol=2e-14):
        raise ValueError(f"Inconsistent saved statistic: {a} != {b}")


def validate(root, summary, plan, manifest, runs, inputs, aggregates, units):
    if manifest["status"] != "COMPLETE":
        raise ValueError("Incomplete profile must not be plotted")
    expected_inputs = len(plan["sizes"]) * 2 * plan["samplesPerType"]
    expected_runs = expected_inputs * (1 + 2 * len(plan["gaSeeds"]))
    assert len(inputs) == expected_inputs == manifest["expectedInputs"]
    assert len(runs) == expected_runs == manifest["completedRuns"] == manifest["expectedRuns"]
    assert len({r["runId"] for r in runs}) == len(runs)
    assert len(units) == expected_inputs * 3
    assert len(aggregates) == len(plan["sizes"]) * 2 * 3
    original = {i["caseId"]: i for i in inputs}
    grouped = defaultdict(list)
    for row in runs:
        assert row["phase"] == "MEASURED" and row["profile"] == plan["name"]
        assert row["planSha256"] == manifest["planSha256"]
        source = original[row["caseId"]]
        assert row["inputSha256"] == source["sha256"] == sha(root / source["inputFile"])
        assert row["type"] == source["type"] and row["size"] == source["size"]
        for metric in ("Ci", "K"):
            close(row["initial" + metric], source["initial" + metric])
            close(row["delta" + metric], float(row["initial" + metric]) - float(row["final" + metric]))
        ci_ok, k_ok = float(row["finalCi"]) <= .1, float(row["finalK"]) < .3
        assert (row["ciAcceptable"] == "true") == ci_ok
        assert (row["kAcceptable"] == "true") == k_ok
        assert (row["bothAcceptable"] == "true") == (ci_ok and k_ok)
        result = root / row["resultFile"]
        exported = read_json(result.with_name("run.json"))["run"]
        assert exported["runId"] == row["runId"] and exported["inputSha256"] == source["sha256"]
        for field in ("finalCi", "finalK", "elapsedMillis", "logDeviation"):
            close(row[field], exported[field])
        grouped[(row["caseId"], row["configuration"])].append(row)
    for source in inputs:
        for config in CONFIGS:
            samples = grouped[source["caseId"], config]
            expected = 1 if config == "COSINE" else len(plan["gaSeeds"])
            assert len(samples) == expected
            if config != "COSINE":
                assert {int(s["seed"]) for s in samples} == set(plan["gaSeeds"])
    # Recompute hierarchical means from the raw run table independently of Java.
    for unit in units:
        samples = grouped[unit["caseId"], unit["configuration"]]
        for field, raw in (("meanFinalCi", "finalCi"), ("meanFinalK", "finalK"),
                           ("meanElapsedMillis", "elapsedMillis"), ("meanLogDeviation", "logDeviation")):
            close(unit[field], np.mean([float(s[raw]) for s in samples]))
    for group in aggregates:
        members = [u for u in units if all(u[k] == group[k] for k in ("size", "type", "configuration"))]
        assert len(members) == int(group["nMatrices"]) == plan["samplesPerType"]
        for field in ("meanFinalCi", "meanFinalK", "meanElapsedMillis", "meanLogDeviation", "ownSuccessRate", "bothAcceptableRate"):
            close(group[field], np.mean([float(u[field]) for u in members]))
    json_runs = read_json(summary / "runs.json")["runs"]
    assert [r["runId"] for r in json_runs] == [r["runId"] for r in runs]
    # Check every CSV column against its typed JSON counterpart, including counters,
    # booleans, nulls and full-width integer seeds (never convert a seed to float).
    for name, key in [('runs', 'runs'), ('per_matrix', 'rows'), ('aggregated', 'rows'),
                      ('overall', 'rows'), ('by_initial_category', 'rows')]:
        csv_rows = read_csv(summary / (name + '.csv'))
        json_rows = read_json(summary / (name + '.json'))[key]
        assert len(csv_rows) == len(json_rows)
        for csv_row, json_row in zip(csv_rows, json_rows):
            assert set(csv_row) == set(json_row)
            for field, expected in json_row.items():
                actual = csv_row[field]
                if expected is None: assert actual == 'NA'
                elif isinstance(expected, bool): assert actual == str(expected).lower()
                elif isinstance(expected, int): assert int(actual) == expected
                elif isinstance(expected, float): close(actual, expected)
                else: assert actual == expected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", choices=["SMOKE", "PILOT", "REPORT", "FULL"], default="REPORT")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    root = args.root.resolve()
    summary = root / "summary" / args.profile
    plan = read_json(summary / "plan.json")
    manifest = read_json(summary / "manifest.json")
    runs, inputs = read_csv(summary / "runs.csv"), read_csv(summary / "inputs.csv")
    aggregates, units = read_csv(summary / "aggregated.csv"), read_csv(summary / "per_matrix.csv")
    validate(root, summary, plan, manifest, runs, inputs, aggregates, units)
    figures = root / "figures" / args.profile
    figures.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 10,
                         "axes.spines.top": False, "axes.spines.right": False, "svg.fonttype": "none"})
    produced, skipped = [], []

    def save(fig, name, sources, note=""):
        footer = f"Профиль {args.profile}; N={plan['samplesPerType']} матриц на тип и n; GA: {len(plan['gaSeeds'])} seed/матрицу"
        fig.text(.5, .015, footer + ("\n" + note if note else ""), ha="center", va="bottom", fontsize=8)
        fig.tight_layout(rect=(0, .10 if note else .07, 1, 1))
        for ext in ("png", "svg"):
            path = figures / f"{name}.{ext}"
            fig.savefig(path, dpi=300, bbox_inches="tight", metadata={"Creator": "MPR comparison"})
        plt.close(fig)
        produced.append({"name": name, "png": str((figures / (name + '.png')).relative_to(root)),
                         "svg": str((figures / (name + '.svg')).relative_to(root)), "sources": sources, "note": note})

    source = [f"summary/{args.profile}/aggregated.csv"]
    line_specs = [("01_final_ci", "meanFinalCi", "Средний итоговый CI", "CI", .1),
                  ("02_final_k", "meanFinalK", "Средний итоговый K", "K", .3),
                  ("04_time", "meanElapsedMillis", "Время математического алгоритма", "Время, мс", None),
                  ("05_changed", "meanChangedComparisons", "Изменённые независимые сравнения", "Число сравнений", None),
                  ("06_deviation", "meanLogDeviation", "Отклонение экспертных оценок", "Логарифмическое отклонение D", None)]
    for matrix_type, title in TYPES.items():
        letter = "A" if matrix_type == "DISCRETE_EXPERT" else "B"
        for name, field, heading, ylabel, threshold in line_specs:
            fig, ax = plt.subplots(figsize=(7.2, 4.7))
            for config, color, marker in zip(CONFIGS, COLORS, MARKERS):
                data = sorted((r for r in aggregates if r["type"] == matrix_type and r["configuration"] == config), key=lambda r: int(r["size"]))
                ax.plot([int(r["size"]) for r in data], [float(r[field]) for r in data], marker=marker, markerfacecolor="none", color=color, label=LABELS[config])
            if threshold is not None:
                ax.axhline(threshold, color="gray", ls="--", lw=1, label=f"Порог {threshold:g}")
            if name == "04_time":
                ax.set_yscale("log")
            ax.set(title=f"{heading}\n{title}", xlabel="Размерность n", ylabel=ylabel, xticks=plan["sizes"])
            ax.grid(alpha=.2); ax.legend(fontsize=8)
            save(fig, name + "_" + letter, source, "Среднее по матрицам после усреднения seed внутри матрицы")

        fig, axes = plt.subplots(1, 2, figsize=(10, 4.6), sharey=True)
        for ax, field, heading in zip(axes, ["ownSuccessRate", "bothAcceptableRate"], ["Собственный критерий метода", "Оба порога одновременно"]):
            for config, color, marker in zip(CONFIGS, COLORS, MARKERS):
                data = sorted((r for r in aggregates if r["type"] == matrix_type and r["configuration"] == config), key=lambda r: int(r["size"]))
                ax.plot([int(r["size"]) for r in data], [float(r[field]) for r in data], marker=marker, markerfacecolor="none", color=color, label=LABELS[config])
            ax.set(title=heading, xlabel="Размерность n", ylim=(-.04, 1.06), xticks=plan["sizes"])
            ax.grid(alpha=.2)
        axes[0].set_ylabel("Доля успешных завершений")
        axes[1].legend(fontsize=8)
        fig.suptitle(title)
        save(fig, "03_success_" + letter, source, "ALREADY_ACCEPTABLE включён в собственный успех")

        if plan["samplesPerType"] >= 3:
            fig, ax = plt.subplots(figsize=(8, 4.8))
            for offset, config, color in [(-.17, CONFIGS[1], COLORS[1]), (.17, CONFIGS[2], COLORS[2])]:
                for n in plan["sizes"]:
                    values = [float(u["meanFinalK"]) for u in units if u["type"] == matrix_type and u["configuration"] == config and int(u["size"]) == n]
                    box = ax.boxplot([values], positions=[n + offset], widths=.28, patch_artist=True, manage_ticks=False,
                                     medianprops={"color": "black"}, showfliers=False)
                    box["boxes"][0].set(facecolor=color, alpha=.35)
                    ax.scatter(np.linspace(n + offset - .07, n + offset + .07, len(values)), values, s=15, color=color, zorder=3)
                ax.plot([], [], color=color, lw=5, label=LABELS[config])
            ax.axhline(.3, color="gray", ls="--", lw=1)
            ax.set(title=f"Распределение итогового K\n{title}", xlabel="Размерность n", ylabel="Средний по seed K отдельной матрицы", xticks=plan["sizes"])
            ax.grid(alpha=.2); ax.legend(fontsize=8)
            save(fig, "08_k_distribution_" + letter, [f"summary/{args.profile}/per_matrix.csv"], "Точки — независимые матрицы; повторы seed не считаются новыми матрицами")
        else:
            skipped.append({"name": "08_k_distribution_" + letter, "reason": "Менее трёх независимых матриц в группе; boxplot не строится"})

    # This trace was fixed in the plan, never chosen by observed optimization quality.
    trace_id = f"A_n{plan['traceSize']:02d}_m000"
    fig, ax = plt.subplots(figsize=(7.2, 4.7))
    history_sources = []
    for config, color in zip(CONFIGS[1:], COLORS[1:]):
        row = next(r for r in runs if r["caseId"] == trace_id and r["configuration"] == config and r["seed"] == "42")
        result = read_json(root / row["resultFile"])
        history = result["results"]["genetic"]["history"]
        # Actual exported schema names are checked, not inferred from the graph shape.
        ax.plot([h["generation"] for h in history], [h["bestK"] for h in history], marker=".", color=color, label=LABELS[config])
        history_sources.append(row["resultFile"])
    ax.axhline(.3, color="gray", ls="--", lw=1, label="Порог K < 0.3")
    ax.set(title=f"Динамика GA: {trace_id}, seed=42", xlabel="Поколение (0 — начальная популяция)", ylabel="Лучший K")
    ax.grid(alpha=.2); ax.legend(fontsize=8)
    save(fig, "07_ga_history", history_sources, "Матрица и seed заданы до выполнения эксперимента")

    fig, axes = plt.subplots(1, 2, figsize=(10, 4.7))
    for matrix_type, title in TYPES.items():
        rows = [r for r in inputs if r["type"] == matrix_type]
        for ax, field, label in zip(axes, ["initialCi", "initialK"], ["Исходный CI", "Исходный K"]):
            ax.scatter([int(r["size"]) + (-.07 if matrix_type == "DISCRETE_EXPERT" else .07) for r in rows],
                       [float(r[field]) for r in rows], label=title, alpha=.65, s=22)
            ax.set(xlabel="Размерность n", ylabel=label, xticks=plan["sizes"])
            ax.grid(alpha=.2)
    axes[0].axhline(.1, color="gray", ls="--", lw=1); axes[1].axhline(.3, color="gray", ls="--", lw=1)
    axes[1].legend(fontsize=8)
    fig.suptitle("Распределение исходной несогласованности")
    save(fig, "09_initial_distribution", [f"summary/{args.profile}/inputs.csv"], "Каждая точка — одна исходная матрица")

    figure_manifest = {"schemaVersion": 1, "profile": args.profile, "planSha256": manifest["planSha256"],
                       "sourceSha256": {str(p.relative_to(root)): sha(p) for p in [summary / f for f in ("runs.csv", "inputs.csv", "aggregated.csv", "per_matrix.csv")]},
                       "matplotlibVersion": matplotlib.__version__, "numpyVersion": np.__version__,
                       "figures": produced, "skipped": skipped}
    (figures / "manifest.json").write_text(json.dumps(figure_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_paired(summary, inputs, units)
    write_report(root, summary, plan, manifest, runs, inputs, aggregates, produced, skipped)
    print(f"{args.profile}: validated {len(inputs)} inputs / {len(runs)} runs; {len(produced)} figures (PNG + SVG)")


def write_paired(summary, inputs, units):
    indexed = {(u['caseId'], u['configuration']): u for u in units}
    paired = []
    for source in inputs:
        upper, bi = [indexed[source['caseId'], c] for c in CONFIGS[1:]]
        row = {k: source[k] for k in ('caseId', 'type', 'size', 'sha256')}
        for field in ('meanFinalCi', 'meanFinalK', 'meanChangedComparisons', 'meanLogDeviation', 'meanElapsedMillis', 'ownSuccessRate', 'bothAcceptableRate'):
            row[field + '_BminusU'] = float(bi[field]) - float(upper[field])
        paired.append(row)
    with (summary / 'paired_modes.csv').open('w', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=list(paired[0])); writer.writeheader(); writer.writerows(paired)
    (summary / 'paired_modes.json').write_text(json.dumps({'schemaVersion': 1, 'rows': paired}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def write_report(root, summary, plan, manifest, runs, inputs, groups, figures, skipped):
    overall = read_csv(summary / "overall.csv")
    category_counts = Counter(i["initialCategory"] for i in inputs)
    lines = [f"# Сравнение методов: фактически выполненный профиль {plan['name']}", "",
             "## Цель", "", "Независимо сравнить косинусную коррекцию 2018 года и два режима количественного GA на одних исходных МПС. Это собственное исследование, а не воспроизведение авторской экспериментальной выборки.", "",
             "## Методика", "",
             f"Размерности: {plan['sizes']}; {plan['samplesPerType']} независимых матриц каждого типа на размерность. Всего **{len(inputs)} исходных матриц и {len(runs)} измеряемых запусков**. Seed GA: {plan['gaSeeds']}; master seed={plan['masterSeed']}.", "",
             "Тип A: независимый равномерный выбор из 17 значений {1/9,…,1/2,1,2,…,9}. Тип B: wᵢ=exp(U[0,ln3)), aᵢⱼ=wᵢ/wⱼ; случайное подмножество ceil(0.4m) независимых пар возмущается множителем exp(U[−ln3,ln3)). Выход за [1/9,9] отклоняется с повтором до 64 попыток; исчерпание — ошибка задания. Округления и отбора по исходному CI/K нет. Веса и каждое возмущение сохранены в метаданных входов.", "",
             f"Косинусы: CI≤0.1, максимум {plan['maxIterations']} исправлений. GA: K<0.3, P={plan['population']}, максимум {plan['maxGenerations']} поколений, 64 попытки мутации. У всех диапазон [1/9,9]. Целевые функции и операторы не менялись.", "",
             "Экспериментальная единица — исходная матрица. Сначала усредняются GA-seed внутри неё, затем матричные средние имеют одинаковый вес. Групповые медианы и выборочные SD вычислены по матричным средним; в per_matrix медианы/SD относятся к seed. Доли — средние доли seed внутри матриц; дополнительно сохранены числа матриц с успехом всех/хотя бы одного seed. Неудачные статусы включены. Это описательная статистика без проверки значимости.", "",
             "Собственный успех: CI≤0.1 для косинусов и K<0.3 для GA; ALREADY_ACCEPTABLE включён. Общая диагностика: одновременно CI≤0.1 и K<0.3. Статусы не отождествляются. D — RMS логарифмов отношений верхних элементов; он не является целевой функцией. Относительное изменение при исходном показателе ≤10⁻¹² обозначается NA.", "",
             f"Выполнено {manifest['warmupRuns']} отдельных разогревочных запусков в той же JVM/потоке; они исключены из статистики. Порядок конфигураций и seed циклически чередуется по номеру входа. Время алгоритма измеряется без чтения/экспорта/графиков. Полное время Java-профиля: **{manifest['wallSeconds']:.3f} с**; среда: `{manifest['environment']}`. Прогрев ограниченный, не является JMH; время зависит от JIT, GC и оборудования.", "",
             "Каждый вход создаётся однократно, имеет SHA-256, все методы получают его независимо. При повторе проверяются спецификация и хеш. Каждая сохранённая итоговая CSV повторно загружена через модель; CI/K, число изменений и история проверены. Лимиты времени/диска приводят к явной ошибке профиля, а не исключению неудобных результатов.", "",
             "## Результаты", "",
             "| Конфигурация | Матриц / запусков | Успех свой | CI допустим | K допустим | Оба | Средний CI | Средний K | Изменений | D | Время, мс |",
             "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for r in overall:
        p = lambda field: f"{100 * float(r[field]):.1f}%"
        lines.append(f"| {LABELS[r['configuration']]} | {r['nMatrices']} / {r['nRuns']} | {p('ownSuccessRate')} | {p('ciAcceptableRate')} | {p('kAcceptableRate')} | {p('bothAcceptableRate')} | {float(r['meanFinalCi']):.6g} | {float(r['meanFinalK']):.6g} | {float(r['meanChangedComparisons']):.3f} | {float(r['meanLogDeviation']):.4f} | {float(r['meanElapsedMillis']):.3f} |")
    lines += ["", "Начальные категории (число независимых входов): " + "; ".join(f"{CAT_LABELS[c]} — {category_counts[c]}" for c in CAT_LABELS) + ".", "",
              "Полные таблицы: [входы](inputs.csv), [каждый запуск](runs.csv), [сводка seed внутри матрицы](per_matrix.csv), [размерность × тип × метод](aggregated.csv), [разбиение по начальной категории](by_initial_category.csv), [общая сводка](overall.csv). CSV имеют соответствующие JSON. SD и знаменатели относительных показателей находятся в агрегированных файлах.", "",
              "| Тип | n | Метод | N | Запусков | CI итог | K итог | ΔCI | ΔK | Свой успех | Оба | D | мс |",
              "|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for r in groups:
        lines.append(f"| {'A' if r['type']=='DISCRETE_EXPERT' else 'B'} | {r['size']} | {LABELS[r['configuration']]} | {r['nMatrices']} | {r['nRuns']} | {float(r['meanFinalCi']):.5f} | {float(r['meanFinalK']):.5f} | {float(r['meanDeltaCi']):.5f} | {float(r['meanDeltaK']):.5f} | {100*float(r['ownSuccessRate']):.1f}% | {100*float(r['bothAcceptableRate']):.1f}% | {float(r['meanLogDeviation']):.3f} | {float(r['meanElapsedMillis']):.2f} |")
    lines += ["", "## Обсуждение", ""]
    for r in overall:
        subset = [s for s in runs if s['configuration'] == r['configuration']]
        bad_ci = sum(float(s['deltaCi']) < -1e-12 for s in subset)
        bad_k = sum(float(s['deltaK']) < -1e-12 for s in subset)
        lines += [f"**{LABELS[r['configuration']]}:** среднее снижение CI={float(r['meanDeltaCi']):.6g}, K={float(r['meanDeltaK']):.6g}; уже приемлемые — {100*float(r['alreadyAcceptableRate']):.1f}%, без итоговых изменений — {100*float(r['noChangesRate']):.1f}%. Ухудшение CI наблюдалось в {bad_ci}/{len(subset)} запусков, K — в {bad_k}/{len(subset)} (включая повторы seed). Эти случаи сохранены, не исключены.", ""]
    for t, title in TYPES.items():
        type_inputs = [i for i in inputs if i['type']==t]
        type_categories = Counter(i['initialCategory'] for i in type_inputs)
        lines += [f"**{title}: состав {len(type_inputs)} входов** — " + '; '.join(f"{CAT_LABELS[c]}: {type_categories[c]}" for c in CAT_LABELS) + ".", "",
                  "| Метод | Свой успех | Оба порога | Средний K | Среднее D | Время, мс |",
                  "|---|---:|---:|---:|---:|---:|"]
        for config in CONFIGS:
            typed = [r for r in groups if r['type']==t and r['configuration']==config]
            avg = lambda field: np.mean([float(r[field]) for r in typed])
            lines.append(f"| {LABELS[config]} | {100*avg('ownSuccessRate'):.1f}% | {100*avg('bothAcceptableRate'):.1f}% | {avg('meanFinalK'):.5f} | {avg('meanLogDeviation'):.4f} | {avg('meanElapsedMillis'):.3f} |")
        lines.append("")
        lines += [f"**{title}.** Изменение групповых средних от минимального к максимальному n:", ""]
        for config in CONFIGS:
            data = sorted((r for r in groups if r['type']==t and r['configuration']==config), key=lambda r:int(r['size']))
            first, last = data[0], data[-1]
            lines += [f"- {LABELS[config]}: n={first['size']}→{last['size']}, K={float(first['meanFinalK']):.4f}→{float(last['meanFinalK']):.4f}, CI={float(first['meanFinalCi']):.4f}→{float(last['meanFinalCi']):.4f}, изменений={float(first['meanChangedComparisons']):.2f}→{float(last['meanChangedComparisons']):.2f}, D={float(first['meanLogDeviation']):.3f}→{float(last['meanLogDeviation']):.3f}, время={float(first['meanElapsedMillis']):.2f}→{float(last['meanElapsedMillis']):.2f} мс."]
        lines.append("")
    lines += ["Парные различия GA: [по каждой исходной матрице](paired_modes.csv). Знак B−U: отрицательное значение CI/K/D означает меньшее значение у BIDIRECTIONAL. Средние ниже вычислены по независимым матрицам, а не по числу seed.", "",
              "| Тип | Матриц | ΔK (B−U) | ΔCI (B−U) | ΔD (B−U) | Δуспеха, п.п. | Меньший K: B / U / ничья |",
              "|---|---:|---:|---:|---:|---:|---:|"]
    for t, title in TYPES.items():
        paired = [r for r in read_csv(summary / 'paired_modes.csv') if r['type']==t]
        diff = lambda field: np.mean([float(r[field+'_BminusU']) for r in paired])
        wins_b = sum(float(r['meanFinalK_BminusU']) < -1e-12 for r in paired)
        wins_u = sum(float(r['meanFinalK_BminusU']) > 1e-12 for r in paired)
        lines.append(f"| {title} | {len(paired)} | {diff('meanFinalK'):.5f} | {diff('meanFinalCi'):.5f} | {diff('meanLogDeviation'):.5f} | {100*diff('ownSuccessRate'):.2f} | {wins_b} / {wins_u} / {len(paired)-wins_b-wins_u} |")
    lines += ["", "Статусы фактически выполненных запусков (включая все лимиты и отсутствие изменений):", "",
              "| Метод | Статусы и количества |", "|---|---|"]
    for config in CONFIGS:
        counts = Counter(r['status'] for r in runs if r['configuration']==config)
        lines.append(f"| {LABELS[config]} | " + '; '.join(f"{k}: {v}" for k,v in sorted(counts.items())) + " |")
    lines.append("")
    lines += ["Для парного анализа режимов GA используются одинаковые входы и seed. Совпадение seed не означает совпадение последовательности случайных операций: BIDIRECTIONAL дополнительно выбирает ориентацию, а прибавление δ к обратному значению даёт нелинейное изменение хранимого верхнего элемента. Таблицы показывают наблюдаемые различия, но не устанавливают их единственную причинную связь с оператором.", "",
              "Косинусный метод прекращает коррекцию при допустимом CI даже при высоком K; GA прекращает поиск по K. Различия общего критерия и собственного успеха следует читать совместно с начальными категориями. Отсутствие изменений при уже выполненном собственном пороге — ожидаемое поведение. Для типа B латентная структура близка к согласованной, но её восстановление не входит в целевые функции; D измеряет отклонение от поданной возмущённой матрицы, а не от латентной.", "",
              "## Графики", ""]
    for f in figures:
        lines.append(f"- [{f['name']} (PNG)](../../{f['png']}) · [SVG](../../{f['svg']}); источник: {', '.join(f['sources'])}.")
    for f in skipped:
        lines.append(f"- Не построен {f['name']}: {f['reason']}.")
    lines += ["", "## Ограничения", "",
              f"На каждую группу приходится только {plan['samplesPerType']} независимых матриц; {len(plan['gaSeeds'])} GA seed характеризуют стохастический разброс внутри матрицы. Увеличение числа повторов не увеличивает число независимых входов. Типы генерации и шкала ограничивают переносимость выводов. Наборы разных размерностей независимы, поэтому линии не являются траекториями одной матрицы. Стандартные отклонения описательные, доверительные интервалы и статистическая значимость не заявляются.", "",
              "Программные уточнения исходных статей сохраняются: детерминированные правила выбора коррекции/разрешения конфликтов, проверки допустимого улучшения CI и шкалы у косинусного метода; оператор скрещивания, округление размеров групп, отбрасывание недопустимых мутаций и управляемый RNG у GA. См. независимое описание этапа 5: [воспроизведение](../../../reproduction/README.md). Здесь алгоритмы не изменялись. Сравнение не доказывает полной эквивалентности авторским реализациям.", "",
              "## Выводы", "",
              "Достигнутые собственные пороги и одновременная приемлемость CI/K различаются; единый рейтинг методов только по TARGET_REACHED некорректен. Изменение оценок и время необходимо оценивать вместе с согласованностью. Численные различия относятся к данному фиксированному плану, типам матриц и среде. Универсальное превосходство какого-либо метода не утверждается.", ""]
    if plan['name']=='REPORT':
        indexed = {r['configuration']: r for r in overall}
        c,u,b = [indexed[k] for k in CONFIGS]
        lines += [f"В выполненном REPORT косинусы достигли собственного порога в {100*float(c['ownSuccessRate']):.1f}% случаев, но обоих порогов — в {100*float(c['bothAcceptableRate']):.1f}%. У GA UPPER_ONLY одновременно допустимы CI/K в {100*float(u['bothAcceptableRate']):.1f}%, у BIDIRECTIONAL — в {100*float(b['bothAcceptableRate']):.1f}%. Средний K составил соответственно {float(c['meanFinalK']):.4f}, {float(u['meanFinalK']):.4f}, {float(b['meanFinalK']):.4f}. Эти доли включают уже приемлемые входы и все неуспешные статусы.", "",
                  f"Оба режима GA в среднем изменяли больше сравнений ({float(u['meanChangedComparisons']):.2f}/{float(b['meanChangedComparisons']):.2f}), чем косинусы ({float(c['meanChangedComparisons']):.2f}); их общее среднее D также составило {float(u['meanLogDeviation']):.3f}/{float(b['meanLogDeviation']):.3f} против {float(c['meanLogDeviation']):.3f}. Этот общий результат зависит от состава типов: отдельные таблицы A/B необходимы для интерпретации. В этой выборке BIDIRECTIONAL имеет меньший средний итоговый K, чем UPPER_ONLY; величина различия зависит от типа и размерности, что отражено в парной таблице. Крупные случайные матрицы типа A остаются наиболее трудными по критерию K при заданном бюджете. Более низкий индекс не означает автоматического сохранения экспертных оценок.", ""]
    lines += [
              "## Воспроизведение", "", f"`./gradlew benchmark{plan['name'].title()}`; только графики: `experiments/comparison/.venv/bin/python experiments/comparison/scripts/plot_results.py --profile {plan['name']}`. Планы, среда, расписание и хеши находятся рядом с таблицами. FULL не считается выполненным без собственного COMPLETE manifest.", ""]
    text = "\n".join(lines)
    (summary / "report.md").write_text(text, encoding="utf-8")
    if plan["name"] == "REPORT":
        # The requested stable entry point, with links adjusted by one directory level.
        text = text.replace("](inputs.csv)", "](REPORT/inputs.csv)")
        for name in ("runs", "per_matrix", "aggregated", "by_initial_category", "overall", "paired_modes"):
            text = text.replace(f"]({name}.csv)", f"](REPORT/{name}.csv)")
        text = text.replace("](../../figures/", "](../figures/").replace("](../../../reproduction/", "](../../reproduction/")
        (root / "summary/report.md").write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
