#!/usr/bin/env python3
"""Plot archived Java statistics and write the approximate-reproduction report. No correction in Python."""
import argparse
import csv
import hashlib
import json
import math
import os
from collections import Counter
from pathlib import Path

os.environ.setdefault('MPLCONFIGDIR', '/tmp/mpr-kurennykh-matplotlib')
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def read_csv(path):
    with path.open(encoding='utf-8', newline='') as handle:
        return list(csv.DictReader(handle))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', choices=['REPORT', 'SMOKE'], default='REPORT')
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    root = args.root.resolve(); summary = root / 'summary' / args.profile
    manifest = read_json(summary / 'manifest.json'); plan = read_json(summary / 'plan.json')
    if manifest['status'] != 'COMPLETE':
        raise ValueError('Incomplete profile is not valid input for plots/report')
    rows = read_csv(summary / 'runs.csv'); aggregates = read_csv(summary / 'aggregated.csv')
    groups = read_csv(summary / 'groups.csv'); comparison = read_csv(summary / 'comparison_with_article.csv')
    assert len(rows) == manifest['expectedMatrices'] == manifest['completedMatrices'] == len(plan['sizes']) * plan['matricesPerSize']
    assert len({r['id'] for r in rows}) == len(rows)
    assert all(r['profile'] == args.profile and r['planSha256'] == manifest['planSha256'] for r in rows)
    assert sha(root / manifest['inputArchive']) == manifest['inputArchiveSha256']
    assert sha(root / manifest['outputArchive']) == manifest['outputArchiveSha256']
    all_groups = {int(r['size']): r for r in groups if r['group'] == 'ALL'}
    indexed = {(int(r['size']), r['group'], r['metric']): r for r in aggregates}
    for n in plan['sizes']:
        cases = [r for r in rows if int(r['size']) == n]
        assert len(cases) == plan['matricesPerSize'] == int(all_groups[n]['nMatrices'])
        assert int(all_groups[n]['failedCorrections']) == sum(r['correctionRequired'] == 'true' and r['targetReached'] == 'false' for r in cases)
        for metric in ['iterations', 'deltaCi', 'eFull', 'vUpper', 'elapsedMillis', 'initialCi', 'finalCi']:
            mean = np.mean([float(r[metric]) for r in cases])
            assert math.isclose(mean, float(indexed[n, 'ALL', metric]['mean']), rel_tol=1e-12, abs_tol=1e-13)
    comparisons = {(int(r['size']), r['metric']): r for r in comparison}
    sizes = plan['sizes']; figure_root = root / 'figures' / args.profile; figure_root.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 10, 'axes.spines.top': False,
                         'axes.spines.right': False, 'svg.fonttype': 'none'})
    produced = []
    sources = [f'summary/{args.profile}/aggregated.csv', f'summary/{args.profile}/comparison_with_article.csv']

    def save(fig, name, note, data_sources):
        footer = f"Профиль {args.profile}; наши N={plan['matricesPerSize']} на n; группа ALL, включая неудачи"
        fig.text(.5, .012, footer + '\n' + note, ha='center', va='bottom', fontsize=8)
        fig.tight_layout(rect=(0, .13, 1, 1))
        for extension in ['png', 'svg']:
            fig.savefig(figure_root / f'{name}.{extension}', dpi=300, bbox_inches='tight')
        plt.close(fig)
        produced.append({'name': name, 'sources': data_sources, 'note': note,
                         'png': f'figures/{args.profile}/{name}.png', 'svg': f'figures/{args.profile}/{name}.svg'})

    specifications = [
        ('01_iterations', 'iterations', 'Количество принятых исправлений', 'Среднее число исправлений',
         'Авторский подсчёт «варьируемых элементов» не полностью определён', False),
        ('02_delta_ci', 'deltaCi', 'Среднее снижение индекса Саати', 'ΔCI = CI до − CI после',
         'Формула совпадает; исходная авторская выборка и генератор неизвестны', False),
        ('03_time', 'elapsedMillis', 'Время работы корректора', 'Среднее время, мс (логарифмическая шкала)',
         'Только описательно: разные язык, оборудование, JIT и методика', True),
        ('04_squared_error', 'eFull', 'Сумма квадратов отклонений: все элементы', 'Среднее E_full, формула (11)',
         'Одинаковая формула; сравниваются разные исходные выборки', False),
        ('05_variation', 'vUpper', 'Процентная вариация: разные определения', 'Средняя вариация, %',
         'ОПИСАТЕЛЬНО: наша V_upper; формула и знаменатель авторов не указаны', False)]
    for name, metric, title, ylabel, note, log_scale in specifications:
        ours = np.array([float(indexed[n, 'ALL', metric]['mean']) for n in sizes])
        author = [float(comparisons[n, metric]['authorValue']) for n in sizes]
        fig, ax = plt.subplots(figsize=(8, 5.1))
        ax.plot(sizes, author, marker='s', markerfacecolor='none', color='#a85d21', label='Авторы: таблица 1')
        ax.plot(sizes, ours, marker='o', color='#246693', label='Наша реализация: ALL')
        if not log_scale:
            low = [float(indexed[n, 'ALL', metric]['ci95Low']) for n in sizes]
            high = [float(indexed[n, 'ALL', metric]['ci95High']) for n in sizes]
            ax.fill_between(sizes, low, high, color='#246693', alpha=.15, label='Наш 95% ДИ: CLT-приближение')
        else:
            ax.set_yscale('log')
        ax.set(title=title, xlabel='Размерность n', ylabel=ylabel, xticks=sizes)
        ax.grid(alpha=.2); ax.legend(fontsize=8)
        save(fig, name, note, sources)

    fig, axes = plt.subplots(1, 2, figsize=(10, 5))
    axes[0].bar(sizes, [float(comparisons[n, 'originalBestRankChanges']['authorValue']) for n in sizes], color='#a85d21')
    axes[0].set(title='Авторы: абсолютное число случаев', xlabel='Размерность n', ylabel='Случаи; N на размерность не указан', xticks=sizes)
    axes[1].bar(sizes, [100 * float(all_groups[n]['bestRankChangeRate']) for n in sizes], color='#246693')
    axes[1].set(title='Наши данные: частота изменения', xlabel='Размерность n', ylabel='Доля исходно лучших объектов, %', xticks=sizes, ylim=(0, 100))
    for ax in axes: ax.grid(axis='y', alpha=.2)
    save(fig, '06_original_best_rank', 'РАЗНЫЕ ЕДИНИЦЫ: авторские проценты не вычисляются без N на размерность',
         [f'summary/{args.profile}/groups.csv', f'summary/{args.profile}/comparison_with_article.csv'])

    fig, ax = plt.subplots(figsize=(8, 5))
    ax.plot(sizes, [float(all_groups[n]['targetRate']) for n in sizes], marker='o', label='CI≤0.1 среди всех входов')
    ax.plot(sizes, [float(all_groups[n]['requiredCorrectionSuccessRate']) for n in sizes], marker='s', label='Успешно исправлены среди исходных CI>0.1')
    ax.set(title='Достижение целевой согласованности', xlabel='Размерность n', ylabel='Доля', xticks=sizes, ylim=(0, 1.05))
    ax.grid(alpha=.2); ax.legend(fontsize=8)
    save(fig, '07_success', 'Первоначально приемлемые МПС входят только в первую кривую', [f'summary/{args.profile}/groups.csv'])

    fig, ax = plt.subplots(figsize=(8, 5))
    for metric, label, color in [('initialCi', 'До коррекции', '#a85d21'), ('finalCi', 'После коррекции', '#246693')]:
        ax.plot(sizes, [float(indexed[n, 'ALL', metric]['mean']) for n in sizes], marker='o', label=label, color=color)
        ax.fill_between(sizes, [float(indexed[n, 'ALL', metric]['ci95Low']) for n in sizes],
                        [float(indexed[n, 'ALL', metric]['ci95High']) for n in sizes], color=color, alpha=.13)
    ax.axhline(.1, ls='--', color='gray', label='Порог индивидуальной МПС: 0.1')
    ax.set(title='Средний CI до и после коррекции', xlabel='Размерность n', ylabel='CI, среднее по матрицам', xticks=sizes)
    ax.grid(alpha=.2); ax.legend(fontsize=8)
    save(fig, '08_ci_before_after', 'ДИ — CLT-приближение; средний CI не равен доле успешных коррекций', [f'summary/{args.profile}/aggregated.csv'])

    figure_manifest = {'schemaVersion': 1, 'profile': args.profile, 'planSha256': manifest['planSha256'], 'figures': produced,
                       'matplotlibVersion': matplotlib.__version__, 'numpyVersion': np.__version__,
                       'sourceSha256': {str(p.relative_to(root)): sha(p) for p in [summary / f for f in ['runs.csv', 'aggregated.csv', 'groups.csv', 'comparison_with_article.csv']]}}
    (figure_root / 'manifest.json').write_text(json.dumps(figure_manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    report(summary, args.profile, plan, manifest, rows, aggregates, groups, comparison, produced)
    print(f"{args.profile}: {len(rows)} actual matrices, 8 figures in PNG 300 DPI and SVG; analytical report saved")


def report(summary, profile, plan, manifest, rows, aggregates, groups, comparison, figures):
    sizes = plan['sizes']; all_groups = {int(r['size']): r for r in groups if r['group'] == 'ALL'}
    indexed = {(int(r['size']), r['group'], r['metric']): r for r in aggregates}
    mean = lambda n, metric, group='ALL': float(indexed[n, group, metric]['mean'])
    required = sum(int(r['correctionRequired']) for r in all_groups.values())
    corrected = sum(int(r['successfullyCorrected']) for r in all_groups.values())
    failed = sum(int(r['failedCorrections']) for r in all_groups.values())
    already = len(rows) - required; target = already + corrected
    rank_changes = sum(int(r['originalBestRankChanges']) for r in all_groups.values())
    statuses = Counter(r['status'] for r in rows)
    improved = sum(float(r['deltaCi']) > 1e-12 for r in rows)
    worsened = sum(float(r['deltaCi']) < -1e-12 for r in rows)
    plateau_required = sum(r['correctionRequired']=='true' and abs(float(r['deltaCi']))<=1e-12 for r in rows)
    iteration_monotone = all(mean(b, 'iterations')>mean(a, 'iterations') for a,b in zip(sizes, sizes[1:]))
    ci_monotone = all(mean(b, 'deltaCi')>mean(a, 'deltaCi') for a,b in zip(sizes, sizes[1:]))
    e_monotone = all(mean(b, 'eFull')>mean(a, 'eFull') for a,b in zip(sizes, sizes[1:]))
    tied_initial = sum(int(r['initialTiedLeaders'])>1 for r in rows)
    tied_final = sum(int(r['finalTiedLeaders'])>1 for r in rows)
    lines = [f'# Приближённое воспроизведение Куренных и соавторов, 2018: {profile}', '',
             '## 1. Цель эксперимента', '',
             'Проверить математическую работоспособность неизменённой реализации косинусного метода и сопоставить статистику собственной заранее заданной выборки с таблицей 1. Совпадение отдельных чисел не доказывает эквивалентности алгоритмов; расхождение не объявляется автоматически ошибкой.', '',
             '## 2. Что исследовали авторы', '',
             'Геометрическое выявление плохо согласованных сравнений по косинусам строк и рекомендации a_ik←a_ij·a_jk. Таблица 1 содержит время, полную сумму квадратов отклонений, процентную вариацию, итерации, снижение CI и число случаев изменения ранга лучшего объекта. Источник: [оригинальная публикация](https://www.mathnet.ru/rus/ipmp2555), DOI 10.20948/prepr-2018-196. Печатные страницы 12–13 (физические PDF 13–14) и формула (11) проверены визуально. Все числа эталона совпали с PDF; эталон не менялся.', '',
             '## 3. Условия авторского исследования', '',
             'C# 7.0, .NET Core 2.0; случайные положительные обратносимметричные МПС размеров 3–10, статистика по выборке из 1000 матриц. Не установлено однозначно, означает ли это 1000 на каждый n или распределение общей выборки по размерам. Поэтому авторские N на n и проценты изменения ранга здесь отсутствуют.', '',
             'Не опубликованы матрицы, seed и генератор/распределение, распределение N по n, правила первоначального отбора, точный способ весов и равенств, полный порядок рекомендаций и ограничений, лимит правок, аппаратура и прогрев. В статье обсуждается шкала 1/9–9, но отображение выходящих за неё рекомендаций не определено.', '',
             '## 4. Условия нашего исследования', '',
             f"Фактически выполнен **{profile}: {len(rows)} матриц, {plan['matricesPerSize']} на каждое n={sizes}**, master seed={plan['masterSeed']}. Идентификаторы K2018_nNN_mMMMM, фиксированные seed существующего генератора. Верхние сравнения независимо равномерны на 17 значениях {{1/9,…,1/2,1,2,…,9}}; нижние восстановлены обратными, диагональ 1. Предварительного отбора по CI/K нет. Выборка с возвращением: совпадение значений двух случайных матриц допустимо, ID и seed уникальны. Общий изменяемый RNG между матрицами не используется.", '',
             f"Существующий CosineConsistencyCorrector: CI≤{plan['targetCi']}, максимум {plan['maxCorrections']} принятых правок, диапазон [1/9,9], минимальное улучшение CI 10⁻¹², минимальная поддержка 0. План, определения и группы зафиксированы до запусков в [PROTOCOL.md](../../PROTOCOL.md); хеш плана `{manifest['planSha256']}`. Первые пять входов SMOKE/REPORT совпадают; Java-аудит сверяет их, если архив SMOKE доступен. Ни seed, ни вариант V не подбирались по результатам.", '',
             f"Выполнено {manifest['warmups']} отдельных прогревочных запусков (10 на n); они исключены из всех статистических групп. Время измерено внутри единственного рабочего потока только вокруг correct(), включая внутренние пересчёты показателей корректора, без генерации, внешнего анализа/весов, I/O и графиков. Порядок — n по возрастанию, затем sample; ограниченный прогрев не устраняет все JIT/GC эффекты. Полное время Java-профиля по manifest: **{manifest['wallSeconds']:.3f} с**. Среда сохранена в [environment.json](environment.json).", '',
             'Основная группа ALL включает все матрицы и все неудачные статусы. NEEDS_CORRECTION — только исходный CI>0.1, включая неудачи. SUCCESSFUL_REQUIRED — только успешно исправленные из предыдущей группы. Отсутствие изменений при первоначально приемлемой матрице — нормальный результат. Веса — прежний PerronWeights (главный правый собственный вектор); это объекты самой МПС, без таблицы альтернатив этапа 7.', '',
             '## 5. Различия реализаций', '',
             'Наша неизменённая реализация каждый раз пересчитывает косинусы, ранжирует поддержку j по min(xi_ij,xi_jk), детерминированно разрешает равенства и принимает только строго улучшающую CI допустимую правку. Выход за шкалу отклоняется без обрезки/округления. Авторский метод описывает рекомендации эксперту, допускает принятие, отказ или собственную величину исправления; полный автоматический C# путь и выбор j не опубликованы. Эти различия установлены по описаниям; их отдельные количественные вклады в расхождения не установлены.', '',
             'В нашей реализации для n=3 любая принятая правка через единственный третий объект делает единственную триаду согласованной; поэтому требуется максимум одна принятая правка. Авторское среднее 1.507 не воспроизводится при таком определении итерации. Возможны иной счёт элементов/обратных ячеек, иная процедура/ограничения, но выбирать одну причину без трассы нельзя.', '',
             '## 6. Формулы показателей', '',
             'Основной E_full = Σ_iΣ_j(b_ij−a_ij)² — точная формула (11) авторов. Дополнительный E_upper = Σ_i<j(b_ij−a_ij)². Основной V_upper = (100/m)Σ_i<j|b_ij−a_ij|/a_ij, m=n(n−1)/2. Это **наше** определение, поскольку в статье нет формулы/знаменателя процента. Дополнительные V_changed делит ту же сумму на число окончательно изменённых верхних генов (null при нуле), V_offdiagonal усредняет по всем i≠j. Эти варианты не выбирались по близости к статье.', '',
             'ΔCI=CI_initial−CI_final; D=√mean_i<j[(ln(b_ij/a_ij))²], существующая устойчивая реализация. Итерации корректора, длина истории принятых правок, число уникальных затронутых историей пар и число окончательно изменённых независимых пар сохранены отдельно. В текущем API iterations=history.size, но уникальные пары/окончательные изменения могут отличаться.', '',
             'Приоритеты сортируются по убыванию; точно равные double-веса — по исходному индексу, позиции 1,2,… . Сохраняются лидер до/после и позиция первоначального лидера, Δпозиции=after−before. Почти равные веса искусственно не объединяются. Способ весов/равенств авторов неизвестен.', '',
             'Для каждой размерности и группы сохранены mean/median, выборочная SD (N−1), min/max и число определённых значений. 95% ДИ среднего: mean±1.959963984540054·SD/√N, **нормальное CLT-приближение**. N<2: SD/ДИ null, N<30: smallSample=true, нормальная аппроксимация ненадёжна. Интервалы не обрезаются по нулю. Это не авторские интервалы, не проверка значимости различия с авторами и не гарантия покрытия при асимметричных распределениях.', '',
             '## 7. Результаты по размерностям', '',
             '| n | Всего | Уже CI≤0.1 | Нуждались | Исправлены | Неудачи | Достигнут порог, все | Успех среди нуждавшихся |',
             '|---:|---:|---:|---:|---:|---:|---:|---:|']
    for n in sizes:
        r = all_groups[n]
        lines.append(f"| {n} | {r['nMatrices']} | {r['initiallyAcceptable']} | {r['correctionRequired']} | {r['successfullyCorrected']} | {r['failedCorrections']} | {100*float(r['targetRate']):.1f}% | {100*float(r['requiredCorrectionSuccessRate']):.2f}% |")
    lines += ['', f"Итого: {already} первоначально приемлемых, {required} требовали коррекции, {corrected} успешно исправлены, {failed} неудачных коррекций. Порог достигнут у {target}/{len(rows)} ({100*target/len(rows):.3f}%) с учётом первоначально приемлемых. Успех среди нуждавшихся: {corrected}/{required} ({100*corrected/required:.3f}%).", '',
              '| n | Средний CI до | Средний CI после | Среднее D | Средние правки | Уникально затронуто | Окончательно изменено |',
              '|---:|---:|---:|---:|---:|---:|---:|']
    for n in sizes:
        values = [mean(n, m) for m in ['initialCi', 'finalCi', 'logDeviation', 'acceptedCorrections', 'uniqueTouchedComparisons', 'changedComparisons']]
        lines.append(f"| {n} | " + ' | '.join(f'{v:.6f}' for v in values) + ' |')
    lines += ['', 'Статусы по размерностям:', '', '| n | Статусы и количества |', '|---:|---|']
    for n in sizes:
        counts = Counter(r['status'] for r in rows if int(r['size'])==n)
        lines.append(f"| {n} | " + '; '.join(f'{k}: {v}' for k,v in sorted(counts.items())) + ' |')
    lines += ['', 'Три группы (для интерпретации отбора; основная — ALL):', '',
              '| n | Группа | N | Итерации | ΔCI | E_full | V_upper, % |', '|---:|---|---:|---:|---:|---:|---:|']
    for group in groups:
        n = int(group['size']); name = group['group']
        vals = [indexed[n,name,m]['mean'] for m in ['iterations','deltaCi','eFull','vUpper']]
        lines.append(f"| {n} | {name} | {group['nMatrices']} | " + ' | '.join('NA' if v=='NA' else f'{float(v):.5f}' for v in vals) + ' |')
    lines += ['', 'Полная статистика: [aggregated.csv](aggregated.csv) / [JSON](aggregated.json), [группы](groups.csv), [каждый запуск](runs.csv), [индекс входов](inputs.csv). Для каждого показателя доступны медиана, SD, min/max, ДИ и N_defined. Прогрев хранится отдельно в warmup.json.', '',
              '## 8. Авторы — наша реализация', '',
              'Разность = наша средняя ALL − опубликованное значение; относительная разность делится на модуль значения авторов. Относительные разности ниже — арифметические описательные различия, не оценки значимости или ошибки программы. Для рангов разности отсутствуют из-за неизвестного авторского N на размерность.', '']
    names = {'iterations': 'Среднее число итераций / принятых парных правок', 'deltaCi': 'Среднее снижение CI',
             'elapsedMillis': 'Время, мс: только описательное сопоставление', 'eFull': 'Полная сумма квадратов, формула (11)',
             'vUpper': 'Процентная вариация: формула авторов неизвестна', 'originalBestRankChanges': 'Изменение ранга исходного лидера: несопоставимые знаменатели'}
    for metric, title in names.items():
        lines += [f'### {title}', '', '| n | Авторы | Наша ALL | Наш 95% ДИ среднего | Разность | Абсолютная разница | Относительная разность |', '|---:|---:|---:|---|---:|---:|---:|']
        for r in comparison:
            if r['metric']!=metric: continue
            n = int(r['size']); interval = 'не применяется к счётчику'
            if metric!='originalBestRankChanges':
                a = indexed[n,'ALL',metric]; interval = f"[{float(a['ci95Low']):.6g}; {float(a['ci95High']):.6g}]"
            delta = 'NA' if r['differenceOurMinusAuthor']=='NA' else f"{float(r['differenceOurMinusAuthor']):+.6g}"
            absolute = 'NA' if r['absoluteDifference']=='NA' else f"{float(r['absoluteDifference']):.6g}"
            relative = 'NA' if r['relativeDifference']=='NA' else f"{100*float(r['relativeDifference']):+.2f}%"
            lines.append(f"| {n} | {float(r['authorValue']):.6g} | {float(r['ourValue']):.6g} | {interval} | {delta} | {absolute} | {relative} |")
        warnings = next(r['limitations'] for r in comparison if r['metric']==metric)
        lines += ['', f'Ограничение сопоставимости: {warnings}', '']
    lines += ['Дополнительные заранее определённые варианты — не подмена основной метрики:', '',
              '| n | E_upper | V_changed, % | V_offdiagonal, % |', '|---:|---:|---:|---:|']
    for n in sizes:
        changed = indexed[n,'ALL','vChanged']['mean']
        changed_text = 'NA' if changed == 'NA' else f'{float(changed):.6f}'
        lines.append(f"| {n} | {mean(n,'eUpper'):.6f} | {changed_text} | {mean(n,'vOffdiagonal'):.6f} |")
    lines += ['', '[Машиночитаемая таблица сравнения и предупреждений](comparison_with_article.csv) / [JSON](comparison_with_article.json). Авторские частоты/проценты из последнего столбца таблицы не вычислялись.', '',
              '## 9. Анализ совпадений и расхождений', '',
              'Для E_full и CI сравниваются одинаковые математические меры, но неизвестная авторская выборка не равна нашей. Для вариации не установлена идентичность определений. Для итераций есть конкретная несовместимость счёта при n=3. Наш непрерывный допустимый диапазон и отбраковка за его пределами могут блокировать правку, тогда как авторское отображение на шкалу не задано. Строгое улучшение CI и выбор j также могут менять траекторию. Это возможные механизмы, а не доказанная количественная декомпозиция разницы.', '',
              'При n=3 недопустимыми могут оказаться все три мультипликативные рекомендации: например, для цикла с a12=9, a23=9, a13=1/9 необходимые значения 81 или 1/81 выходят за шкалу. Такой отказ — ограничение фиксированного метода/диапазона, а не разрешение скрыто обрезать значения. Исходные матрицы с нулём принятых исправлений остаются в статистике.', '',
              'Среднее число правок увеличивается с n, но ограничение 100 создаёт цензурирование остановки на сложных входах. Поэтому средние по SUCCESSFUL_REQUIRED являются условными по успеху и не заменяют ALL. Большее снижение CI может отражать более высокий исходный CI, а не лучшую относительную эффективность. Среднее время отражает конкретную среду, а не универсальное ускорение относительно C#.', '',
              '## 10. Ответы на исследовательские вопросы', '',
              f"**RQ1.** CI уменьшился более чем на 10⁻¹² в {improved}/{len(rows)} запусках; существенно увеличился в {worsened}. Исходно требовали коррекции, но не получили измеренного улучшения: {plateau_required}. Средние до/после по n приведены в разделе 7. Это подтверждает работоспособность на значительной части фиксированной выборки, но не гарантирует успешную правку каждой МПС.", '',
              f"**RQ2.** Порог достигнут у {target}/{len(rows)} ({100*target/len(rows):.3f}%) всех входов; из {required} исходно неприемлемых исправлены {corrected} ({100*corrected/required:.3f}%), неудач {failed}. Диапазон успеха по размерностям: " + '; '.join(f"n={n}: {100*float(all_groups[n]['requiredCorrectionSuccessRate']):.2f}%" for n in sizes) + '.', '',
              '**RQ3.** Среднее число принятых правок по n: ' + '; '.join(f"{n}: {mean(n,'iterations'):.3f}" for n in sizes) + f". Строгий рост на всех соседних размерностях: {'да' if iteration_monotone else 'нет'}. Это качественное сопоставление с ростом в таблице 1, при несовпадающем определении итераций/выборке; наибольшие размерности ограничены 100 правками.", '',
              '**RQ4.** Среднее ΔCI по n: ' + '; '.join(f"{n}: {mean(n,'deltaCi'):.6f}" for n in sizes) + f". Строго монотонный рост: {'да' if ci_monotone else 'нет'}. Следовательно, закономерность авторов подтверждена {'качественно по всем соседним n, но не численно' if ci_monotone else 'лишь частично; полный монотонный рост не воспроизведён'}. Изменения исходного CI и доли неудач нужно учитывать вместе.", '',
              f"**RQ5.** E_full от n={sizes[0]} до n={sizes[-1]}: {mean(sizes[0],'eFull'):.6f}→{mean(sizes[-1],'eFull'):.6f}; строгий рост по n: {'да' if e_monotone else 'нет'}. V_upper по n: " + '; '.join(f"{n}: {mean(n,'vUpper'):.3f}%" for n in sizes) + '. Значения более 100% допустимы из-за относительного знаменателя; допустимый диапазон самих сравнений сохранён. Это существенное изменение экспертных оценок, а не утверждение об их малости. D и альтернативные определения приведены отдельно.', '',
              f"**RQ6.** Ранг первоначально лучшего объекта изменился в {rank_changes}/{len(rows)} ({100*rank_changes/len(rows):.3f}%) входов. По n: " + '; '.join(f"{n}: {all_groups[n]['originalBestRankChanges']}/{all_groups[n]['nMatrices']}" for n in sizes) + f". Точные равенства лидирующих double-весов: исходно {tied_initial} случаев, в результате {tied_final}; применено заранее заданное правило индексов. Авторские проценты не определены. Два отдельно сохранённых признака — смена лидера и изменение позиции исходного лидера — в данной порядковой схеме совпадают, но не объединены в данных.", '',
              '**RQ7.** Проверены генерация и свойства МПС, исходные/итоговые CI/K, веса, сохранённые истории и сводки. Качественно сопоставимы наблюдаемый рост числа правок и E_full; динамика ΔCI охарактеризована в RQ4. Средние таблицы 1 численно сопоставлены, но не воспроизведены точно. Вариация, авторские ранговые частоты, полный автоматический алгоритм и исходная выборка не восстановлены по имеющимся сведениям.', '',
              '## 11. Ограничения и проверяемость', '',
              'Конечная псевдослучайная выборка фиксированного распределения; неизвестные авторские входы/seed/N по n и условные выборки; различия процедур и шкалы; порог/лимит 100; точные double-равенства; собственные правые приоритеты; нормальная аппроксимация ДИ при асимметричных хвостах; только одна измерительная сессия на конкретной машине. Доверительные интервалы не учитывают неопределённость неизвестного авторского протокола, систематические отличия генерации и реализации. Не делается тест превосходства или причинного объяснения каждого расхождения.', '',
              'Архивы исходов и входов разнесены по профилям, содержат однозначные ID/seed и SHA-256. [manifest.json](manifest.json) COMPLETE подтверждает объём и хеши. Java-аудит читает каждую матрицу, восстанавливает генерацию, воспроизводит историю без новых оптимизаций, проверяет положительность/диагональ/обратность/шкалу, CI/K, веса, ранги, метрики и все агрегаты/CSV. Для чисел аудита допуск 10⁻¹¹·max(1,|x|,|y|), целочисленные ID/seed сравниваются точно; хеши/исходные double проверяются точно. Массивы из архивов являются нашими данными, не авторскими.', '',
              'Входы: `inputs/<PROFILE>/matrices.jsonl.gz`; итоги, веса и истории: `outputs/<PROFILE>/results.jsonl.gz`. Архивы исключены из Git, но формируются задачей автоматически и могут быть включены в отдельный ZIP для преподавателя. При отсутствии архивов сначала запустить reproduceKurennykh2018, затем verifyKurennykh2018. Предыдущие исследовательские результаты/эталоны не перезаписывались.', '',
              '## 12. Итог о воспроизводимости', '',
              '**Точное воспроизведение экспериментальных чисел таблицы 1 не достигнуто и по доступным данным не может быть подтверждено.** Выполнено самостоятельное приближённое воспроизведение состава исследования на полном фиксированном плане. Математическая работоспособность подтверждена численными проверками; качественные закономерности подтверждены в указанной в RQ3–RQ7 степени. Численное сопоставление E_full, ΔCI и времени выполнено с явными ограничениями. Неоднозначные определения/генератор и неизвестные исходные данные препятствуют заключению о полной идентичности авторскому исследованию. Отрицательные результаты и все неудачи сохранены.', '',
              '## Графики и воспроизведение', '']
    for f in figures:
        lines.append(f"- [{f['name']} PNG](../../{f['png']}) / [SVG](../../{f['svg']}); {f['note']}; источники: {', '.join(f['sources'])}.")
    lines += ['', '`./gradlew reproduceKurennykhSmoke` → `./gradlew reproduceKurennykh2018` → `./gradlew verifyKurennykh2018` → `./gradlew plotKurennykh2018`.', '',
              'Повторное построение отчёта/графиков не запускает корректор. Основная статистика относится только к фактически выполненному профилю, указанному в заголовке и manifest.', '']
    (summary / 'report.md').write_text('\n'.join(lines), encoding='utf-8')


if __name__ == '__main__':
    main()
