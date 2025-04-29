# 📊 MantiMetrics

**MantiMetrics** is a Java/Maven application designed to extract **static** and **commit** software metrics from Git projects and generate customised CSV datasets.

---

## 🚀 How it works

MantiMetrics:
- Reads configuration from `.properties` files
- Extracts static metrics from source code
- Extracts commit metrics from Git history
- Merges data
- Exports the final dataset in `.csv` format

All are based **dynamically** on the required metrics, with no need to modify the code.

---

## 🛠️ Project Structure

```
MantiMetrics/
├── config/
│ ├── config_avro.properties
│ └── config_bookkeeper.properties
├── data/
│ ├── raw/
│ │ ├── avro/
│ │ └── bookkeeper/
│ └── output/
│ ├── dataset_avro.csv
│ └── dataset_bookkeeper.csv
├── src/
│ └── main/
│ └── java/
│ └── it/
│ └── mantimetrics/
│ ├── App.java
│ ├── extractor/
│ │ ├── StaticMetricsExtractor.java
│ │ └── CommitMetricsExtractor.java
│ ├── merger/
│ │ └── DataMerger.java
│ ├── writer/
│ │ └── CsvWriter.java
│ └── utils/
│ ├── ConfigLoader.java
│ └── MetricsConfiguration.java
├── pom.xml
└── README.md
```

---

## ⚙️ Configuration

For each Git project to be analyzed, you must create a `.properties` configuration file like this one:

### Example: `config/config_avro.properties`

```properties
project.name=AVRO
code.directory=data/raw/avro/src
commit.repo.directory=data/raw/avro
release.percentage=33
output.csv=data/output/dataset_avro.csv

jira.url=https://issues.apache.org/jira
jira.projectKey=AVRO
jira.ticketType=defect
jira.ticketStatus=Closed,Resolved
jira.resolution=Fixed

# Metrics to be extracted
metrics.static=LOC,cyclomaticComplexity,nestingDepth,branchCount
metrics.commit=methodHistories,authors,churn

method.pattern=.*
```

### Main parameters:

| Parameter | Description |
|:------------------------|:------------------------------------------------------|
| `code.directory` | Path to source files. |
| `commit.repo.directory` | Path to the Git repository. |
| `metrics.static` | Static metrics to be extracted (comma-separated). |
| `metrics.commit` | Metrics on commits to be extracted (comma-separated). |
| `output.csv` | Path where to save the resulting CSV file. |

---

## 🏃‍♂️ How to Execute

1. **Cleanliness and compilation:**
 ```bash
 mvn clean compile
 ```

2. **Execution with configuration:**
 ```bash
 mvn clean compile exec:java "-Dexec.mainClass=it.mantimetrics.App" "-Dexec.args=config/config_avro.properties"
 ```

✅ The final CSV will be saved to the specified path (`data/output/dataset_avro.csv`).

---

## 🧩 Main Dependencies

- [JGit](https://www.eclipse.org/jgit/) – to access the Git history
- [OpenCSV](http://opencsv.sourceforge.net/) – for the management of CSV files
- [SLF4J](http://www.slf4j.org/) – for logging (optional)

> **Note**: If you see warnings of `SLF4J: Failed to load StaticLoggerBinder`, you can ignore them or add an SLF4J implementation.

---

## 📈 Example of CSV Output

| methodName | releaseId | LOC | cyclomaticComplexity | nestingDepth | branchCount | methodHistories | authors | churn |
|:-----------|:----------|:----|:---------------------|:-------------|:------------|:----------------|:--------|:------|
| fooMethod | 1 | 15 | 2 | 1 | 1 | 3 | 2 | 45 |
| barMethod | 1 | 30 | 4 | 2 | 3 | 5 | 4 | 60 |

---

## 📋 Future TODO

- [ ] Support advanced AST extraction (e.g., use of JavaParser)
- [ ] Improve log management (insert SLF4J + Logback)
- [ ] Extend support to OO metrics (e.g., LCOM, CBO)

---

## 👨‍💻 Author

- **Matteo La Gioia**

---

# 🔥 Ready to use!
> Change the `.properties` file, and you can generate as many datasets as you need.

---