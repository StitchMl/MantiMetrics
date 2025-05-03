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
├── output/
│   ├── avro_dataset.csv
│   └── bookkeeper_dataset.csv
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── mantimetrics/
│       │           ├── MantiMetrics.java
│       │           ├── config/
│       │           │   └── ProjectConfigLoader.java
│       │           ├── csv/
│       │           │   └── CSVWriter.java                      
│       │           ├── git/
│       │           │   ├── GitService.java
│       │           │   └── ProjectConfig.java
│       │           ├── jira/
│       │           │   └── JiraClient.java 
│       │           ├── metrics/
│       │           │   ├── MethodMetrics.java
│       │           │   └── MetricsCalculator.java
│       │           ├── model/
│       │           │   └── MethodData.java
│       │           └── parser/
│       │               └── CodeParser.java
│       │           
│       └── resources/
│           ├── application.properties
│           ├── github.properties
│           └── log4j.properties
│           └── projects-config.json
├── pom.xml 
└── README.md
```

---

## ⚙️ Configuration

For each Git project to be analyzed, you must add in `projects-config.json` configuration file like this one:

### Example: `resources/projects-config.json`

```properties
[
   {
      "name": "BookKeeper",
      "repoUrl": "https://github.com/apache/bookkeeper.git",
      "jiraKey": "BOOKKEEPER"
   },
   {
      "name": "Avro",
      "repoUrl": "https://github.com/apache/avro.git",
      "jiraKey": "AVRO"
   }
]
```

### Main parameters:

| Parameter        | Description                                           |
|:-----------------|:------------------------------------------------------|
| `name`           | Repo name.                                            |
| `repoUrl`        | Path to the Git repository.                           |
| `jiraKey`        | Name of repo on JIRA.                                 |
---
## 🏃‍♂️ How to Execute

1. **Cleanliness and compilation:**
   ```bash
   mvn clean compile
   ```
   
✅ The final CSV will be saved to the specified path (`output/avro_dataset.csv`).

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
| fooMethod  | 1         | 15  | 2                    | 1            | 1           | 3               | 2       | 45    |
| barMethod  | 1         | 30  | 4                    | 2            | 3           | 5               | 4       | 60    |

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
> Change the `projects-config.json` file, and you can generate as many datasets as you need.

---