const fs = require('fs');
const D = require('/tmp/reportbuild/node_modules/docx');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType,
  Table, TableRow, TableCell, WidthType, ShadingType, ImageRun, PageNumber, Footer
} = D;

const BASE = '/sessions/ecstatic-blissful-heisenberg/mnt/MantiMetrics/output/report';
const data = JSON.parse(fs.readFileSync(BASE + '/report_data.json', 'utf8'));
const FIG = BASE + '/figures';
const OUT = '/sessions/ecstatic-blissful-heisenberg/mnt/Using Jira and Git/Report_Milestone1.docx';

const TABLE_W = 9000;
const HEAD_FILL = 'D9E1F2';

function parseRuns(text) {
  const runs = [];
  const parts = String(text).split(/(\*\*[^*]+\*\*)/g);
  for (const part of parts) {
    if (!part) continue;
    if (part.startsWith('**') && part.endsWith('**'))
      runs.push(new TextRun({ text: part.slice(2, -2), bold: true }));
    else runs.push(new TextRun(part));
  }
  return runs;
}
function p(text, opts = {}) {
  return new Paragraph({ spacing: { after: opts.after == null ? 140 : opts.after, line: 276 }, alignment: opts.align, children: parseRuns(text) });
}
function h1(text) { return new Paragraph({ heading: HeadingLevel.HEADING_1, spacing: { before: 260, after: 130 }, children: [new TextRun({ text })] }); }
function h2(text) { return new Paragraph({ heading: HeadingLevel.HEADING_2, spacing: { before: 180, after: 90 }, children: [new TextRun({ text })] }); }

function cell(text, w, o) {
  o = o || {};
  return new TableCell({
    width: { size: w, type: WidthType.DXA },
    shading: o.fill ? { type: ShadingType.CLEAR, fill: o.fill } : undefined,
    margins: { top: 40, bottom: 40, left: 90, right: 90 },
    children: [new Paragraph({ alignment: o.align || AlignmentType.LEFT, spacing: { after: 0, line: 252 }, children: [new TextRun({ text: String(text), bold: !!o.bold })] })],
  });
}
function table(headers, rows, widths) {
  const headRow = new TableRow({ tableHeader: true, children: headers.map((hd, i) => cell(hd, widths[i], { bold: true, fill: HEAD_FILL, align: i === 0 ? AlignmentType.LEFT : AlignmentType.CENTER })) });
  const bodyRows = rows.map(r => new TableRow({ children: r.map((c, i) => cell(c, widths[i], { align: i === 0 ? AlignmentType.LEFT : AlignmentType.CENTER })) }));
  return new Table({ columnWidths: widths, width: { size: TABLE_W, type: WidthType.DXA }, rows: [headRow, ...bodyRows] });
}
function figure(file, wpx, hpx, caption) {
  return [
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 120, after: 40 }, children: [new ImageRun({ type: 'png', data: fs.readFileSync(`${FIG}/${file}`), transformation: { width: wpx, height: hpx } })] }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 160 }, children: [new TextRun({ text: caption, italics: true, size: 18, color: '555555' })] }),
  ];
}

const perRows = data.per.map(x => [x.rel, String(x.cls), String(x.buggy), x.buggyPct.toFixed(1) + '%', String(x.smelly)]);
perRows.push(['Totale', String(data.totRows), String(data.totBuggy), (100 * data.totBuggy / data.totRows).toFixed(1) + '%', '1875']);
const sensRows = data.sens.map(s => [s.label, String(s.releases), String(s.rows), s.buggyPct.toFixed(1) + '%', s.smellyPct.toFixed(1) + '%']);

const children = [];
children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 40 }, children: [new TextRun({ text: 'Milestone 1 — Costruzione del Dataset per la Bug Prediction', bold: true, size: 34 })] }));
children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 30 }, children: [new TextRun({ text: 'Progetto Apache AVRO · tool MantiMetrics', size: 24 })] }));
children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 240 }, children: [new TextRun({ text: 'ISW2 2025–2026 · Matteo Lagioia', size: 20, color: '555555' })] }));

children.push(h1('1. Introduzione'));
children.push(p('Apache AVRO è un framework di serializzazione dati e di remote procedure call ampiamente utilizzato nell\'ecosistema dei big data, con schemi definiti in JSON e supporto multi-linguaggio. Questa Milestone costruisce, per il modulo Java del progetto, un **dataset a livello di classe** per la predizione dei difetti (bug prediction): ogni riga rappresenta una classe osservata in una specifica release, descritta da metriche di prodotto, di processo, di code smell e di ticket, con una variabile target booleana **Buggy** che indica se quella classe è risultata difettosa in quella release.'));
children.push(p('Il dataset è generato dal tool **MantiMetrics**, che integra le informazioni di Jira (release e bug-ticket) e di Git/GitHub (storia dei commit, sorgenti) e le arricchisce con le analisi di SonarCloud. L\'obiettivo della Milestone 1 non è l\'accuratezza di un modello, bensì la **correttezza metodologica** e la piena tracciabilità delle scelte di costruzione del dataset, discusse criticamente nella sezione Threats to Validity.'));

children.push(h1('2. Metodologia'));
children.push(h2('2.1 Estrazione delle release e dei ticket'));
children.push(p('Le release sono ottenute incrociando i **tag Git** del repository (114 tag) con le **versioni Jira**, ottenendo 43 release comuni ordinate cronologicamente. Ad esse si applica lo **snoring** (§2.4), che seleziona il primo 34% della timeline, cioè le **14 release più vecchie** (da 1.0.0 a 1.5.4). I bug-ticket sono estratti da Jira selezionando le issue di tipo difetto in stato risolto/chiuso (**1114 ticket**), con i relativi campi: data di creazione, affected/fixed versions, priorità, tipo e componenti. È previsto, come flag sperimentale, l\'arricchimento con le **GitHub Issues** etichettate come bug.'));
children.push(h2('2.2 Linkage commit–ticket'));
children.push(p('Il **linkage** collega un ticket ai file effettivamente modificati per risolverlo, analizzando i messaggi di commit alla ricerca dell\'identificativo del ticket (pattern AVRO-<numero>, ed opzionalmente i riferimenti alle GitHub Issue). La qualità del dataset dipende criticamente da questo passo: il **tasso di linkage** misurato è **0.90** (90,1% dei commit che toccano file Java riportano almeno un ID ticket), un valore elevato che indica buona tracciabilità e ridotto rischio di falsi negativi.'));
children.push(h2('2.3 Etichettatura dei difetti e stima dell\'Injected Version (Proportion)'));
children.push(p('Per ogni difetto si determinano la Opening Version (OV, release di apertura del ticket), la Fixed Version (FV, ricavata dal commit di fix collegato) e la Injected Version (IV, release che ha introdotto il difetto). Quando Jira riporta le **affected versions** (287 ticket) l\'IV è nota direttamente. In assenza, l\'IV è stimata con la tecnica **Proportion**: IV = FV − (FV − OV) · P, dove P è calibrato sui ticket con IV nota. La variante **Total** usa un valore medio globale di P (P medio = 0,984), mentre la variante **Incremental** ricalcola P progressivamente in ordine cronologico. Una classe è etichettata **Buggy** in una release R se R ricade nell\'intervallo [IV, FV) di un difetto che ne ha toccato i file. Complessivamente 442 ticket disponevano di un commit di fix e 103 hanno richiesto il fallback su Proportion. Le date mancanti (OV/FV) sono ricostruite dalle date dei tag Git; in assenza di dati di calibrazione P vale 1 (IV = OV).'));
children.push(h2('2.4 Snoring'));
children.push(p('Le release più recenti soffrono di **snoring**: molti difetti non sono ancora stati scoperti al momento dell\'osservazione, per cui classi realmente difettose verrebbero etichettate erroneamente come pulite. Per mitigare questo bias si scarta la coda più recente della timeline. La scelta di progetto è conservare il **primo 34%** delle release (scartando l\'ultimo ~66%): un compromesso tra quantità di dati e affidabilità delle etichette. La variante alternativa (primo 20%) è usata come confronto nell\'analisi di sensibilità.'));

children.push(h1('3. Costruzione del dataset'));
children.push(p('Ogni classe-release è descritta da 35 colonne: 4 identificative (Project, Path, Class, ReleaseId), le feature predittive raggruppate qui di seguito, e il target Buggy come ultima colonna.'));
children.push(h2('3.1 Metriche di prodotto'));
children.push(p('**LOC** (linee di codice) come proxy di dimensione; **WMC** (Weighted Methods per Class, somma della complessità ciclomatica dei metodi) come indice di complessità cognitiva; **LCOM** (Lack of Cohesion of Methods) come indicatore di coesione/qualità del design. Per limiti dell\'analizzatore sintattico (JavaParser senza symbol solver) le restanti metriche CK (DIT, NOC, CBO, RFC) non sono calcolate: è una deviazione dichiarata nei Threats to Validity.'));
children.push(h2('3.2 Metriche di processo'));
children.push(p('Nella modalità **intervallo** (variazione nella singola release): **NR** (numero di revisioni), **NFix** (commit di fix), **NAuth** (autori distinti), **LOC_Added**, **LOC_Deleted**, **Churn**. Nella modalità **cumulativa**: **totalNR, totalNFix, totalNAuth, totalChurn** e **Age** (numero di release da quando la classe è comparsa). Si aggiungono i massimi storici **maxLOC, maxWMC, maxNSmells**. Il razionale è che la propensione al cambiamento (change proneness) è storicamente correlata alla presenza di difetti.'));
children.push(h2('3.3 Code smell (NSmells)'));
children.push(p('**NSmells** è il numero di code smell per classe rilevati da **SonarCloud** con analisi source-only (nessuna compilazione, sonar.java.binaries=.); **NSmellsDensity** normalizza il valore sulle LOC. La copertura è di **13 release su 14**: la 1.0.0 è esclusa per un\'anomalia del tag Git (vedi Threats).'));
children.push(h2('3.4 Feature dai ticket (TLP)'));
children.push(p('Feature Ticket-Level aggregate sulle issue collegate a una classe: **PriorityMax/Avg** (priorità del ticket), **TypeRiskMax/Avg** (rischio derivato dal tipo di issue), **ComponentCountMax/Avg** (numero di componenti toccati), aggregate come massimo e media. Si aggiungono **OpenTickets** (numero di ticket aperti alla release, come "temperatura" esterna del progetto) e **TLCC_Lin/TLCC_Log** (Temporal Locality of Changes, versione lineare e logaritmica), che catturano quanto di recente una classe sia stata coinvolta nei ticket.'));
children.push(h2('3.5 Feature storiche e target'));
children.push(p('**prevNSmells** e **prevBuggy** riportano lo stato della classe nella release precedente; **Buggy** (yes/no) è il target ed è collocato come ultima colonna, come richiesto per l\'addestramento in Weka.'));

children.push(h1('4. Risultati (Research Questions)'));
children.push(h2('RQ1 — Quante classi e quante difettose per release?'));
children.push(p(`La variante principale (**pct34_total_gh0_churn0**) contiene **${data.totRows} righe** su ${data.selected} release, con **${data.totBuggy} classi Buggy (14,1%)**, proporzione realistica per un dataset di defect prediction (la classe difettosa è minoritaria). Le tre release più vecchie (1.0–1.2) presentano 0% di Buggy: è un effetto di bordo atteso, dovuto all\'assenza di difetti futuri tracciabili così indietro nel tempo. La percentuale di Buggy raggiunge i massimi in 1.3.3 (28,9%) e 1.5.4 (28,7%).`));
children.push(table(['Release', '# Classi', '# Buggy', '% Buggy', '# Smelly'], perRows, [1400, 1900, 1900, 1900, 1900]));
children.push(...figure('fig1_classi_buggy_per_release.png', 600, 300, 'Figura 1 — Numero di classi (barre) e percentuale di Buggy (linea) per release nella variante principale.'));
children.push(h2('RQ2 — Il dataset è stabile?'));
children.push(p(`Si contano **${data.distinctClasses} classi distinte** nell\'arco delle 14 release; **${data.stableAll} (19,9%)** sono presenti in tutte le release e un ampio nucleo persiste per almeno 7 release. La popolazione cresce in modo regolare (da 93 a 310 classi), segno di una base stabile con espansione progressiva: le classi non cambiano drasticamente da una release all\'altra, il che rende sensato il tracciamento storico delle feature cumulative.`));
children.push(h2('RQ3 — Come cambia il dataset al variare dei flag (analisi di sensibilità)?'));
children.push(p('I quattro flag generano 16 varianti, ma su AVRO le dimensioni indipendenti effettive sono due. L\'integrazione delle **GitHub Issues** non produce alcun effetto (nessun bug-ticket aggiuntivo collegato) e le due varianti **Proportion (Total/Incremental)** generano etichette identiche (P medio ≈ 0,98, con la maggioranza dei ticket dotata di affected versions): i dataset risultano quindi coincidenti lungo questi due assi. Restano impattanti soltanto lo **snoring** e l\'**esclusione delle classi con churn = 0**, come riassunto nella tabella e nella Figura 3.'));
children.push(table(['Variante', '# Release', '# Righe', '% Buggy', '% Smelly'], sensRows, [3400, 1400, 1400, 1400, 1400]));
children.push(...figure('fig3_sensibilita.png', 600, 300, 'Figura 3 — Effetto di snoring e churn=0 su dimensione del dataset e percentuale di Buggy (GitHub Issues e Proportion ininfluenti su AVRO).'));
children.push(p('L\'esclusione delle classi con churn = 0 rimuove le classi non modificate nella release, riducendo fortemente le righe (da 3183 a 842) e concentrando sia i difetti (14,1% → 22,8%) sia gli smell. La variante principale scelta è **pct34_total_gh0_churn0**, che massimizza i dati disponibili, adotta la Proportion Total (stabile) e non applica il filtro aggressivo su churn, restando la base più affidabile per la Milestone 2.'));
children.push(h2('Potere discriminante delle feature'));
children.push(p('La Figura 2 confronta la distribuzione di LOC, WMC e NSmells tra classi difettose e non: le classi Buggy tendono a valori mediani più alti, coerentemente con l\'ipotesi che dimensione, complessità e code smell siano associati alla difettosità.'));
children.push(...figure('fig2_boxplot_feature_buggy.png', 600, 267, 'Figura 2 — Distribuzione di LOC, WMC e NSmells per classi non difettose (no) e difettose (yes).'));

children.push(h1('5. Threats to Validity'));
children.push(p('**Linkage (falsi negativi).** Con un linkage del 90%, circa il 10% dei fix non riporta un ID ticket e resta "invisibile": alcune classi realmente difettose vengono etichettate come pulite, introducendo rumore nel target.'));
children.push(p('**Data leakage (Proportion Total).** La variante Total stima l\'IV con un P medio globale che incorpora informazione futura: è temporalmente irrealistica, ma scelta per la stabilità del dato nel contesto didattico. Su AVRO l\'effetto è comunque nullo, poiché Total e Incremental coincidono.'));
children.push(p('**Snoring.** Scartare l\'ultimo 66% della timeline riduce i dati disponibili, ma è necessario per eliminare le release "dormienti" in cui i difetti non sono ancora emersi; è un compromesso tra perdita di dati e pulizia delle etichette.'));
children.push(p('**Deviazione sulle metriche CK.** Sono calcolate solo WMC e LCOM; l\'omissione di DIT, NOC, CBO ed RFC (dovuta all\'assenza del symbol solver) è una minaccia alla validità di costrutto: parte del design object-oriented non è catturata.'));
children.push(p('**NSmells (copertura e 1.0.0).** Gli smell sono source-only e la copertura è 13/14 release. La release 1.0.0 è esclusa perché il tag Git risolve a un albero di codice post-riorganizzazione non corrispondente al vero 1.0.0: assegnarne gli smell avrebbe introdotto rumore da una versione errata (peggio di 0). I cambi di layout dei sorgenti nel tempo complicano le scansioni per-release.'));
children.push(p('**GitHub Issues.** L\'integrazione non ha effetto misurabile su AVRO; il risultato non è generalizzabile ad altri progetti in cui le issue potrebbero aggiungere difetti o rumore.'));
children.push(p('**Classi di test.** Sono naturalmente escluse perché l\'analisi copre solo le directory dei sorgenti principali (src/java, src/main/java); scelta accettabile, poiché i bug nei test raramente vengono tracciati in Jira.'));
children.push(p('**Feature selection.** Il dataset include intenzionalmente tutte le ~30 feature: la selezione è rimandata alla Milestone 2, per lasciare la scelta agli algoritmi ed evitare bias manuali.'));

children.push(h1('6. Piani per la Milestone 2'));
children.push(p('I 16 dataset sono già esportati in formato **ARFF** compatibile con Weka (attributo Buggy nominale {yes,no} come ultima colonna, identificativi come stringhe ignorabili/rimovibili). In M2 si procederà con: **feature selection** (InfoGain, CfsSubsetEval, eventualmente wrapper); addestramento di più **classificatori** (Random Forest, Naïve Bayes, IBk, alberi/logistica); **bilanciamento** della classe (es. SMOTE/undersampling) applicato al solo training set; **valutazione** con validazione time-ordered (walk-forward per release) e metriche AUC, precision/recall e Cohen\'s Kappa. Il confronto tra varianti — in particolare l\'effetto del filtro churn = 0 — sarà discusso alla luce dei risultati.'));

const doc = new Document({
  creator: 'Matteo Lagioia',
  title: 'Milestone 1 — Dataset Bug Prediction AVRO',
  styles: { default: { document: { run: { font: 'Calibri', size: 22 } } } },
  sections: [{
    properties: {},
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ children: ['Pag. ', PageNumber.CURRENT, ' / ', PageNumber.TOTAL_PAGES], size: 18, color: '888888' })] })] }) },
    children,
  }],
});

Packer.toBuffer(doc).then(buf => { fs.writeFileSync(OUT, buf); console.log('written', OUT, buf.length, 'bytes'); });
