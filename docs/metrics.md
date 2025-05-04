## Summary

This documentation describes the metrics implemented in `MethodMetrics` and calculated by `MetricsCalculator`.  
The metrics cover aspects of **size measurement** (LOC, statement count), **structural complexity** (cyclomatic and cognitive complexity), **semantic complexity** (Halstead metrics), **nesting depth** and **detection of 'code smells'** (Long Method, God Class, Feature Envy, Duplicated Code).

---

## 1. Basic Metrics

### 1.1 Code Lines (LOC)
Counter of non-empty source code lines, excluding comments and blank lines.Counter of non-empty source code lines, excluding comments and blank lines.
> **Meaning:** more LOC indicates a larger and potentially more challenging method to maintain.  

### 1.2 Statement Count
Number of individual executable instructions (`Statements`) in the method.
> **Meaning:** provides a crude measure of the number of operations performed by the method.

---

## 2. Complexity metrics

### 2.1 Cyclomatic Complexity
Number of linearly independent paths in the method, calculated as **decision points + 1**.
> **Formula (simple):**  
> \(\text{CC} = \#{text{if, for, while, do, switch entries, conditional expressions} + 1\)  
> **Meaning:** measures the complexity of the control flow; high values imply more tests needed.  

### 2.2 Cognitive Complexity
SonarSource's metric for assessing the mental difficulty of comprehension, which penalizes nesting and control structures.
> **Algorithm (simplified):** each conditional or iteration construct increases by 1, nesting adds further weight.  
> **Meaning:** aligns the metric to the human perception of complexity.  

---

## 3. Halstead Metrics

Introduced by Maurice Halstead (1977), they measure code properties based on operators and operands.

| Symbol | Description                       |
|:------:|-----------------------------------|
|   n₁   | Number of distinct operators      |
|   n₂   | Number of distinct operands       |
|   N₁   | Total number of operators         |
|   N₂   | Total number of operands          |
|   n    | Vocabulary = n₁ + n₂              |
|   N    | Length = N₁ + N₂                  |
|   V    | Volume = N × log₂(n)              |
|   D    | Difficulty = (n₁ / 2) × (N₂ / n₂) |
|   E    | Effort = D × V                    |

> **Meaning:** provide estimates of **volume**, **difficulty** and **effort** required to understand or write the method.  

---

## 4. Nesting Depth

### 4.1 Max Nesting Depth
Maximum nesting depth of `if`, `for`, `while`, `do` or `switch` in the method.
> **Meaning:** deep nodes make the code more difficult to follow.  

---

## 5. Code Smells

### 5.1 Long Method
Excessively long method, with too many lines and responsibilities.
> **Identification:** LOC > 50 (example threshold in the code).  
> **Impact:** reduces readability, increases the likelihood of bugs.  

### 5.2 God Class
All-encompassing' class that encompasses too many responsibilities or methods.
> **Impact:** high coupling, low cohesion, difficult to maintain.  

### 5.3 Feature Envy
Method that makes extensive use of another class' data/methods rather than its own.
> **Impact:** violation of the Single Responsibility Principle, suggests method or class extraction.  

### 5.4 Duplicated Code
Identical or very similar portions of code are scattered in several places.
> **Impact:** increases technical debt, complicates modifications.  

---

*End of metrics documentation.*